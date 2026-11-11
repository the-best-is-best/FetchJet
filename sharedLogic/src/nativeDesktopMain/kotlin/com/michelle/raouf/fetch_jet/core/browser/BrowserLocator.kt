package com.michelle.raouf.fetch_jet.core.browser

internal class BrowserPaths(
    val windows: List<String> = emptyList(),
    val mac: List<String> = emptyList(),
    val linux: List<String> = emptyList(),
)

internal expect object BrowserLocator {
    fun hasCandidates(paths: BrowserPaths): Boolean
    fun find(paths: BrowserPaths): String?
    fun start(executable: String, args: List<String>): Boolean
}
