package com.michelle.raouf.fetch_jet.domain.model

enum class EngineType(val displayName: String) {
    HTTP_DIRECT("HTTP/HTTPS Direct"),
    BITTORRENT("BitTorrent P2P"),
    MEDIA_STREAM("Media Stream Extractor")
}