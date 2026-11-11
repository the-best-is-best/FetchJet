package com.michelle.raouf.fetch_jet.core.power

import com.michelle.raouf.fetch_jet.platform.desktop.DesktopOs
import com.michelle.raouf.fetch_jet.platform.desktop.runProcess
import kotlinx.coroutines.runBlocking

actual object SystemPower {
    actual val isSupported: Boolean = true

    actual fun sleep(): Result<Unit> = run(listOf("pmset", "sleepnow"))

    actual fun shutDown(): Result<Unit> =
        run(listOf("osascript", "-e", "tell application \"System Events\" to shut down"))

    private fun run(command: List<String>): Result<Unit> {
        val result = try {
            runBlocking { DesktopOs.runProcess(command, mergeStderr = true, timeoutMs = 30_000) }
        } catch (e: Throwable) {
            return Result.failure(IllegalStateException(e.message ?: "${command.first()} failed"))
        }
        if (!result.timedOut && result.exitCode == 0) return Result.success(Unit)
        val reason = result.stdout.trim().ifEmpty { "${command.joinToString(" ")} exit ${result.exitCode}" }
        return Result.failure(IllegalStateException(reason))
    }
}
