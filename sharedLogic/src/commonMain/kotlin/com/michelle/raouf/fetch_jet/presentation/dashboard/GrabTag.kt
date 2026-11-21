package com.michelle.raouf.fetch_jet.presentation.dashboard

fun grabTagOf(fileName: String, folder: String): String {
    val ext = fileName.trim().substringAfterLast('.', "")
    if (ext.isNotEmpty() && ext.length <= 5 && ext.all { it.isLetterOrDigit() && it.code < 128 }) return ext.uppercase()
    return folder.uppercase().take(5).ifBlank { "FILE" }
}

enum class GrabTagKind { VIDEO, AUDIO, OTHER }

fun grabTagKindOf(folder: String): GrabTagKind = when (folder) {
    "Video" -> GrabTagKind.VIDEO
    "Music" -> GrabTagKind.AUDIO
    else -> GrabTagKind.OTHER
}
