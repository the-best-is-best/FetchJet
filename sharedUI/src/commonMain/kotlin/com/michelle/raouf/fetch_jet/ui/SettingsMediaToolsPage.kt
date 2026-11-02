package com.michelle.raouf.fetch_jet.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.core.i18n.start.localizeStep
import com.tbib.kmp_localization.LocalizationManager
import com.michelle.raouf.fetch_jet.theme.StatusCompleted
import com.michelle.raouf.fetch_jet.theme.StatusPaused

@Composable
internal fun MediaToolsPage(
    ytDlpStatus: String,
    isYtDlpInstalled: Boolean,
    isFfmpegInstalled: Boolean,
    jsRuntimeName: String,
    isJsRuntimeReady: Boolean,
    toolsDirectoryPath: String,
    isDownloadingYtDlp: Boolean,
    toolDownloadName: String?,
    toolDownloadFraction: Float?,
    pathResolver: PathResolver,
    onDownloadYtDlpClicked: () -> Unit
) {
    val ready = isYtDlpInstalled && isFfmpegInstalled
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        SettingsGroup(title = LocalizationManager.Strings.mediaToolsGroup) {
            SettingRow("yt-dlp", LocalizationManager.Strings.ytDlpHint) {
                if (isYtDlpInstalled) StatusLabel(
                    LocalizationManager.Strings.settingsReady,
                    StatusCompleted
                )
                else StatusLabel(
                    LocalizationManager.Strings.settingsMissing,
                    MaterialTheme.colorScheme.error
                )
            }
            SettingDivider()
            SettingRow("FFmpeg", LocalizationManager.Strings.ffmpegHint) {
                if (isFfmpegInstalled) StatusLabel(
                    LocalizationManager.Strings.settingsReady,
                    StatusCompleted
                )
                else StatusLabel(LocalizationManager.Strings.ffmpegMissing, StatusPaused)
            }
            if (jsRuntimeName.isNotBlank()) {
                SettingDivider()
                SettingRow(
                    LocalizationManager.Strings.jsRuntimeTitle(jsRuntimeName),
                    LocalizationManager.Strings.jsRuntimeHint
                ) {
                    if (isJsRuntimeReady) StatusLabel(
                        LocalizationManager.Strings.settingsReady,
                        StatusCompleted
                    )
                    else StatusLabel(
                        LocalizationManager.Strings.missingPress(if (isYtDlpInstalled && isFfmpegInstalled) LocalizationManager.Strings.mediaUpdateTools else LocalizationManager.Strings.mediaDownloadTheTools),
                        StatusPaused
                    )
                }
            }
            if (toolsDirectoryPath.isNotBlank()) {
                SettingDivider()
                SettingRow(
                    title = LocalizationManager.Strings.mediaToolsFolder,
                    subtitle = toolsDirectoryPath,
                    onClick = { pathResolver.openDirectoryInExplorer(toolsDirectoryPath) }
                ) {
                    Text(
                        LocalizationManager.Strings.open,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val content: @Composable () -> Unit = {
                if (isDownloadingYtDlp) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        when {
                            toolDownloadName == null -> if (ready) LocalizationManager.Strings.mediaUpdatingTools else LocalizationManager.Strings.mediaDownloadingTools
                            toolDownloadFraction == null -> LocalizationManager.Strings.mediaDownloadingTool(
                                toolDownloadName
                            )

                            else -> LocalizationManager.Strings.mediaDownloadingToolPercent(
                                toolDownloadName,
                                (toolDownloadFraction * 100).toInt()
                            )
                        }
                    )
                } else {
                    Text(
                        if (ready) LocalizationManager.Strings.mediaUpdateTools else LocalizationManager.Strings.mediaDownloadTheTools,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            if (ready) {
                OutlinedButton(
                    onClick = onDownloadYtDlpClicked,
                    enabled = !isDownloadingYtDlp,
                    shape = RoundedCornerShape(10.dp)
                ) { content() }
            } else {
                Button(
                    onClick = onDownloadYtDlpClicked,
                    enabled = !isDownloadingYtDlp,
                    shape = RoundedCornerShape(10.dp)
                ) { content() }
            }
        }
        if (isDownloadingYtDlp && toolDownloadName != null) {
            if (toolDownloadFraction != null) {
                LinearProgressIndicator(
                    progress = { toolDownloadFraction },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
        if (ytDlpStatus.isNotBlank()) {
            Text(
                localizeStep(ytDlpStatus),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
