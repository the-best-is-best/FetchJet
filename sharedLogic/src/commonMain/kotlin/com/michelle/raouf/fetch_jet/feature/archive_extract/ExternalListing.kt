package com.michelle.raouf.fetch_jet.feature.archive_extract

class ExternalEntry(val path: String, val size: Long, val isDirectory: Boolean, val isLink: Boolean)

/** The entries 7-Zip reports for [archive], read before anything is written to disk. */
internal expect suspend fun listWithExternalArchiver(archive: String, password: String?): List<ExternalEntry>
