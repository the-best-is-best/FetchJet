package com.michelle.raouf.fetch_jet.core.update

import com.michelle.raouf.fetch_jet.core.filesystem.DesktopPaths
import com.michelle.raouf.fetch_jet.feature.archive_extract.ArchiveExtractor
import com.michelle.raouf.fetch_jet.feature.archive_extract.ExtractPlace
import com.michelle.raouf.fetch_jet.platform.desktop.HostHooks
import com.michelle.raouf.fetch_jet.platform.win.WinApp
import com.michelle.raouf.fetch_jet.platform.win.WinProcess
import com.michelle.raouf.fetch_jet.platform.win.winCreateDirectories
import com.michelle.raouf.fetch_jet.platform.win.winDeleteFile
import com.michelle.raouf.fetch_jet.platform.win.winDeleteRecursively
import com.michelle.raouf.fetch_jet.platform.win.winExists
import com.michelle.raouf.fetch_jet.platform.win.winListEntries
import com.michelle.raouf.fetch_jet.platform.win.winMove
import com.michelle.raouf.fetch_jet.platform.win.winProcessId
import kotlin.system.exitProcess
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKStringFromUtf16
import kotlinx.cinterop.value
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import platform.windows.CommandLineToArgvW
import platform.windows.GetCommandLineW
import platform.windows.GetLastError
import platform.windows.LocalFree
import platform.windows.Sleep

internal object WindowsUpdater {
    val canInstallInApp: Boolean get() = (WinApp.launcherPath != null || WinApp.isPortable) && !WinApp.isStoreBuild

    fun firstAsset(assets: List<ReleaseAsset>, vararg extensions: String): ReleaseAsset? =
        assets.firstOrNull { asset -> extensions.any { asset.name.endsWith(it, ignoreCase = true) } }

    fun pickAsset(assets: List<ReleaseAsset>): ReleaseAsset? =
        if (WinApp.isPortable) firstAsset(assets, PortableUpdater.ASSET_SUFFIX)
        else firstAsset(assets, ".msi") ?: firstAsset(assets, ".exe")

    fun installerCommand(installer: String): List<String> =
        if (installer.endsWith(".msi", ignoreCase = true)) {
            listOf("msiexec.exe", "/i", installer, "/passive", "/norestart", "FJ_RELAUNCH=1")
        } else {
            listOf(installer)
        }

    fun installAndExit(installerPath: String) {
        val installer = installerPath.replace('/', '\\')
        if (WinApp.isPortable) return PortableUpdater.installAndExit(installer)
        val folder = DesktopPaths.parentOf(installer) ?: DesktopPaths.temp()
        val command = installerCommand(installer)
        runAfterExit(command, folder, emptyMap(), "Update: starting ${command.first()} after the app closed")
    }

    fun runAfterExit(command: List<String>, folder: String, environment: Map<String, String?>, note: String) {
        val launch = {
            println(note)
            WinProcess.start(command, environment = environment, workDir = folder, captureOutput = false, detached = true).close()
        }
        val quit = HostHooks.requestExit
        if (quit == null) {
            launch()
            exitProcess(0)
        }
        HostHooks.afterShutdown = launch
        quit()
    }

    fun latestStoreVersion(): String? = try {
        askStore()
    } catch (_: Throwable) {
        Sleep(3000u)
        askStore()
    }

    private fun askStore(): String? {
        val answer = HostHooks.storeUpdates?.invoke()?.takeIf { it.isNotBlank() }
            ?: error("Microsoft Store did not answer")
        return readStoreAnswer(answer)
    }

    private fun readStoreAnswer(text: String): String? {
        val lines = text.lines().map { it.trim() }
        lines.firstOrNull { it.startsWith("FJERR") }?.let {
            error("Microsoft Store: " + it.removePrefix("FJERR").trim().ifBlank { "unknown error" })
        }
        val answer = lines.firstOrNull { it.startsWith("FJSTORE") } ?: error("Microsoft Store did not answer")
        val versions = answer.removePrefix("FJSTORE").trim().split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (versions.isEmpty()) return null
        return versions.reduce { best, next -> if (isNewer(next, best)) next else best }
    }
}

internal object PortableUpdater {
    const val ASSET_SUFFIX = "-windows-portable.zip"
    const val STAGING = ".fetchjet-update"
    const val OLD_SUFFIX = ".fjold"

