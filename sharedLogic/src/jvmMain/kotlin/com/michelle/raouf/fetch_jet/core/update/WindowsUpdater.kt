package com.michelle.raouf.fetch_jet.core.update

import com.michelle.raouf.fetch_jet.core.platform.WindowsStorePackage
import com.michelle.raouf.fetch_jet.core.update.WindowsUpdater.latestStoreVersion
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * The installed MSI build updates itself: its MSI has a fixed upgrade id, so a newer MSI
 * replaces it in place (per-user install, no admin prompt). The Store build is updated by the
 * Store, see [latestStoreVersion].
 */
internal object WindowsUpdater : DesktopUpdater {
    override val platformKey = "windows"

    override val canInstallInApp: Boolean =
        DesktopInstall.launcherPath != null && !WindowsStorePackage.isInstalled

    override fun pickAsset(assets: List<ReleaseAsset>): ReleaseAsset? =
        DesktopInstall.firstAsset(assets, ".msi") ?: DesktopInstall.firstAsset(assets, ".exe")

    override fun installAndExit(installer: File) {
        val install = if (installer.name.endsWith(".msi", ignoreCase = true))
            "msiexec /i \"${installer.absolutePath}\" /passive /norestart"
        else
            "\"${installer.absolutePath}\""
        val relaunch = DesktopInstall.launcherPath?.let { "start \"\" \"$it\"" } ?: ""
        // Wait until this process is really gone (shutdown can take a few seconds), else the
        // installer finds FetchJet's files in use.
        val pid = ProcessHandle.current().pid()
        val script = File(installer.parentFile, "fetchjet-update.cmd")
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

    /** Newest version the Microsoft Store has waiting for this install, or null for none. */
    fun latestStoreVersion(): String? {
        val encoded = java.util.Base64.getEncoder()
            .encodeToString(storeUpdateScript.toByteArray(Charsets.UTF_16LE))
        val process = ProcessBuilder(
            "powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
            "-EncodedCommand", encoded
        ).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        if (!process.waitFor(90, TimeUnit.SECONDS)) {
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
}
