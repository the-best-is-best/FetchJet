package com.michelle.raouf.fetch_jet.core.update

/**
 * The app version - the ONE place to change it before a release.
 *
 * androidApp (versionName / versionCode) and desktopApp (packageVersion) read this line from
 * their build.gradle.kts, and the release job checks that the `v*` tag matches it, so the
 * update check, the installers and the GitHub release always agree.
 * (iOS: keep MARKETING_VERSION in iosApp/Configuration/Config.xcconfig the same.)
 */
const val APP_VERSION = "1.3.1"

/**
 * The browser extension version this app ships and works with (extension/manifest.json). An
 * extension at this version or newer works as is; an older one is shown as "needs an update".
 * The desktop build fails when manifest.json says something else.
 */
const val EXTENSION_VERSION = "3.3.0"

/** True when extension version [found] is older than [wanted] ("3.2.63" < "3.3.0"). */
fun isOlderExtension(found: String, wanted: String = EXTENSION_VERSION): Boolean {
    val a = found.split('.').map { it.toIntOrNull() ?: 0 }
    val b = wanted.split('.').map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x < y
    }
    return false
}

/** GitHub repository whose published releases are the updates. */
const val UPDATE_REPOSITORY = "the-best-is-best/FetchJet"
