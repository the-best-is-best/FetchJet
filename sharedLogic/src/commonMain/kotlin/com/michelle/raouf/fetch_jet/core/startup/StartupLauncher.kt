package com.michelle.raouf.fetch_jet.core.startup

import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import kotlin.concurrent.Volatile

/**
 * "Start FetchJet when I log in" (desktop only, via github.com/vinceglb/AutoLaunch):
 * Windows registry Run key, macOS LaunchAgent, Linux autostart entry. The OS starts the app
 * with `--autostart=true`.
 */
expect object StartupLauncher {
    /** false on Android/iOS, and on desktop when run from Gradle/an IDE instead of the installed app. */
    val isSupported: Boolean
    suspend fun isEnabled(): Boolean
    suspend fun setEnabled(enabled: Boolean): Result<Unit>
}

/** Argument the OS passes when it starts FetchJet at login. */
const val AUTOSTART_ARG = "--autostart=true"

/** Startup preferences (settings folder, startup.properties). */
object StartupPreferences {
    private val file get() = "${PathResolver().getSettingsDirectory()}/startup.properties".toPath()

    /** Set by the desktop launcher when the OS started the app at login. */
    @Volatile
    var launchedAtLogin: Boolean = false

    /** Started at login: stay in the tray instead of opening the window. */
    var startHidden: Boolean
        get() = read("startHidden")?.toBooleanStrictOrNull() ?: true
        set(value) = write("startHidden", value.toString())

    private fun read(key: String): String? = try {
        if (!FileSystem.SYSTEM.exists(file)) null
        else FileSystem.SYSTEM.read(file) { readUtf8() }.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("$key=") }
            ?.substringAfter('=')?.trim()
    } catch (_: Throwable) {
        null
    }

    private fun write(key: String, value: String) {
        try {
            val lines = if (FileSystem.SYSTEM.exists(file))
                FileSystem.SYSTEM.read(file) { readUtf8() }.lineSequence()
                    .filter { it.isNotBlank() && !it.trim().startsWith("$key=") }.toMutableList()
            else mutableListOf("# FetchJet startup settings")
            lines.add("$key=$value")
            file.parent?.let { FileSystem.SYSTEM.createDirectories(it) }
            FileSystem.SYSTEM.write(file) { writeUtf8(lines.joinToString("\n") + "\n") }
        } catch (_: Throwable) {
        }
    }
}
