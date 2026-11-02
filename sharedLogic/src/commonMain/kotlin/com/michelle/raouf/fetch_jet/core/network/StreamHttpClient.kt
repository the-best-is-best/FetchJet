package com.michelle.raouf.fetch_jet.core.network

import io.ktor.client.HttpClient

/**
 * HTTP client for fetching media streams in pieces. Speaks HTTP/1.1 only: some video CDNs
 * (googlevideo) answer 403 to a ranged request over HTTP/2, while the same request over
 * HTTP/1.1 — what browsers' downloaders, curl and yt-dlp use — gets 206.
 */
expect fun createStreamHttpClient(): HttpClient

/** Diagnostics: the same ranged GET through the platform's plainest HTTP API; returns a short report. */
expect fun rawProbeForDiagnostics(url: String, userAgent: String): String
