package com.michelle.raouf.fetch_jet.presentation.dashboard

import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.michelle.raouf.fetch_jet.feature.media_extractor.HlsDownloader

fun isStreamPlaylistUrl(url: String): Boolean =
    HlsDownloader.isHlsUrl(url) || url.substringBefore('?').substringBefore('#').endsWith(".mpd", ignoreCase = true)

fun addNeedsFileName(
    engine: EngineType,
    url: String,
    suggestedFileName: String,
    typedFileName: String,
    listOrPlaylist: Boolean
): Boolean = (engine == EngineType.HTTP_DIRECT || (engine == EngineType.MEDIA_STREAM && isStreamPlaylistUrl(url))) &&
        url.isNotBlank() &&
        !listOrPlaylist &&
        suggestedFileName.isBlank() &&
        typedFileName.isBlank()
