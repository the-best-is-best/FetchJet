package com.michelle.raouf.fetch_jet.core.log

import com.michelle.raouf.fetch_jet.core.log.AppLog.verbose


/**
 * Diagnostic logging switch. Errors are always printed; the chatty lines (torrent peer
 * statistics every few seconds, engine details) only when [verbose] is on.
 *
 * Where the switch comes from:
 *  - build time: `fetchjet.verboseLogs=true` in gradle.properties, or the environment
 *    variable FETCHJET_VERBOSE_LOGS=true (GitHub Actions reads the repository variable
 *    of the same name), baked into the desktop launcher and Android BuildConfig;
 *  - run time (desktop): the same environment variable on the user's machine, so a
 *    release build can be made to log for a bug report without rebuilding.
 */
object AppLog {
    // Plain var on purpose: a momentarily stale read only affects whether a debug line prints,
    // and @Volatile is not available in common code on every Kotlin target.
    var verbose: Boolean = false

    /** Printed only in verbose mode; the message is built lazily. */
    inline fun d(message: () -> String) {
        if (verbose) println(message())
    }

    /** Always printed (goes to app.log on desktop, logcat on Android). */
    fun e(message: String) = println(message)
}
