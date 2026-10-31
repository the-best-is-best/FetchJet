package com.michelle.raouf.fetch_jet.core.update

/**
 * The app version - the ONE place to change it before a release.
 *
 * androidApp (versionName / versionCode) and desktopApp (packageVersion) read this line from
 * their build.gradle.kts, and the release job checks that the `v*` tag matches it, so the
 * update check, the installers and the GitHub release always agree.
 * (iOS: keep MARKETING_VERSION in iosApp/Configuration/Config.xcconfig the same.)
 */
const val APP_VERSION = "1.0.1"

/** GitHub repository whose published releases are the updates. */
const val UPDATE_REPOSITORY = "the-best-is-best/FetchJet"
