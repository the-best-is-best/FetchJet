package com.michelle.raouf.fetch_jet.feature.file_scan

import com.michelle.raouf.fetch_jet.platform.desktop.DesktopOs
import com.michelle.raouf.fetch_jet.platform.desktop.exists
import com.michelle.raouf.fetch_jet.platform.desktop.nativePath
import com.michelle.raouf.fetch_jet.platform.desktop.runProcess

internal expect fun systemScannerAvailable(): Boolean

internal expect suspend fun systemScan(path: String, sourceUrl: String): ScanResult?

internal expect fun systemMarkFromInternet(path: String, sourceUrl: String, pageUrl: String?)

actual object FileScanner {
    private const val COMMAND_TIMEOUT_MS = 15 * 60_000L

    actual val supported: Boolean = true
    actual val hasSystemScanner: Boolean get() = systemScannerAvailable()

    actual suspend fun scan(path: String, sourceUrl: String, command: String): ScanResult {
        val file = DesktopOs.nativePath(path)
        if (!DesktopOs.exists(file)) return ScanResult(ScanOutcome.FAILED, "file not found")
        if (command.isNotBlank()) return runCommand(command, file)
        return systemScan(file, sourceUrl) ?: ScanResult(ScanOutcome.NO_SCANNER)
    }

    actual fun markFromInternet(path: String, sourceUrl: String, pageUrl: String?) {
        val file = DesktopOs.nativePath(path)
        if (DesktopOs.exists(file)) systemMarkFromInternet(file, sourceUrl, pageUrl)
    }

    private suspend fun runCommand(command: String, file: String): ScanResult {
        val args = ScanCommand.build(command, file)
        if (args.isEmpty()) return ScanResult(ScanOutcome.NO_SCANNER)
        val result = try {
            DesktopOs.runProcess(args, mergeStderr = true, timeoutMs = COMMAND_TIMEOUT_MS)
        } catch (e: Throwable) {
            return ScanResult(ScanOutcome.FAILED, e.message ?: "could not start ${args.first()}")
        }
        if (!DesktopOs.exists(file)) return ScanResult(ScanOutcome.THREAT, "removed by the scanner")
        if (result.timedOut) return ScanResult(ScanOutcome.FAILED, "timed out")
        if (result.exitCode == 0) return ScanResult(ScanOutcome.CLEAN)
        val line = result.stdout.lineSequence().map { it.trim() }.lastOrNull { it.isNotEmpty() }.orEmpty()
        return ScanResult(ScanOutcome.THREAT, "exit ${result.exitCode}" + if (line.isEmpty()) "" else ": ${line.take(200)}")
    }
}
