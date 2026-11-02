@file:OptIn(ExperimentalForeignApi::class)

package com.michelle.raouf.fetch_jet.feature.media_extractor

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import platform.posix.fread
import platform.posix.pclose
import platform.posix.popen

internal fun escapeShellArg(arg: String): String {
    return "'" + arg.replace("'", "'\\''") + "'"
}

@OptIn(ExperimentalForeignApi::class)
fun executeCommandStreaming(
    cmd: String,
    onLine: ((String) -> Unit)? = null
): Int {
    val toolsDir = getIosToolsDirectory()
    val pathEnv =
        "PATH=\"$toolsDir:/var/jb/usr/bin:/usr/local/bin:/usr/bin:/bin:/opt/homebrew/bin:/var/jb/usr/local/bin:\$PATH\" PYTHONUNBUFFERED=1"
    val fullCmd = "$pathEnv $cmd 2>&1"

    val fp = popen(fullCmd, "r") ?: return -1
    try {
        memScoped {
            val buffer = allocArray<ByteVar>(1024)
            val lineSb = StringBuilder()
            while (true) {
                val bytesRead = fread(buffer, 1u, 1024u, fp).toLong()
                if (bytesRead <= 0L) break
                for (i in 0 until bytesRead) {
                    val c = (buffer[i.toInt()].toInt() and 0xFF).toChar()
                    if (c == '\r' || c == '\n') {
                        if (lineSb.isNotEmpty()) {
                            val line = lineSb.toString().trim()
                            if (line.isNotEmpty()) {
                                onLine?.invoke(line)
                            }
                            lineSb.clear()
                        }
                    } else {
                        lineSb.append(c)
                    }
                }
            }
            if (lineSb.isNotEmpty()) {
                val line = lineSb.toString().trim()
                if (line.isNotEmpty()) {
                    onLine?.invoke(line)
                }
                lineSb.clear()
            }
        }
    } finally {
        val status = pclose(fp)
        return status
    }
}

fun runYtDlpCapture(args: List<String>): String? {
    val ytdlp = getExistingYtDlpBinary() ?: return null
    val python = getExistingPythonBinary()

    val cmdList = mutableListOf<String>()
    if (python != null && (ytdlp.endsWith(".py") || !isExecutable(ytdlp))) {
        cmdList.add(escapeShellArg(python))
    }
    cmdList.add(escapeShellArg(ytdlp))
    cmdList.addAll(args.map { escapeShellArg(it) })

    val fullCmd = cmdList.joinToString(" ")
    val sb = StringBuilder()
    val code = executeCommandStreaming(fullCmd) { line ->
        sb.append(line).append("\n")
    }
    return if (code == 0 || sb.isNotEmpty()) sb.toString().trim() else null
}
