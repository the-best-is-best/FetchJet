package com.michelle.raouf.fetch_jet.feature.archive_extract

class ArchiveEvent(
    val name: String,
    val files: Int,
    val error: String?,
    val started: Boolean = false,
    val destination: String = "",
    val taskId: String = "",
    val needsPassword: Boolean = false,
    val wrongPassword: Boolean = false,
)
