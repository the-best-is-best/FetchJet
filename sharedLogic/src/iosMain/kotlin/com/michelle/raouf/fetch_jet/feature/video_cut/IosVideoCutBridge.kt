package com.michelle.raouf.fetch_jet.feature.video_cut

fun interface IosVideoCutProgressCallback {
    fun onProgress(fraction: Float, elapsedMs: Long, etaMs: Long)
}

interface IosVideoCutBridge {
    fun canCut(inputPath: String): Boolean

    fun durationMs(inputPath: String): Long

    fun cut(
        runId: String,
        inputPath: String,
        outputPath: String,
        startMs: Long,
        endMs: Long,
        progress: IosVideoCutProgressCallback
    ): Int

    fun cancel(runId: String)
}

object IosVideoCutBridgeRegistry {
    var bridge: IosVideoCutBridge? = null
}
