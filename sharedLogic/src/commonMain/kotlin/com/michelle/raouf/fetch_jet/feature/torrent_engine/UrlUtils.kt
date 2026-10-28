package com.michelle.raouf.fetch_jet.feature.torrent_engine

fun extractReferer(urlStr: String): String {
    return try {
        val schemeEnd = urlStr.indexOf("://")
        if (schemeEnd != -1) {
            val nextSlash = urlStr.indexOf('/', schemeEnd + 3)
            if (nextSlash != -1) {
                urlStr.substring(0, nextSlash + 1)
            } else {
                "$urlStr/"
            }
        } else {
            urlStr
        }
    } catch (_: Exception) {
        urlStr
    }
}