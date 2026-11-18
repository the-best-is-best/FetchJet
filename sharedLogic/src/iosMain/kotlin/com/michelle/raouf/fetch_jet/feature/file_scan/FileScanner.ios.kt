package com.michelle.raouf.fetch_jet.feature.file_scan

actual object FileScanner {
    actual val supported: Boolean = false
    actual val hasSystemScanner: Boolean = false
    actual suspend fun scan(path: String, sourceUrl: String, command: String): ScanResult = ScanResult(ScanOutcome.NO_SCANNER)
    actual fun markFromInternet(path: String, sourceUrl: String, pageUrl: String?) = Unit
}
