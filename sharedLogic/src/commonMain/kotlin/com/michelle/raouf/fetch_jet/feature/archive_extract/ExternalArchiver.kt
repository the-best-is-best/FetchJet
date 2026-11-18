package com.michelle.raouf.fetch_jet.feature.archive_extract

class ArchivePasswordException(val wrong: Boolean) :
    ArchiveException(if (wrong) "the password is wrong" else "this archive has a password")

expect object ExternalArchiver {
    fun available(): Boolean

    suspend fun extract(archive: String, into: String, password: String?, onProgress: (Float) -> Unit)
}
