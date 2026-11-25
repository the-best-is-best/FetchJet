package com.michelle.raouf.fetch_jet.feature.video_cut

import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.michelle.raouf.fetch_jet.core.log.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

actual object PlatformVideoCutter {
    actual fun outputExtension(sourceExtension: String): String? = "mp4"

    actual suspend fun durationMs(input: String): Long? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(input)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    @OptIn(UnstableApi::class)
    actual suspend fun cut(
        input: String,
        output: String,
        startMs: Long,
        endMs: Long,
        onProgress: (VideoCutProgress) -> Unit
    ): Boolean? {
        val context = PathResolver.appContext ?: return false
        val partial = File(output.substringBeforeLast('.') + ".part.mp4")
        val target = File(output)
        val bitrate = sourceBitrate(input)
        var ok = false
        try {
            ok = withContext(Dispatchers.Main) {
                val eta = CutEta()
                onProgress(eta.update(0f))
                var watcher: kotlinx.coroutines.Job? = null
                val done = suspendCancellableCoroutine { continuation ->
                    val clipping = MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(startMs)
                        .setEndPositionMs(endMs)
                        .build()
                    val item = MediaItem.Builder()
                        .setUri(Uri.fromFile(File(input)))
                        .setClippingConfiguration(clipping)
                        .build()
                    val encoders = DefaultEncoderFactory.Builder(context)
                        .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(bitrate).build())
                        .build()
                    val transformer = Transformer.Builder(context)
                        .setVideoMimeType(MimeTypes.VIDEO_H264)
                        .setAudioMimeType(MimeTypes.AUDIO_AAC)
                        .setEncoderFactory(encoders)
                        .addListener(object : Transformer.Listener {
                            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                if (continuation.isActive) continuation.resume(true)
                            }

                            override fun onError(
                                composition: Composition,
                                exportResult: ExportResult,
                                exportException: ExportException
                            ) {
                                AppLog.e("video cut failed: ${exportException.errorCodeName}: ${exportException.message}")
                                if (continuation.isActive) continuation.resume(false)
                            }
                        })
                        .build()
                    val holder = ProgressHolder()
                    watcher = launch {
                        while (isActive) {
                            delay(400)
                            if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                                onProgress(eta.update(holder.progress / 100f))
                            }
                        }
                    }
                    continuation.invokeOnCancellation {
                        android.os.Handler(android.os.Looper.getMainLooper()).post { transformer.cancel(); partial.delete() }
                    }
                    transformer.start(item, partial.absolutePath)
                }
                watcher?.cancel()
                if (done) onProgress(eta.update(1f))
                done
            }
            ok = ok && partial.length() > 0L && (target.delete() || !target.exists()) && partial.renameTo(target)
            return ok
        } finally {
            if (!ok) partial.delete()
        }
    }

    private fun sourceBitrate(input: String): Int {
        val retriever = MediaMetadataRetriever()
        val bits = try {
            retriever.setDataSource(input)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                    ?.takeIf { it > 0L }?.let { File(input).length() * 8000L / it }
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
        return (bits ?: 8_000_000L).coerceIn(2_000_000L, 80_000_000L).toInt()
    }
}
