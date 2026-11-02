package com.michelle.raouf.fetch_jet.feature.media_extractor

import com.deniscerri.ytdl.core.RuntimeManager
import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.michelle.raouf.fetch_jet.core.log.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

actual suspend fun runLocalFfmpeg(args: List<String>): Boolean = withContext(Dispatchers.IO) {
    val ctx = PathResolver.appContext ?: return@withContext false
    try {
        RuntimeManager.init(ctx)
        val exe = RuntimeManager.ffmpegLocation.executable
        if (!exe.exists()) return@withContext false
        val pb = ProcessBuilder(listOf(exe.absolutePath) + args).redirectErrorStream(true)
        RuntimeManager.getEnvironment().forEach { (k, v) -> if (v != null) pb.environment()[k] = v }
        val p = pb.start()
        val out = StringBuilder()
        val t = Thread {
            try {
                p.inputStream.bufferedReader()
                    .forEachLine { if (out.length < 4000) out.appendLine(it) }
            } catch (_: Exception) {
            }
        }
        t.isDaemon = true; t.start()
        if (!p.waitFor(30, TimeUnit.MINUTES)) {
            p.destroyForcibly(); return@withContext false
        }
        t.join(2000)
        if (p.exitValue() != 0) AppLog.e(
            "ffmpeg (local join) exit ${p.exitValue()}: ${
                out.toString().trim().takeLast(600)
            }"
        )
        p.exitValue() == 0
    } catch (e: Exception) {
        AppLog.e("ffmpeg (local join) could not run: ${e.message}"); false
    }
}
