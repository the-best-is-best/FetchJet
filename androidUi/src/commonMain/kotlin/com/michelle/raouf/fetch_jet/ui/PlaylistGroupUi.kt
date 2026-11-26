package com.michelle.raouf.fetch_jet.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.presentation.dashboard.PlaylistGroup
import com.michelle.raouf.fetch_jet.presentation.dashboard.PlaylistLanguagesUi
import com.tbib.kmp_localization.LocalizationManager

/** A language code as the name one of the videos' tracks gave it, else the code itself. */
private fun languageName(code: String, tasks: List<DownloadTask>): String =
    tasks.firstNotNullOfOrNull { t -> t.subtitleTracks.firstOrNull { it.langCode == code }?.langName?.takeIf { n -> n.isNotBlank() } } ?: code

/**
 * The top of an open playlist: back, its name and count, Check for new videos, pause / resume
 * all, delete; under it the playlist's subtitle languages and "Add a subtitle language".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlaylistPageHeader(
    group: PlaylistGroup,
    languages: PlaylistLanguagesUi?,
    onBack: () -> Unit,
    onCheck: () -> Unit,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onDelete: () -> Unit,
    onHide: () -> Unit,
    onReadLanguages: () -> Unit,
    onAddLanguage: (String) -> Unit,
    onDismissLanguages: () -> Unit,
    onOpenFolder: (() -> Unit)? = null,
    onPlay: (() -> Unit)? = null,
) {
    val s = LocalizationManager.Strings
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        GroupHeaderRow(group.title, group.summaryText, onBack) {
            PageActions(group, onCheck, onPauseAll, onResumeAll, onDelete, onHide, onOpenFolder, onPlay)
        }
        if (group.isPage) return@Column
        val record = group.record
        val subtitlesLine = if (record.subtitleLanguages.isEmpty()) s.playlistNoSubtitles
        else s.playlistSubtitlesFor.replace("%1", record.subtitleLanguages.joinToString(", ") { languageName(it, group.tasks) })
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("💬 $subtitlesLine", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            val page = languages?.takeIf { it.playlistId == group.id }
            if (page == null) {
                TextButton(onClick = onReadLanguages) { Text("+ " + s.playlistAddLanguage, fontSize = 12.sp) }
            }
        }
        val page = languages?.takeIf { it.playlistId == group.id }
        if (page != null) {
            val note = when {
                page.reading -> s.playlistReadingLanguages
                page.adding -> s.playlistLanguageAdding.replace("%1", page.done.toString()).replace("%2", page.total.toString())
                else -> page.message
            }
            if (note.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    if (page.reading || page.adding) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(note, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f, fill = false))
                    if (!page.adding) {
                        TextButton(onClick = onDismissLanguages) { Text("✕", fontSize = 12.sp) }
                    }
                }
            }
            if (!page.reading && !page.adding && page.available.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    page.available.forEach { track ->
                        ChoiceChip(
                            label = "+ " + track.langName.ifBlank { track.langCode },
                            selected = false,
                            onClick = { onAddLanguage(track.langCode) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PageActions(
    group: PlaylistGroup,
    onCheck: () -> Unit,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onDelete: () -> Unit,
    onHide: () -> Unit,
    onOpenFolder: (() -> Unit)?,
    onPlay: (() -> Unit)?,
) {
    val s = LocalizationManager.Strings
    val shape = RoundedCornerShape(10.dp)
    if (onPlay != null) PlayGroupButton(onPlay)
    if (onOpenFolder != null) {
        OutlinedButton(onClick = onOpenFolder, shape = shape) { Text("📂 " + s.rowMenuOpenFolder, fontSize = 12.sp) }
    }
    if (group.record.url.isNotBlank()) {
        OutlinedButton(onClick = onCheck, shape = shape) { Text("⟳ " + (if (group.isPage) s.grabCheckNew else s.playlistCheckNew), fontSize = 12.sp) }
    }
    PauseResumeAllButtons(group.canPause, group.canResume, onPauseAll, onResumeAll)
    OutlinedButton(onClick = onHide, shape = shape) { Text("🙈 " + s.hiddenHide, fontSize = 12.sp) }
    OutlinedButton(
        onClick = onDelete,
        shape = shape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
    ) { Text("🗑 " + (if (group.isPage) s.folderDeleteAll else s.playlistDeleteAll), fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
}


/**
 * The top of an open folder ("group by folder"): back, its name, the full path and count,
 * open the folder, pause / resume all, delete all. No Check, no subtitles: that is for playlists.
 */
@Composable
fun FolderPageHeader(
    group: com.michelle.raouf.fetch_jet.presentation.dashboard.FolderGroup,
    canOpenFolder: Boolean,
    onBack: () -> Unit,
    onOpenFolder: () -> Unit,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onDelete: () -> Unit,
    onHide: () -> Unit,
    onPlay: (() -> Unit)? = null,
) {
    val s = LocalizationManager.Strings
    val actions: @Composable () -> Unit = {
        val shape = RoundedCornerShape(10.dp)
        if (onPlay != null) PlayGroupButton(onPlay)
        if (canOpenFolder) {
            OutlinedButton(onClick = onOpenFolder, shape = shape) { Text("📂 " + s.rowMenuOpenFolder, fontSize = 12.sp) }
        }
        PauseResumeAllButtons(group.canPause, group.canResume, onPauseAll, onResumeAll)
        OutlinedButton(onClick = onHide, shape = shape) { Text("🙈 " + s.hiddenHide, fontSize = 12.sp) }
        OutlinedButton(
            onClick = onDelete,
            shape = shape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
        ) { Text("🗑 " + s.folderDeleteAll, fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
    }
    GroupPageHeader("📁 " + group.label, group.summaryText, group.path, onBack, actions)
}

@Composable
internal fun GroupHeaderRow(
    title: String,
    summary: String,
    onBack: () -> Unit,
    actions: @Composable () -> Unit
) {
    val s = LocalizationManager.Strings
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth >= 720.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onBack) { Text("◀ " + s.playlistBack, fontSize = 13.sp) }
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(summary, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                actions()
            }
        } else {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onBack) { Text("◀", fontSize = 14.sp) }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(summary, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) { actions() }
            }
        }
    }
}

@Composable
internal fun GroupPageHeader(
    title: String,
    summary: String,
    path: String?,
    onBack: () -> Unit,
    actions: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        GroupHeaderRow(title, summary, onBack, actions)
        if (path != null) {
            Text(
                path,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
            )
        }
    }
}

@Composable
internal fun PlayGroupButton(onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, shape = RoundedCornerShape(10.dp)) {
        Text("▶ " + LocalizationManager.Strings.playerPlayAll, fontSize = 12.sp)
    }
}

@Composable
internal fun PauseResumeAllButtons(canPause: Boolean, canResume: Boolean, onPauseAll: () -> Unit, onResumeAll: () -> Unit) {
    val s = LocalizationManager.Strings
    val shape = RoundedCornerShape(10.dp)
    OutlinedButton(onClick = onPauseAll, enabled = canPause, shape = shape) { Text("⏸ " + s.homePauseAll, fontSize = 12.sp) }
    OutlinedButton(onClick = onResumeAll, enabled = canResume, shape = shape) { Text("⬇ " + s.homeResumeAll, fontSize = 12.sp) }
}
