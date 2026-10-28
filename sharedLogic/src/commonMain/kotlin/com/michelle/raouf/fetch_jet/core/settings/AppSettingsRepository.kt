package com.michelle.raouf.fetch_jet.core.settings

import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM

data class AppSettings(
    val destinationPath: String,
    val disableTorrentUpload: Boolean = false,
    val isDarkMode: Boolean = true,
    val globalDownloadSpeedLimitBytes: Long = 0L,
    val globalUploadSpeedLimitBytes: Long = 0L,
    val speedDisplayUnit: String = "MB/s"
)

class AppSettingsRepository(
    private val pathResolver: PathResolver = PathResolver()
) {
    private val settingsFile = "${pathResolver.getSettingsDirectory()}/settings.properties".toPath()

    fun loadSettings(): AppSettings {
        val defaultPath = pathResolver.getDownloadsDirectory()
        var destination = defaultPath
        var disableUpload = false
        var darkMode = true
        var dlLimit = 0L
        var ulLimit = 0L
        var speedUnit = "MB/s"

        try {
            if (FileSystem.SYSTEM.exists(settingsFile)) {
                val content = FileSystem.SYSTEM.read(settingsFile) { readUtf8() }
                val lines = content.lineSequence()
                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.startsWith("#") || !trimmed.contains("=")) continue
                    val key = trimmed.substringBefore("=").trim()
                    val value = trimmed.substringAfter("=").trim()
                    when (key) {
                        "destinationPath" -> if (value.isNotBlank()) destination = value
                        "disableTorrentUpload" -> disableUpload =
                            value.toBooleanStrictOrNull() ?: false

                        "isDarkMode" -> darkMode = value.toBooleanStrictOrNull() ?: true
                        "globalDownloadSpeedLimitBytes" -> dlLimit = value.toLongOrNull() ?: 0L
                        "globalUploadSpeedLimitBytes" -> ulLimit = value.toLongOrNull() ?: 0L
                        "speedDisplayUnit" -> if (value.isNotBlank()) speedUnit = value
                    }
                }
            }
        } catch (_: Exception) {
        }

        val normalizedSpeedUnit = when (speedUnit) {
            "BYTES_PER_SEC" -> "MB/s"
            "BITS_PER_SEC" -> "Mbps"
            "BOTH" -> "MB/s"
            else -> speedUnit
        }

        return AppSettings(
            destinationPath = destination,
            disableTorrentUpload = disableUpload,
            isDarkMode = darkMode,
            globalDownloadSpeedLimitBytes = dlLimit,
            globalUploadSpeedLimitBytes = ulLimit,
            speedDisplayUnit = normalizedSpeedUnit
        )
    }

    fun saveSettings(settings: AppSettings) {
        try {
            settingsFile.parent?.let { FileSystem.SYSTEM.createDirectories(it) }
            FileSystem.SYSTEM.write(settingsFile) {
                writeUtf8("# FetchJet Settings\n")
                writeUtf8("destinationPath=${settings.destinationPath}\n")
                writeUtf8("disableTorrentUpload=${settings.disableTorrentUpload}\n")
                writeUtf8("isDarkMode=${settings.isDarkMode}\n")
                writeUtf8("globalDownloadSpeedLimitBytes=${settings.globalDownloadSpeedLimitBytes}\n")
                writeUtf8("globalUploadSpeedLimitBytes=${settings.globalUploadSpeedLimitBytes}\n")
                writeUtf8("speedDisplayUnit=${settings.speedDisplayUnit}\n")
            }
        } catch (_: Exception) {
        }
    }
}
