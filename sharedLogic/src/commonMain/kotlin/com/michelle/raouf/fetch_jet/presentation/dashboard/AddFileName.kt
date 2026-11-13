package com.michelle.raouf.fetch_jet.presentation.dashboard

import com.michelle.raouf.fetch_jet.domain.model.EngineType

fun addNeedsFileName(
    engine: EngineType,
    url: String,
    suggestedFileName: String,
    typedFileName: String,
    listOrPlaylist: Boolean
): Boolean = engine == EngineType.HTTP_DIRECT &&
        url.isNotBlank() &&
        !listOrPlaylist &&
        suggestedFileName.isBlank() &&
        typedFileName.isBlank()
