package com.michelle.raouf.fetch_jet.ui

import androidx.compose.runtime.Composable

@Composable
internal expect fun InAppBrowser(startUrl: String, onClose: () -> Unit)

internal fun browserAddressToUrl(typed: String): String {
    val text = typed.trim()
    if (text.isEmpty()) return ""
    if (text.contains("://")) return text
    val looksLikeAddress = !text.contains(' ') && (text.contains('.') || text.startsWith("localhost"))
    return if (looksLikeAddress) "https://$text"
    else "https://www.google.com/search?q=" + text.replace("%", "%25").replace("&", "%26").replace("#", "%23").replace("+", "%2B").replace(' ', '+')
}
