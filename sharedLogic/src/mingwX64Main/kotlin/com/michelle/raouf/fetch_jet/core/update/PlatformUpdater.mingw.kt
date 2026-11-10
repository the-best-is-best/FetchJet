package com.michelle.raouf.fetch_jet.core.update

import com.michelle.raouf.fetch_jet.core.filesystem.WinPaths
import com.michelle.raouf.fetch_jet.platform.win.HostHooks
import com.michelle.raouf.fetch_jet.platform.win.WinApp
import com.michelle.raouf.fetch_jet.platform.win.WinProcess
import com.michelle.raouf.fetch_jet.platform.win.winExists
import com.michelle.raouf.fetch_jet.platform.win.winProcessId
import com.michelle.raouf.fetch_jet.platform.win.winRunProcess
import com.michelle.raouf.fetch_jet.platform.win.winWriteText
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.system.exitProcess
import kotlinx.coroutines.runBlocking
import platform.windows.Sleep

internal object WindowsUpdater {
    private const val STORE_SCRIPT = """§ErrorActionPreference = 'Stop'
§ProgressPreference = 'SilentlyContinue'
try {
    Add-Type -AssemblyName System.Runtime.WindowsRuntime
    §null = [Windows.Services.Store.StoreContext, Windows.Services.Store, ContentType = WindowsRuntime]
    §asTask = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
        §_.Name -eq 'AsTask' -and §_.GetParameters().Count -eq 1 -and §_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
    } | Select-Object -First 1
    §op = [Windows.Services.Store.StoreContext]::GetDefault().GetAppAndOptionalStorePackageUpdatesAsync()
    §task = §asTask.MakeGenericMethod([System.Collections.Generic.IReadOnlyList[Windows.Services.Store.StorePackageUpdate]]).Invoke(§null, @(§op))
    if (-not §task.Wait(60000)) { "FJERR the Store did not answer in time"; exit }
    §versions = @(§task.Result | ForEach-Object { §v = §_.Package.Id.Version; "§(§v.Major).§(§v.Minor).§(§v.Build)" })
    "FJSTORE " + (§versions -join ',')
} catch {
    §e = §_.Exception
    while (§e.InnerException) { §e = §e.InnerException }
    "FJERR " + (§e.Message -replace '\s+', ' ').Trim()
}"""

    val canInstallInApp: Boolean get() = WinApp.launcherPath != null && !WinApp.isStoreBuild

    fun firstAsset(assets: List<ReleaseAsset>, vararg extensions: String): ReleaseAsset? =
        assets.firstOrNull { asset -> extensions.any { asset.name.endsWith(it, ignoreCase = true) } }

    fun pickAsset(assets: List<ReleaseAsset>): ReleaseAsset? =
        firstAsset(assets, ".msi") ?: firstAsset(assets, ".exe")

    fun installAndExit(installerPath: String) {
        val installer = installerPath.replace('/', '\\')
        val folder = WinPaths.parentOf(installer) ?: WinPaths.temp()
        val install =
            if (installer.endsWith(".msi", ignoreCase = true)) "msiexec /i \"$installer\" /passive /norestart"
            else "\"$installer\""
        val relaunch = WinApp.launcherPath?.let { "start \"\" \"$it\"" }.orEmpty()
        val pid = winProcessId()
        val script = "$folder\\fetchjet-update.cmd"
        val text = listOf(
            "@echo off",
            "chcp 65001 >nul",
            ":wait",
            "tasklist /FI \"PID eq $pid\" 2>nul | find \"$pid\" >nul && (timeout /t 1 /nobreak >nul & goto wait)",
            install,
            relaunch,
            "del \"%~f0\"",
            ""
        ).joinToString("\r\n")
        check(winWriteText(script, text)) { "could not write the update script" }
        WinProcess.start(
            emptyList(), workDir = folder, captureOutput = false, detached = true,
            rawCommandLine = "cmd.exe /c start \"FetchJet update\" /min \"$script\""
        ).close()
        val quit = HostHooks.requestExit
        if (quit != null) quit() else exitProcess(0)
    }

    fun latestStoreVersion(): String? = try {
        askStore()
    } catch (_: Throwable) {
        Sleep(3000u)
        askStore()
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun askStore(): String? {
        val script = STORE_SCRIPT.replace('§', '$')
        val bytes = ByteArray(script.length * 2)
        for (i in script.indices) {
            val code = script[i].code
            bytes[i * 2] = (code and 0xFF).toByte()
            bytes[i * 2 + 1] = (code ushr 8).toByte()
        }
        val result = runBlocking {
            winRunProcess(
                listOf(
                    "powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                    "-EncodedCommand", Base64.encode(bytes)
                ),
                timeoutMs = 90_000
            )
        }
        if (result.timedOut) error("Microsoft Store did not answer")
        val lines = result.stdout.lines().map { it.trim() }
        lines.firstOrNull { it.startsWith("FJERR") }?.let {
            error("Microsoft Store: " + it.removePrefix("FJERR").trim().ifBlank { "unknown error" })
        }
        val answer = lines.firstOrNull { it.startsWith("FJSTORE") } ?: error("Microsoft Store did not answer")
        val versions = answer.removePrefix("FJSTORE").trim().split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (versions.isEmpty()) return null
        return versions.reduce { best, next -> if (isNewer(next, best)) next else best }
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
