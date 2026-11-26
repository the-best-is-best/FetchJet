package com.michelle.raouf.fetch_jet.core.util

/**
 * Which file names are video or audio. [VIDEO] / [AUDIO] say what a file is (hiding, badges, folders,
 * compress); [PLAYABLE_VIDEO] / [PLAYABLE_AUDIO] are the part the in-app players open on every
 * platform (Media3 on Android, AVPlayer on iOS, libmpv on desktop). The other media files open in
 * the system's app, hidden ones through a decrypted temporary copy.
 */
object MediaExtensions {
    val PLAYABLE_VIDEO: Set<String> = setOf("mp4", "mkv", "webm", "mov", "avi", "m4v", "3gp", "ts", "flv")
    val PLAYABLE_AUDIO: Set<String> = setOf("mp3", "m4a", "m4b", "aac", "flac", "wav", "ogg", "oga", "opus", "weba", "mka")

    val VIDEO: Set<String> = PLAYABLE_VIDEO + setOf("m2ts", "mts", "mpg", "mpeg", "wmv", "3g2", "ogv")
    val AUDIO: Set<String> = PLAYABLE_AUDIO + setOf("wma", "aiff", "aif", "amr", "ac3")

    fun extensionOf(name: String): String = name.substringAfterLast('.', "").lowercase()

    fun isVideo(name: String): Boolean = extensionOf(name) in VIDEO
    fun isAudio(name: String): Boolean = extensionOf(name) in AUDIO
    fun isMedia(name: String): Boolean = isVideo(name) || isAudio(name)

    fun isPlayableVideo(name: String): Boolean = extensionOf(name) in PLAYABLE_VIDEO
    fun isPlayableAudio(name: String): Boolean = extensionOf(name) in PLAYABLE_AUDIO
}
