package com.michelle.raouf.fetch_jet.core.power

import java.util.concurrent.TimeUnit

actual object SystemPower {
    private val os = System.getProperty("os.name").orEmpty().lowercase()
    private val windows = os.contains("win")
    private val mac = os.contains("mac")

    actual val isSupported: Boolean = true

    actual fun sleep(): Result<Unit> = run(
        when {
            windows -> listOf(
                "powershell", "-NoProfile", "-NonInteractive", "-Command",
                "Add-Type -AssemblyName System.Windows.Forms; " +
                        "[void][System.Windows.Forms.Application]::SetSuspendState('Suspend', \$false, \$false)"
            )

            mac -> listOf("pmset", "sleepnow")
            else -> listOf("systemctl", "suspend")
        }
    )

    actual fun shutDown(): Result<Unit> = run(
        when {
            windows -> listOf("shutdown", "/s", "/t", "0")
            mac -> listOf("osascript", "-e", "tell application \"System Events\" to shut down")
            else -> listOf("systemctl", "poweroff")
        }
    )

    private fun run(command: List<String>): Result<Unit> = runCatching {
        println("WhenDone: ${command.joinToString(" ")}")
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        if (process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() != 0) {
            val output = process.inputStream.bufferedReader().readText().trim()
            error(output.ifBlank { "exit code ${process.exitValue()}" })
        }
    }
}
