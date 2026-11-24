package com.michelle.raouf.fetch_jet.ui

import android.webkit.CookieManager

internal actual fun clearBrowserLogin(site: String) {
    val manager = CookieManager.getInstance()
    val hosts = listOf(site, "www.$site")
    for (host in hosts) {
        val raw = manager.getCookie("https://$host").orEmpty()
        if (raw.isBlank()) continue
        for (pair in raw.split(";")) {
            val name = pair.substringBefore("=").trim()
            if (name.isEmpty()) continue
            manager.setCookie("https://$host", "$name=; Max-Age=0; Path=/")
            manager.setCookie("https://$host", "$name=; Max-Age=0; Path=/; Domain=$site")
            manager.setCookie("https://$host", "$name=; Max-Age=0; Path=/; Domain=.$site")
        }
    }
    manager.flush()
}
