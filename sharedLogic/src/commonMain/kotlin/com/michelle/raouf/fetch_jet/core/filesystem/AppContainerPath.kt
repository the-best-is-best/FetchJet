package com.michelle.raouf.fetch_jet.core.filesystem

/**
 * A path saved by an earlier install, pointed at this install's app folder. iOS gives the app a
 * new data container on reinstall, so a saved absolute path would point at a folder that no
 * longer belongs to the app. Elsewhere the path is returned unchanged.
 */
expect fun relocateAppPath(path: String): String
