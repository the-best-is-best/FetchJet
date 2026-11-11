package com.michelle.raouf.fetch_jet.feature.device_sync

import com.michelle.raouf.fetch_jet.core.update.DesktopOs
import java.io.File
import java.util.concurrent.TimeUnit

internal actual fun deviceSyncHostName(): String? = when (DesktopOs.current) {
    DesktopOs.WINDOWS -> System.getenv("COMPUTERNAME")
    DesktopOs.MAC -> command("/usr/sbin/scutil", "--get", "ComputerName")
        ?: command("/usr/sbin/scutil", "--get", "LocalHostName")
    DesktopOs.LINUX -> runCatching { File("/etc/hostname").readText().trim() }.getOrNull()?.takeIf { it.isNotEmpty() }
        ?: System.getenv("HOSTNAME")
        ?: command("hostname")
}?.trim()?.let { if (DesktopOs.current == DesktopOs.MAC) it else it.substringBefore('.') }

private fun command(vararg args: String): String? = try {
    val process = ProcessBuilder(*args).redirectError(ProcessBuilder.Redirect.DISCARD).start()
    if (!process.waitFor(2, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        null
    } else if (process.exitValue() != 0) {
        null
    } else {
        process.inputStream.bufferedReader().readText().trim().takeIf { it.isNotEmpty() }
    }
} catch (_: Exception) {
    null
}
