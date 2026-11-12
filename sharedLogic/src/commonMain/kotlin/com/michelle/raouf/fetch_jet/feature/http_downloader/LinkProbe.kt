package com.michelle.raouf.fetch_jet.feature.http_downloader

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull

sealed interface LinkProbeResult {
    data class Ok(val sizeBytes: Long, val resumable: Boolean, val fileName: String) : LinkProbeResult
    data class Failed(val code: String, val status: Int = 0) : LinkProbeResult
}

object LinkProbeCode {
    const val REFUSED = "refused"
    const val NOT_FOUND = "not found"
    const val LOGIN_NEEDED = "login needed"
    const val BUSY = "busy"
    const val SERVER_ERROR = "server error"
    const val HTTP_ERROR = "http error"
    const val WEB_PAGE = "web page"
    const val ROBOT = "robot check"
    const val TIMEOUT = "timeout"
    const val NO_CONNECTION = "no connection"
    const val SECURE_FAILED = "secure connection failed"
    const val REDIRECTS = "too many redirects"
    const val INVALID_LINK = "invalid link"
    const val FOLDER = "folder"
    const val FTP_LOGIN = "ftp login refused"
    const val FTP_PROXY = "ftp proxy"
    const val FTP_NO_TOOL = "ftp tls tool missing"
    const val FTP_ERROR = "ftp error"
}

const val LINK_PROBE_TIMEOUT_MS = 20_000L

fun canProbeLink(url: String): Boolean {
    val s = url.trim()
    if (s.any { it.isWhitespace() }) return false
    return s.startsWith("http://", ignoreCase = true) || s.startsWith("https://", ignoreCase = true) || isFtpUrl(s)
}

private val robotMarkers = listOf(
    "are you a robot", "not a robot", "captcha", "verify you are human", "verify you are a human",
    "just a moment", "checking your browser", "cf-chl", "challenge-platform", "attention required"
)

internal fun looksLikeRobotCheck(pageText: String): Boolean {
    if (pageText.isBlank()) return false
    val text = pageText.lowercase()
    return robotMarkers.any { text.contains(it) }
}

internal fun serverFileName(initialUrl: String, finalUrl: String, contentDisposition: String?): String {
    contentDispositionFileName(contentDisposition)?.let { return it }
    if (finalUrl == initialUrl) return ""
    val last = finalUrl.substringBefore('?').substringBefore('#').substringAfterLast('/').trim()
    val before = initialUrl.substringBefore('?').substringBefore('#').substringAfterLast('/').trim()
    val hasExtension = last.contains('.') && last.substringAfterLast('.').length in 2..5
    return if (hasExtension && last != before) percentDecode(last) else ""
}

internal fun classifyHttpProbe(
    status: Int,
    contentType: String,
    contentLength: Long,
    acceptRanges: Boolean,
    pageText: String = "",
    fileName: String = ""
): LinkProbeResult {
    val html = contentType.lowercase().contains("text/html")
    if (html && looksLikeRobotCheck(pageText)) return LinkProbeResult.Failed(LinkProbeCode.ROBOT, status)
    return when {
        status in 200..299 ->
            if (html) LinkProbeResult.Failed(LinkProbeCode.WEB_PAGE, status)
            else LinkProbeResult.Ok(
                sizeBytes = if (contentLength > 0L) contentLength else -1L,
                resumable = acceptRanges && contentLength > 0L,
                fileName = fileName
            )

        status == 401 || status == 407 -> LinkProbeResult.Failed(LinkProbeCode.LOGIN_NEEDED, status)
        status == 403 -> LinkProbeResult.Failed(LinkProbeCode.REFUSED, status)
        status == 404 || status == 410 -> LinkProbeResult.Failed(LinkProbeCode.NOT_FOUND, status)
        status == 429 || status == 503 -> LinkProbeResult.Failed(LinkProbeCode.BUSY, status)
        status in 500..599 -> LinkProbeResult.Failed(LinkProbeCode.SERVER_ERROR, status)
        else -> LinkProbeResult.Failed(LinkProbeCode.HTTP_ERROR, status)
    }
}

internal fun probeErrorCode(message: String): String {
    val text = message.lowercase()
    return when {
        text.contains("timeout") || text.contains("timed out") -> LinkProbeCode.TIMEOUT
        text.contains("too many redirects") -> LinkProbeCode.REDIRECTS
        text.contains("certificate") || text.contains("handshake") || text.contains("ssl") || text.contains("tls") ->
            LinkProbeCode.SECURE_FAILED

        else -> LinkProbeCode.NO_CONNECTION
    }
}

internal fun ftpProbeCode(replyCode: Int, message: String): String {
    val text = message.lowercase()
    return when {
        replyCode == 530 || replyCode == 430 || replyCode == 331 || replyCode == 332 || text.contains("refused the login") ->
            LinkProbeCode.FTP_LOGIN

        replyCode == 421 || replyCode == 450 || text.contains("too many") -> LinkProbeCode.BUSY
        replyCode == 550 -> LinkProbeCode.NOT_FOUND
        text.contains("tls") -> LinkProbeCode.SECURE_FAILED
        text.contains("closed the connection") || text.contains("not ready") -> LinkProbeCode.NO_CONNECTION
        else -> LinkProbeCode.FTP_ERROR
    }
}

internal fun ftpToolProbeCode(exitCode: Int): String = when (exitCode) {
    6, 7 -> LinkProbeCode.NO_CONNECTION
    9, 78 -> LinkProbeCode.NOT_FOUND
    28 -> LinkProbeCode.TIMEOUT
    35, 51, 58, 59, 60, 64, 77, 82, 83, 90, 91 -> LinkProbeCode.SECURE_FAILED
    67 -> LinkProbeCode.FTP_LOGIN
    else -> LinkProbeCode.FTP_ERROR
}

private val probeDownloader by lazy { HttpChunkDownloader() }

suspend fun probeLink(url: String, requestHeaders: Map<String, String> = emptyMap()): LinkProbeResult {
    val link = url.trim()
    if (!canProbeLink(link)) return LinkProbeResult.Failed(LinkProbeCode.INVALID_LINK)
    return try {
        withTimeoutOrNull(LINK_PROBE_TIMEOUT_MS) {
            if (isFtpUrl(link)) probeFtpLink(link)
            else {
                val metadata = probeDownloader.resolveFinalUrlAndMetadata(link, requestHeaders)
                classifyHttpProbe(
                    status = metadata.statusCode,
                    contentType = metadata.contentType,
                    contentLength = metadata.contentLength,
                    acceptRanges = metadata.acceptRanges,
                    pageText = metadata.pageText,
                    fileName = serverFileName(link, metadata.finalUrl, metadata.contentDisposition)
                )
            }
        } ?: LinkProbeResult.Failed(LinkProbeCode.TIMEOUT)
    } catch (e: TimeoutCancellationException) {
        currentCoroutineContext().ensureActive()
        LinkProbeResult.Failed(LinkProbeCode.TIMEOUT)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        LinkProbeResult.Failed(probeErrorCode(e.message ?: e::class.simpleName.orEmpty()))
    }
}
