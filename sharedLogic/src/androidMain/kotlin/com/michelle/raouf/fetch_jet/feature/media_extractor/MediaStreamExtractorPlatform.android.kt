package com.michelle.raouf.fetch_jet.feature.media_extractor

import android.content.Context
import com.deniscerri.ytdl.core.RuntimeManager
import com.deniscerri.ytdl.core.models.YTDLRequest
import com.deniscerri.ytdl.util.FileUtil
import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.michelle.raouf.fetch_jet.core.log.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

private val isUpdatingYtDlp = AtomicBoolean(false)

fun triggerBackgroundYtDlpUpdate(context: Context) {
    if (isUpdatingYtDlp.compareAndSet(false, true)) {
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                RuntimeManager.init(context)
                RuntimeManager.updateYTDL(context, RuntimeManager.UpdateChannel.STABLE)
            } catch (_: Exception) {
            } finally {
                isUpdatingYtDlp.set(false)
            }
        }
    }
}

internal fun ensureRuntimeInitialized(): Boolean {
    val context = PathResolver.appContext ?: return false
    return try {
        RuntimeManager.init(context)
        RuntimeManager.assertInit()
        true
    } catch (_: Exception) {
        false
    }
}

actual suspend fun resolvePlatformStream(url: String): String? = withContext(Dispatchers.IO) {
    if (!ensureRuntimeInitialized()) return@withContext null

    try {
        val request = YTDLRequest(url)
        request.addOption("-g")
        request.addOption("--no-warnings")
        PathResolver.appContext?.let { ctx ->
            FileUtil.getCookieFile(ctx) { cookiePath ->
                request.addOption("--cookies", cookiePath)
            }
        }

        val response = RuntimeManager.getInstance().execute(request)
        if (response.exitCode == 0 && response.out.isNotBlank()) {
            return@withContext response.out.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
        }
    } catch (_: Exception) {
    }

    null
}

actual suspend fun inspectPlatformPlaylist(url: String): MediaPlaylist? =
    withContext(Dispatchers.IO) {
        val trimmedUrl = normalizePlaylistUrl(url)
        if (trimmedUrl.isEmpty() || !ensureRuntimeInitialized()) return@withContext null
        val context = PathResolver.appContext ?: return@withContext null
        val request = YTDLRequest(trimmedUrl)
        request.addOption("--flat-playlist")
        request.addOption("-J")
        request.addOption("--no-warnings")
        request.addOption("--yes-playlist")
        FileUtil.getCookieFile(context) { cookiePath ->
            request.addOption("--cookies", cookiePath)
        }
        val response = try {
            RuntimeManager.getInstance().execute(request)
        } catch (_: Exception) {
            null
        } ?: return@withContext null
        parsePlaylistJson(response.out)
    }

actual fun lastMediaInspectionError(): String? = null

actual suspend fun runYtDlpJson(
    url: String,
    selector: String,
    headers: Map<String, String>
): String? =
    withContext(Dispatchers.IO) {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isEmpty() || !ensureRuntimeInitialized()) return@withContext null
        val context = PathResolver.appContext ?: return@withContext null
        val request = YTDLRequest(trimmedUrl)
        request.addOption("-j")
        request.addOption("--no-warnings")
        request.addOption("--no-playlist")
        request.addOption("-f", selector)
        ytDlpHeaderArgs(headers, url).chunked(2).forEach { (k, v) -> request.addOption(k, v) }
        FileUtil.getCookieFile(context) { cookiePath -> request.addOption("--cookies", cookiePath) }
        val response = try {
            RuntimeManager.getInstance().execute(request)
        } catch (e: Exception) {
            AppLog.d {
                "runYtDlpJson: yt-dlp failed: ${
                    e.message?.lineSequence()?.lastOrNull { it.isNotBlank() }
                }"
            }
            null
        }
        response?.out?.trim()?.takeIf { it.isNotBlank() }
    }
