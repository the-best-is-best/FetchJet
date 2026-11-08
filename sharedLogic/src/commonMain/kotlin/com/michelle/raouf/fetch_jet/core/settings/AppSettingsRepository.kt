package com.michelle.raouf.fetch_jet.core.settings

import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.michelle.raouf.fetch_jet.core.filesystem.relocateAppPath
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM

data class AppSettings(
    val destinationPath: String,
    val disableTorrentUpload: Boolean = false,
    val isDarkMode: Boolean = true,
    /** "system" (follow the device), "light" or "dark". */
    val themeMode: String = "system",
    val globalDownloadSpeedLimitBytes: Long = 0L,
    val globalUploadSpeedLimitBytes: Long = 0L,
    val speedDisplayUnit: String = "MB/s",
    val language: String = "system",
    val speedPresetLowBytes: Long = 256L * 1024,
    val speedPresetLowUnit: String = "KB/s",
    val speedPresetMediumBytes: Long = 2L * 1024 * 1024,
    val speedPresetMediumUnit: String = "MB/s",
    val speedPresetFastBytes: Long = 3L * 1024 * 1024,
    val speedPresetFastUnit: String = "MB/s",
    val speedPresetHighBytes: Long = 20L * 1024 * 1024,
    val speedPresetHighUnit: String = "MB/s"
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
        var themeMode = "system"
        var dlLimit = 0L
        var ulLimit = 0L
        var speedUnit = "MB/s"
        var language = "system"
        var lowBytes = 256L * 1024
        var lowUnit = "KB/s"
        var mediumBytes = 5L * 1024 * 1024
        var mediumUnit = "MB/s"
        var fastBytes = 10L * 1024 * 1024
        var fastUnit = "MB/s"
        var highBytes = 20L * 1024 * 1024
        var highUnit = "MB/s"

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
                        "destinationPath" -> if (value.isNotBlank()) destination =
                            relocateAppPath(value)
                        "disableTorrentUpload" -> disableUpload =
                            value.toBooleanStrictOrNull() ?: false

                        "isDarkMode" -> darkMode = value.toBooleanStrictOrNull() ?: true
                        "themeMode" -> if (value in setOf("system", "light", "dark")) themeMode = value
                        "globalDownloadSpeedLimitBytes" -> dlLimit = value.toLongOrNull() ?: 0L
                        "globalUploadSpeedLimitBytes" -> ulLimit = value.toLongOrNull() ?: 0L
                        "speedDisplayUnit" -> if (value.isNotBlank()) speedUnit = value
                        "language" -> if (value.isNotBlank()) language = value
                        "speedPresetLowBytes" -> lowBytes = value.toLongOrNull() ?: lowBytes
                        "speedPresetLowUnit" -> if (value.isNotBlank()) lowUnit = value
                        "speedPresetMediumBytes" -> mediumBytes =
                            value.toLongOrNull() ?: mediumBytes

                        "speedPresetMediumUnit" -> if (value.isNotBlank()) mediumUnit = value
                        "speedPresetFastBytes" -> fastBytes = value.toLongOrNull() ?: fastBytes
                        "speedPresetFastUnit" -> if (value.isNotBlank()) fastUnit = value
                        "speedPresetHighBytes" -> highBytes = value.toLongOrNull() ?: highBytes
                        "speedPresetHighUnit" -> if (value.isNotBlank()) highUnit = value
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
            themeMode = themeMode,
            globalDownloadSpeedLimitBytes = dlLimit,
            globalUploadSpeedLimitBytes = ulLimit,
            speedDisplayUnit = normalizedSpeedUnit,
            language = language,
            speedPresetLowBytes = lowBytes,
            speedPresetLowUnit = lowUnit,
            speedPresetMediumBytes = mediumBytes,
            speedPresetMediumUnit = mediumUnit,
            speedPresetFastBytes = fastBytes,
            speedPresetFastUnit = fastUnit,
            speedPresetHighBytes = highBytes,
            speedPresetHighUnit = highUnit
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
                writeUtf8("themeMode=${settings.themeMode}\n")
                writeUtf8("globalDownloadSpeedLimitBytes=${settings.globalDownloadSpeedLimitBytes}\n")
                writeUtf8("globalUploadSpeedLimitBytes=${settings.globalUploadSpeedLimitBytes}\n")
                writeUtf8("speedDisplayUnit=${settings.speedDisplayUnit}\n")
                writeUtf8("language=${settings.language}\n")
                writeUtf8("speedPresetLowBytes=${settings.speedPresetLowBytes}\n")
                writeUtf8("speedPresetLowUnit=${settings.speedPresetLowUnit}\n")
                writeUtf8("speedPresetMediumBytes=${settings.speedPresetMediumBytes}\n")
                writeUtf8("speedPresetMediumUnit=${settings.speedPresetMediumUnit}\n")
                writeUtf8("speedPresetFastBytes=${settings.speedPresetFastBytes}\n")
                writeUtf8("speedPresetFastUnit=${settings.speedPresetFastUnit}\n")
                writeUtf8("speedPresetHighBytes=${settings.speedPresetHighBytes}\n")
                writeUtf8("speedPresetHighUnit=${settings.speedPresetHighUnit}\n")
            }
        } catch (_: Exception) {
        }
    }
}
