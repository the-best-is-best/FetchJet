package com.michelle.raouf.fetch_jet.core.log

/**
 * The app's own log file for bug reports. Desktop already writes ~/.fetch_jet/logs/app.log;
 * Android mirrors stdout/stderr into its private files dir and [export] copies that into the
 * public Downloads folder so the user can send it.
 */
expect object DiagnosticsLog {
    /** True where [export] does something (Android). */
    val canExport: Boolean

    /** Starts mirroring println/stack traces into the log file (call once at start). */
    fun install()

    /** Copies the log to a user-visible place; returns its path, or null when it failed. */
    fun export(): String?
}
