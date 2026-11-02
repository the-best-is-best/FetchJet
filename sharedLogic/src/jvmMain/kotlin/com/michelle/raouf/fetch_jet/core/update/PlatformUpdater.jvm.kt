package com.michelle.raouf.fetch_jet.core.update

import com.michelle.raouf.fetch_jet.core.platform.WindowsStorePackage
import java.io.File
import kotlin.system.exitProcess

actual object PlatformUpdater {
    private val os = System.getProperty("os.name").orEmpty().lowercase()
    private val isWindows = os.contains("win")
    private val isMac = os.contains("mac")

    /** Path of the installed launcher (FetchJet.exe); null when run from Gradle / an IDE. */
    private val launcherPath: String? =
        System.getProperty("jpackage.app-path")?.takeIf { File(it).exists() }

    private val macAppBundle: File? = if (!isMac) null else launcherPath?.let { path ->
        generateSequence(File(path)) { it.parentFile }.firstOrNull { it.name.endsWith(".app") }
    }

    private val linuxDebInstall: Boolean =
        !isWindows && !isMac && launcherPath?.startsWith("/opt/") == true

    private val linuxPortableRoot: File? =
        if (isWindows || isMac || linuxDebInstall) null else launcherPath?.let { File(it).parentFile?.parentFile }

    private fun hasCommand(name: String): Boolean =
        System.getenv("PATH").orEmpty().split(File.pathSeparator)
            .any { File(it, name).canExecute() }

    /**
     * Only the installed Windows build updates itself: its MSI has a fixed upgrade id, so a
     * newer MSI replaces it in place (per-user install, no admin prompt).
     */
    actual val canInstallInApp: Boolean = when {
        isWindows -> launcherPath != null && !WindowsStorePackage.isInstalled
        isMac -> macAppBundle?.let { it.canWrite() && it.parentFile?.canWrite() == true } == true
        linuxDebInstall -> hasCommand("pkexec") && hasCommand("dpkg")
        else -> linuxPortableRoot?.parentFile?.canWrite() == true
    }

    /** The Microsoft Store build is updated by the Store. */
    actual val updatesFromStore: Boolean = WindowsStorePackage.isInstalled

    private val storeUpdateScript = """
        ${'$'}ErrorActionPreference = 'Stop'
        Add-Type -AssemblyName System.Runtime.WindowsRuntime
        ${'$'}null = [Windows.Services.Store.StoreContext, Windows.Services.Store, ContentType = WindowsRuntime]
        ${'$'}asTask = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
            ${'$'}_.Name -eq 'AsTask' -and ${'$'}_.GetParameters().Count -eq 1 -and ${'$'}_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
        } | Select-Object -First 1
        ${'$'}op = [Windows.Services.Store.StoreContext]::GetDefault().GetAppAndOptionalStorePackageUpdatesAsync()
        ${'$'}task = ${'$'}asTask.MakeGenericMethod([System.Collections.Generic.IReadOnlyList[Windows.Services.Store.StorePackageUpdate]]).Invoke(${'$'}null, @(${'$'}op))
        ${'$'}null = ${'$'}task.Wait(60000)
        ${'$'}versions = @(${'$'}task.Result | ForEach-Object { ${'$'}v = ${'$'}_.Package.Id.Version; "${'$'}(${'$'}v.Major).${'$'}(${'$'}v.Minor).${'$'}(${'$'}v.Build)" })
        "FJSTORE " + (${'$'}versions -join ',')
    """.trimIndent()

    actual fun latestStoreVersion(): String? {
        val encoded = java.util.Base64.getEncoder()
            .encodeToString(storeUpdateScript.toByteArray(Charsets.UTF_16LE))
        val process = ProcessBuilder(
            "powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
            "-EncodedCommand", encoded
        ).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        if (!process.waitFor(90, java.util.concurrent.TimeUnit.SECONDS)) {
            process.destroy()
            error("Microsoft Store did not answer")
        }
        val line = output.lineSequence().firstOrNull { it.startsWith("FJSTORE") }
            ?: error(
                "Microsoft Store check failed: ${
                    output.trim().lineSequence().firstOrNull().orEmpty()
                }"
            )
        return line.removePrefix("FJSTORE").split(',').map { it.trim() }.filter { it.isNotEmpty() }
            .maxWithOrNull { a, b -> if (isNewer(a, b)) 1 else if (isNewer(b, a)) -1 else 0 }
    }
    actual val platformKey: String = when {
        isWindows -> "windows"
        isMac -> "macos"
        else -> "linux"
    }

    actual fun pickAsset(assets: List<ReleaseAsset>): ReleaseAsset? {
        fun first(vararg ext: String) =
            assets.firstOrNull { a -> ext.any { a.name.endsWith(it, ignoreCase = true) } }
        return when {
            isWindows -> first(".msi") ?: first(".exe")
            isMac -> first(".dmg")
            linuxPortableRoot != null -> first(".tar.gz")
            else -> first(".deb") ?: first(".AppImage", ".tar.gz")
        }
    }

    actual fun launchInstallerAndExit(installerPath: String): Result<Unit> = runCatching {
        if (!isWindows) return@runCatching runUnixUpdateAndExit(File(installerPath))
        val installer = File(installerPath)
        check(installer.exists()) { "installer not found" }
        // A small batch file runs after FetchJet has quit: waits a moment so our files are
        // released, installs over the old version, then starts the new one.
        val install = if (installer.name.endsWith(".msi", ignoreCase = true))
            "msiexec /i \"${installer.absolutePath}\" /passive /norestart"
        else
            "\"${installer.absolutePath}\""
        val relaunch = launcherPath?.let { "start \"\" \"$it\"" } ?: ""
        val script = File(installer.parentFile, "fetchjet-update.cmd")
        // Wait until this process is really gone (shutdown can take a few seconds), else the
        // installer finds FetchJet's files in use.
        val pid = ProcessHandle.current().pid()
        script.writeText(
            "@echo off\r\n" +
                    ":wait\r\n" +
                    "tasklist /FI \"PID eq $pid\" 2>nul | find \"$pid\" >nul && (timeout /t 1 /nobreak >nul & goto wait)\r\n" +
                    "$install\r\n" +
                    "$relaunch\r\n" +
                    "del \"%~f0\"\r\n"
        )
        ProcessBuilder("cmd.exe", "/c", "start", "\"FetchJet update\"", "/min", script.absolutePath)
            .directory(installer.parentFile)
            .start()
        exitProcess(0)
    }

    private fun q(value: String) = "'" + value.replace("'", "'\\''") + "'"

    private fun runUnixUpdateAndExit(installer: File) {
        check(installer.exists()) { "installer not found" }
        val pid = ProcessHandle.current().pid()
        val body = when {
            isMac -> {
                val app = checkNotNull(macAppBundle) { "FetchJet.app not found" }.absolutePath
                """
                MNT=${'$'}(mktemp -d "${'$'}{TMPDIR:-/tmp}/fetchjet-update.XXXXXX")
                if hdiutil attach -nobrowse -noautoopen -quiet -mountpoint "${'$'}MNT" ${q(installer.absolutePath)}; then
                  SRC=${'$'}(find "${'$'}MNT" -maxdepth 1 -name '*.app' -print -quit)
                  if [ -n "${'$'}SRC" ] && ditto "${'$'}SRC" ${q("$app.new")}; then
                    rm -rf ${q(app)} && mv ${q("$app.new")} ${q(app)}
                  fi
                  hdiutil detach -quiet "${'$'}MNT"
                fi
                rm -rf ${q("$app.new")}
                xattr -dr com.apple.quarantine ${q(app)} 2>/dev/null
                open ${q(app)}
                """
            }

            linuxDebInstall -> """
                pkexec dpkg -i ${q(installer.absolutePath)}
                nohup ${q(launcherPath!!)} >/dev/null 2>&1 &
                """

            else -> {
                val root =
                    checkNotNull(linuxPortableRoot) { "FetchJet folder not found" }.absolutePath
                val launcher = File(launcherPath!!).name
                """
                TMP=${'$'}(mktemp -d /tmp/fetchjet-update.XXXXXX)
                if tar xzf ${q(installer.absolutePath)} -C "${'$'}TMP"; then
                  SRC=${'$'}(find "${'$'}TMP" -mindepth 1 -maxdepth 1 -type d -print -quit)
                  if [ -x "${'$'}SRC/bin/$launcher" ]; then
                    rm -rf ${q("$root.old")} && mv ${q(root)} ${q("$root.old")} && mv "${'$'}SRC" ${
                    q(
                        root
                    )
                } && rm -rf ${q("$root.old")}
                  fi
                fi
                rm -rf "${'$'}TMP"
                nohup ${q("$root/bin/$launcher")} >/dev/null 2>&1 &
                """
            }
        }
        val script = File(installer.parentFile, "fetchjet-update.sh")
        script.writeText(
            "#!/bin/bash\n" +
                    "while kill -0 $pid 2>/dev/null; do sleep 1; done\n" +
                    body.trimIndent() + "\n" +
                    "rm -f \"${'$'}0\"\n"
        )
        script.setExecutable(true)
        ProcessBuilder("/bin/bash", script.absolutePath)
            .directory(installer.parentFile)
            .redirectOutput(File(installer.parentFile, "fetchjet-update.log"))
            .redirectErrorStream(true)
            .start()
        exitProcess(0)
    }
}
