package com.michelle.raouf.fetch_jet.feature.archive_extract

internal actual suspend fun listWithExternalArchiver(archive: String, password: String?): List<ExternalEntry> =
    throw ArchiveException("this kind of archive needs 7-Zip, which this device cannot run")