    fun installAndExit(zipPath: String) {
        val root = WinApp.exeDir
        val fresh = unpack(zipPath, root)
        swap(root, fresh)
        WindowsUpdater.runAfterExit(
            listOf("$root\\FetchJet.exe") + relaunchArguments(), root,
            mapOf("FJ_AFTER_UPDATE_PID" to winProcessId().toString()),
            "Update: starting the new portable FetchJet"
        )
    }

    fun unpack(zipPath: String, root: String): String {
        val staging = "$root\\$STAGING"
        winDeleteRecursively(staging)
        check(winCreateDirectories(staging)) { "cannot write in $root" }
        val zip = "$staging\\update.zip"
        check(winMove(zipPath, zip)) { "cannot move the update into $root (${GetLastError()})" }
        val result = runBlocking { ArchiveExtractor.extract(zip.toPath(), place = ExtractPlace.ALWAYS_FOLDER) }
        val fresh = result.destination.replace('/', '\\')
        check(winExists("$fresh\\FetchJet.exe") && winExists("$fresh\\${WinApp.PORTABLE_MARKER}")) {
            "the update is not a portable FetchJet"
        }
        return fresh
    }

    fun swap(root: String, fresh: String) {
        val files = ArrayList<String>()
        collect(fresh, "", files)
        val done = ArrayList<Pair<String, String?>>()
        try {
            for (rel in files) {
                if (rel.equals(WinApp.PORTABLE_DATA, true) || rel.startsWith(WinApp.PORTABLE_DATA + "\\", true)) continue
                val target = "$root\\$rel"
                winCreateDirectories(target.substringBeforeLast('\\'))
                var backup: String? = null
                if (winExists(target)) {
                    backup = freeOldName(target)
                    check(winMove(target, backup, replace = false)) { "cannot move $rel aside (${GetLastError()})" }
                }
                done += target to backup
                check(winMove("$fresh\\$rel", target, replace = false)) { "cannot put the new $rel in place (${GetLastError()})" }
            }
        } catch (e: Throwable) {
            for ((target, backup) in done.asReversed()) {
                if (winExists(target)) winDeleteFile(target)
                if (backup != null) winMove(backup, target)
            }
            throw e
        }
        println("Update: ${done.size} files of the portable FetchJet replaced")
    }

    private val KEEP_WITH_VALUE = setOf("--home", "--downloads", "--bridge-port")
    private val KEEP_ALONE = setOf("--no-bridge", "--no-focus", "--no-auto-downloads")

    @OptIn(ExperimentalForeignApi::class)
    fun relaunchArguments(): List<String> = memScoped {
        val count = alloc<IntVar>()
        val line = GetCommandLineW()?.toKStringFromUtf16() ?: return emptyList()
        val argv = CommandLineToArgvW(line, count.ptr) ?: return emptyList()
        val all = (0 until count.value).map { argv[it]?.toKStringFromUtf16().orEmpty() }
        LocalFree(argv)
        val kept = ArrayList<String>()
        var i = 1
        while (i < all.size) {
            val arg = all[i]
            when {
                arg in KEEP_WITH_VALUE && i + 1 < all.size -> { kept += arg; kept += all[i + 1]; i++ }
                arg in KEEP_ALONE -> kept += arg
            }
            i++
        }
        kept
    }

    private fun freeOldName(target: String): String {
        val first = target + OLD_SUFFIX
        if (!winExists(first) || winDeleteFile(first)) return first
        var n = 2
        while (winExists("$target.$n$OLD_SUFFIX")) n++
        return "$target.$n$OLD_SUFFIX"
    }

    private fun collect(dir: String, prefix: String, out: MutableList<String>) {
        for (entry in winListEntries(dir) ?: return) {
            val rel = if (prefix.isEmpty()) entry.name else "$prefix\\${entry.name}"
            if (entry.isDirectory) collect("$dir\\${entry.name}", rel, out) else out += rel
        }
    }
}

actual object PlatformUpdater {
    actual val canInstallInApp: Boolean get() = WindowsUpdater.canInstallInApp

    actual val platformKey: String = "windows"

    actual val updatesFromStore: Boolean get() = WinApp.isStoreBuild

    actual fun latestStoreVersion(): String? = WindowsUpdater.latestStoreVersion()

    actual fun pickAsset(assets: List<ReleaseAsset>): ReleaseAsset? = WindowsUpdater.pickAsset(assets)

    actual fun launchInstallerAndExit(installerPath: String): Result<Unit> = runCatching {
        check(winExists(installerPath)) { "installer not found" }
        WindowsUpdater.installAndExit(installerPath)
    }
}
