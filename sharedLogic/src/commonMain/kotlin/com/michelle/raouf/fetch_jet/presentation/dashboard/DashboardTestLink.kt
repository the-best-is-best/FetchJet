package com.michelle.raouf.fetch_jet.presentation.dashboard

import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.michelle.raouf.fetch_jet.feature.http_downloader.LinkProbeCode
import com.michelle.raouf.fetch_jet.feature.http_downloader.LinkProbeResult
import com.michelle.raouf.fetch_jet.feature.http_downloader.canProbeLink
import com.michelle.raouf.fetch_jet.feature.http_downloader.probeLink
import com.tbib.kmp_localization.LocalizationManager
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TestLinkUi(
    val url: String,
    val testing: Boolean = true,
    val result: LinkProbeResult? = null
) {
    val ok: Boolean get() = result is LinkProbeResult.Ok
}

fun DashboardUiState.canTestLink(): Boolean =
    linkList.isEmpty() && detectedEngine == EngineType.HTTP_DIRECT && canProbeLink(inputUrl)

internal fun DashboardViewModel.testLink() {
    val state = _uiState.value
    if (!state.canTestLink() || state.testLink?.testing == true) return
    val shown = state.inputUrl
    val url = com.michelle.raouf.fetch_jet.feature.queue_manager.codeHostRawUrl(shown.trim())
    testLinkJob?.cancel()
    _uiState.update { it.copy(testLink = TestLinkUi(url = url)) }
    testLinkJob = scope.launch {
        val probed = probeLink(url, headersForLink(shown))
        val result = if (probed is LinkProbeResult.Ok && probed.fileName.isNotBlank()) {
            probed.copy(fileName = queueRepository.pathResolver.sanitizeFileName(probed.fileName))
        } else probed
        _uiState.update { st ->
            val current = st.testLink
            if (current != null && current.testing && current.url == url) {
                st.copy(testLink = current.copy(testing = false, result = result))
            } else st
        }
    }
}

internal fun DashboardViewModel.clearTestLink() {
    testLinkJob?.cancel()
    testLinkJob = null
    _uiState.update { if (it.testLink == null) it else it.copy(testLink = null) }
}

fun testLinkText(test: TestLinkUi?, suggestedFileName: String, formatSize: (Long) -> String): String {
    if (test == null || test.testing) return ""
    val strings = LocalizationManager.Strings
    return when (val result = test.result) {
        null -> ""
        is LinkProbeResult.Ok -> buildString {
            append(strings.testLinkOk)
            append(" · ")
            if (result.sizeBytes > 0L) {
                append(formatSize(result.sizeBytes))
                append(" · ")
                append(if (result.resumable) strings.testLinkResumable else strings.testLinkNotResumable)
            } else append(strings.testLinkSizeUnknown)
            if (result.fileName.isNotBlank() && result.fileName != suggestedFileName) {
                append(" · ")
                append(strings.testLinkServerName)
                append(": ")
                append(result.fileName)
            }
        }

        is LinkProbeResult.Failed -> {
            val text = when (result.code) {
                LinkProbeCode.REFUSED -> strings.testLinkRefused
                LinkProbeCode.NOT_FOUND -> strings.testLinkNotFound
                LinkProbeCode.LOGIN_NEEDED -> strings.testLinkLoginNeeded
                LinkProbeCode.BUSY -> strings.testLinkServerBusy
                LinkProbeCode.SERVER_ERROR -> strings.testLinkServerError
                LinkProbeCode.WEB_PAGE -> strings.testLinkWebPage
                LinkProbeCode.ROBOT -> strings.testLinkRobot
                LinkProbeCode.TIMEOUT -> strings.testLinkTimeout
                LinkProbeCode.NO_CONNECTION -> strings.testLinkNoConnection
                LinkProbeCode.SECURE_FAILED -> strings.testLinkSecureFailed
                LinkProbeCode.REDIRECTS -> strings.testLinkRedirects
                LinkProbeCode.INVALID_LINK -> strings.testLinkInvalid
                LinkProbeCode.FOLDER -> strings.testLinkFolder
                LinkProbeCode.FTP_LOGIN -> strings.testLinkFtpLogin
                LinkProbeCode.FTP_PROXY -> strings.testLinkFtpProxy
                LinkProbeCode.FTP_NO_TOOL -> strings.testLinkFtpNoTool
                LinkProbeCode.FTP_ERROR -> strings.testLinkFtpError
                else -> strings.testLinkHttpError
            }
            val numbered = result.status >= 300 && result.code in setOf(
                LinkProbeCode.REFUSED, LinkProbeCode.NOT_FOUND, LinkProbeCode.LOGIN_NEEDED,
                LinkProbeCode.BUSY, LinkProbeCode.SERVER_ERROR, LinkProbeCode.HTTP_ERROR
            )
            if (numbered) "$text (${result.status})" else text
        }
    }
}
