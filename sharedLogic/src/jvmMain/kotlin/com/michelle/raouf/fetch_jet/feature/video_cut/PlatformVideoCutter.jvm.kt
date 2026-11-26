package com.michelle.raouf.fetch_jet.feature.video_cut

actual object PlatformVideoCutter {
    actual fun outputExtension(sourceExtension: String): String? = null

    actual suspend fun durationMs(input: String): Long? = null

    actual suspend fun cut(
        input: String,
        output: String,
        startMs: Long,
        endMs: Long,
        onProgress: (VideoCutProgress) -> Unit
    ): Boolean? = null
}
