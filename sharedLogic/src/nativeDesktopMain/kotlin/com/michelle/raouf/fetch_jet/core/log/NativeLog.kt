@file:OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)

package com.michelle.raouf.fetch_jet.core.log

import com.michelle.raouf.fetch_jet.core.filesystem.DesktopPaths
import com.michelle.raouf.fetch_jet.core.update.APP_VERSION
import com.michelle.raouf.fetch_jet.platform.desktop.DesktopOs
import com.michelle.raouf.fetch_jet.platform.desktop.join
import kotlin.concurrent.AtomicInt
import kotlin.concurrent.Volatile
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext

internal interface LogAppender {
    fun append(bytes: ByteArray): Boolean
}

internal expect fun openLogAppender(path: String): LogAppender?

internal expect fun redirectStdStreamsToPipe(): Int?

internal expect fun readLogPipe(fd: Int, buffer: ByteArray): Int

internal object NativeLog {
    private const val MAX_BYTES = 2L * 1024 * 1024
    private val installed = AtomicInt(0)

    @Volatile
    private var file: LogAppender? = null

    @Volatile
    var path: String = ""
        private set

    fun install(redirect: Boolean) {
        if (!installed.compareAndSet(0, 1)) return
        val wantVerbose = DesktopOs.getEnv("FETCHJET_VERBOSE_LOGS").equals("true", ignoreCase = true)
        val keepConsole = DesktopOs.getEnv("FETCHJET_LOG_CONSOLE") == "1"
        try {
            val dir = DesktopOs.join(DesktopPaths.settings(), "logs")
            DesktopOs.createDirectories(dir)
            val target = DesktopOs.join(dir, "app.log")
            if ((DesktopOs.fileInfo(target)?.size ?: 0L) > MAX_BYTES) DesktopOs.deleteFile(target)
            val appender = openLogAppender(target)
            if (appender != null) {
                file = appender
                path = target
                if (redirect && !keepConsole) startRedirect()
            }
        } catch (_: Throwable) {
        }
        AppLog.verbose = wantVerbose
        println("---- FetchJet started ${DesktopOs.localTimeStamp().replace(' ', 'T')} (native $APP_VERSION) ----")
        println("Verbose logs: $wantVerbose")
    }

    private fun startRedirect() {
        val readEnd = redirectStdStreamsToPipe() ?: return
        GlobalScope.launch(newSingleThreadContext("fetchjet-log")) {
            val buffer = ByteArray(8192)
            var pending = ByteArray(1024)
            var length = 0
            while (true) {
                val read = readLogPipe(readEnd, buffer)
                if (read <= 0) break
                for (i in 0 until read) {
                    val b = buffer[i]
                    if (b == '\n'.code.toByte()) {
                        writeLine(pending, length)
                        length = 0
                    } else if (b != '\r'.code.toByte()) {
                        if (length == pending.size) pending = pending.copyOf(pending.size * 2)
                        pending[length++] = b
                    }
                }
            }
        }
    }

    private fun writeLine(bytes: ByteArray, length: Int) {
        val line = try {
            LogRedact.redact(bytes.decodeToString(0, length))
        } catch (_: Throwable) {
            bytes.decodeToString(0, length)
        }
        writeDirect(line)
    }

    fun writeDirect(line: String) {
        file?.append((line + "\n").encodeToByteArray())
    }
}
