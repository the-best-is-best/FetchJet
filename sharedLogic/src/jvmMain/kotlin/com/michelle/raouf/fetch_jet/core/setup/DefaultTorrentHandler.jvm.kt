package com.michelle.raouf.fetch_jet.core.setup

import com.michelle.raouf.fetch_jet.core.platform.WindowsStorePackage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

actual object DefaultTorrentHandler {
    private val os = System.getProperty("os.name").orEmpty().lowercase()
    internal val isMac = os.contains("mac")
    internal val isWindows = os.startsWith("windows")
    internal val isLinux = os.contains("linux")

    /** The installed app only: a Gradle / IDE run is a plain `java` process. */
    internal val launcher: String? get() = System.getProperty("jpackage.app-path")?.takeIf { it.isNotBlank() }

    actual val supported: Boolean
        get() = (launcher != null || WindowsStorePackage.isInstalled) && (isMac || isWindows || isLinux)

    actual val opensSystemPage: Boolean get() = isWindows

    actual suspend fun isDefault(): Boolean? = withContext(Dispatchers.IO) {
        runCatching {
            when {
                isMac -> MacDefaultHandler.isDefault()
                isWindows -> WindowsDefaultHandler.isDefault()
                isLinux -> LinuxDefaultHandler.isDefault()
                else -> null
            }
        }.getOrNull()
    }

    actual suspend fun makeDefault(): Boolean = withContext(Dispatchers.IO) {
        if (!supported) return@withContext false
        runCatching {
            when {
                isMac -> MacDefaultHandler.makeDefault()
                isWindows -> WindowsDefaultHandler.makeDefault(launcher)
                isLinux -> LinuxDefaultHandler.makeDefault(launcher)
                else -> false
            }
        }.getOrDefault(false)
    }
}

internal class JvmRun(val code: Int, val out: String)

internal fun jvmRun(vararg command: String, timeoutSeconds: Long = 15): JvmRun? = try {
    val process = ProcessBuilder(*command).redirectErrorStream(true).start()
    val out = process.inputStream.bufferedReader().readText()
    if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        null
    } else JvmRun(process.exitValue(), out)
} catch (_: Throwable) {
    null
}
