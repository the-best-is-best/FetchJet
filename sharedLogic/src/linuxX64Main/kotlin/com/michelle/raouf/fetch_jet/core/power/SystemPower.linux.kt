package com.michelle.raouf.fetch_jet.core.power

import com.michelle.raouf.fetch_jet.platform.desktop.DesktopOs
import com.michelle.raouf.fetch_jet.platform.desktop.runProcess
import kotlinx.coroutines.runBlocking

actual object SystemPower {
    actual val isSupported: Boolean = true

    actual fun sleep(): Result<Unit> = firstThatWorks(listOf("systemctl", "suspend"), listOf("loginctl", "suspend"))

    actual fun shutDown(): Result<Unit> = firstThatWorks(listOf("systemctl", "poweroff"), listOf("loginctl", "poweroff"))

    private fun firstThatWorks(vararg commands: List<String>): Result<Unit> {
        var last = "no command worked"
        for (command in commands) {
            val result = try {
                runBlocking { DesktopOs.runProcess(command, mergeStderr = true, timeoutMs = 15_000) }
            } catch (e: Throwable) {
                last = e.message ?: last
                continue
            }
            if (!result.timedOut && result.exitCode == 0) return Result.success(Unit)
            last = result.stdout.trim().ifEmpty { "${command.joinToString(" ")} exit ${result.exitCode}" }
        }
        return Result.failure(IllegalStateException(last))
    }
}
