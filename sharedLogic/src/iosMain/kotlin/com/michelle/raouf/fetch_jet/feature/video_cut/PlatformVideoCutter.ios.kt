package com.michelle.raouf.fetch_jet.feature.video_cut

import com.michelle.raouf.fetch_jet.core.util.currentEpochMs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

actual object PlatformVideoCutter {
    private val NATIVE = setOf("mp4", "m4v", "mov")

    actual fun outputExtension(sourceExtension: String): String? {
        val ext = sourceExtension.lowercase()
        if (IosVideoCutBridgeRegistry.bridge == null || ext !in NATIVE) return null
        return if (ext == "mov") "mov" else "mp4"
    }

    actual suspend fun durationMs(input: String): Long? {
        val bridge = IosVideoCutBridgeRegistry.bridge ?: return null
        if (!bridge.canCut(input)) return null
        return withContext(Dispatchers.IO) { bridge.durationMs(input) }.takeIf { it > 0L }
    }

    actual suspend fun cut(
        input: String,
        output: String,
        startMs: Long,
        endMs: Long,
        onProgress: (VideoCutProgress) -> Unit
    ): Boolean? {
        val bridge = IosVideoCutBridgeRegistry.bridge ?: return null
        if (!bridge.canCut(input) || output.substringAfterLast('.', "").lowercase() !in NATIVE) return null
        val runId = "cut-${currentEpochMs()}-${input.hashCode()}"
        val code = coroutineScope {
            val work = async(Dispatchers.IO) {
                bridge.cut(runId, input, output, startMs, endMs) { fraction, elapsedMs, etaMs ->
                    onProgress(VideoCutProgress(fraction, elapsedMs, etaMs))
                }
            }
            try {
                work.await()
            } catch (e: CancellationException) {
                bridge.cancel(runId)
                throw e
            }
        }
        return when (code) {
            1 -> true
            0 -> false
            else -> null
        }
    }
}
