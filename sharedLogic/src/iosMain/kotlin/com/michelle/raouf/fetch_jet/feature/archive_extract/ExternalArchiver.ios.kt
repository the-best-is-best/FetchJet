package com.michelle.raouf.fetch_jet.feature.archive_extract

actual object ExternalArchiver {
    actual fun available(): Boolean = false

    actual suspend fun extract(archive: String, into: String, password: String?, onProgress: (Float) -> Unit) {
        throw ArchiveException("this kind of archive needs 7-Zip, which this device cannot run")
    }
}
