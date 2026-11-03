package com.michelle.raouf.fetch_jet.core.network

import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.HttpHeaders
import io.ktor.util.AttributeKey

private val FirstSite = AttributeKey<String>("FetchJetFirstSite")

/**
 * Ktor's HttpRedirect removes only Authorization when a redirect leaves the host; the browser's
 * Cookie of the first site went along (an open redirect on a.com handed a.com's login to b.com).
 * Each hop of a redirect passes through here (installed after HttpRedirect, which the default
 * client and every `install(HttpRedirect)` place first); the redirect copies the attributes, so
 * the first site is known on every hop.
 */
internal val RedirectCookieGuard = createClientPlugin("FetchJetRedirectCookies") {
    on(Send) { request ->
        val site = SiteCookieJar.siteOf(request.url.toString())
        val first = request.attributes.getOrNull(FirstSite)
            ?: site.also { request.attributes.put(FirstSite, it) }
        if (first != site) request.headers.remove(HttpHeaders.Cookie)
        proceed(request)
    }
}
