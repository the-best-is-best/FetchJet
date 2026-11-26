package com.michelle.raouf.fetch_jet.core.filesystem

/** Folders and files that no other user of this computer can change (POSIX: ours, mode 0700). */
internal expect object PrivateFolder {
    /** Creates [path] if needed; true when it is a real folder owned by this user and closed to others. */
    fun ensure(path: String): Boolean

    /** true when [path] is a regular file (not a link) owned by this user that nobody else can write. */
    fun isOwnFile(path: String): Boolean
}
