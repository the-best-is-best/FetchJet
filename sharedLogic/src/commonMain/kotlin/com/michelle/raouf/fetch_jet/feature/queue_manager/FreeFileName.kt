package com.michelle.raouf.fetch_jet.feature.queue_manager

/** [fileName] when it is free, else the first free "name (n).ext". */
internal fun firstFreeFileName(fileName: String, isTaken: (String) -> Boolean): String {
    if (!isTaken(fileName)) return fileName
    val dot = fileName.lastIndexOf('.')
    val base = if (dot > 0) fileName.substring(0, dot) else fileName
    val ext = if (dot > 0) fileName.substring(dot) else ""
    var n = 1
    while (isTaken("$base ($n)$ext")) n++
    return "$base ($n)$ext"
}
