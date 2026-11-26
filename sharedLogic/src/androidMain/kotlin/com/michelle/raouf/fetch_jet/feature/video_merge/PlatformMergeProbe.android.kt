package com.michelle.raouf.fetch_jet.feature.video_merge

import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual object PlatformMergeProbe {
    actual suspend fun info(path: String): MergeStreamInfo? = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(path)
            var video: MediaFormat? = null
            var audio: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (video == null && mime.startsWith("video/")) video = format
                if (audio == null && mime.startsWith("audio/")) audio = format
            }
            val v = video ?: return@withContext null
            MergeStreamInfo(
                extension = path.substringAfterLast('.', "").lowercase(),
                videoCodec = v.getString(MediaFormat.KEY_MIME).orEmpty(),
                dimensions = "${v.intOr(MediaFormat.KEY_WIDTH)}x${v.intOr(MediaFormat.KEY_HEIGHT)}",
                audioCodec = audio?.getString(MediaFormat.KEY_MIME).orEmpty(),
                sampleRate = audio?.intOr(MediaFormat.KEY_SAMPLE_RATE)?.toString().orEmpty(),
                channels = audio?.intOr(MediaFormat.KEY_CHANNEL_COUNT)?.toString().orEmpty()
            )
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun MediaFormat.intOr(key: String): Int = if (containsKey(key)) getInteger(key) else 0
}
