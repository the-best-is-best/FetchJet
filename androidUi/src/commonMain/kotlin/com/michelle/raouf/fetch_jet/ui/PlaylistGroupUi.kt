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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
    compact: Boolean = false,
) {
    val s = LocalizationManager.Strings
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        GroupHeaderRow(group.title, group.summaryText, onBack, oneRow = compact) {
            GroupActionButtons(
                GroupActions(
                    onPlay = onPlay,
                    onOpenFolder = onOpenFolder,
                    checkLabel = if (group.record.url.isNotBlank()) (if (group.isPage) s.grabCheckNew else s.playlistCheckNew) else null,
                    onCheck = onCheck,
                    canPause = group.canPause,
                    canResume = group.canResume,
                    onPauseAll = onPauseAll,
                    onResumeAll = onResumeAll,
                    onHide = onHide,
                    deleteLabel = if (group.isPage) s.folderDeleteAll else s.playlistDeleteAll,
                    onDelete = onDelete
                ),
                compact
            )
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
    compact: Boolean = false,
) {
    val s = LocalizationManager.Strings
    val actions = GroupActions(
        onPlay = onPlay,
        onOpenFolder = if (canOpenFolder) onOpenFolder else null,
        canPause = group.canPause,
        canResume = group.canResume,
        onPauseAll = onPauseAll,
        onResumeAll = onResumeAll,
        onHide = onHide,
        deleteLabel = s.folderDeleteAll,
        onDelete = onDelete
    )
    GroupPageHeader("📁 " + group.label, group.summaryText, group.path, onBack, compact) {
        GroupActionButtons(actions, compact)
    }
}

/**
 * The buttons of an open group (playlist, folder, or the same inside Hidden). Each page fills
 * only the actions it has; null = not shown. On a short screen the rare ones go behind ⋮.
 */
class GroupActions(
    val onPlay: (() -> Unit)? = null,
    val onOpenFolder: (() -> Unit)? = null,
    val checkLabel: String? = null,
    val onCheck: () -> Unit = {},
    val canPause: Boolean = false,
    val canResume: Boolean = false,
    val onPauseAll: () -> Unit = {},
    val onResumeAll: () -> Unit = {},
    val onHide: (() -> Unit)? = null,
    val onUnhide: (() -> Unit)? = null,
    val deleteLabel: String? = null,
    val onDelete: () -> Unit = {},
)

@Composable
internal fun GroupActionButtons(actions: GroupActions, compact: Boolean) {
    val s = LocalizationManager.Strings
    val shape = RoundedCornerShape(10.dp)
    actions.onPlay?.let { PlayGroupButton(it) }
    actions.onOpenFolder?.let {
        OutlinedButton(onClick = it, shape = shape) { Text("📂 " + s.rowMenuOpenFolder, fontSize = 12.sp) }
    }
    actions.checkLabel?.let { label ->
        OutlinedButton(onClick = actions.onCheck, shape = shape) { Text("⟳ " + label, fontSize = 12.sp) }
    }
    if (compact) {
        GroupMoreMenu(actions)
        return
    }
    PauseResumeAllButtons(actions.canPause, actions.canResume, actions.onPauseAll, actions.onResumeAll)
    actions.onHide?.let {
        OutlinedButton(onClick = it, shape = shape) { Text("🙈 " + s.hiddenHide, fontSize = 12.sp) }
    }
    actions.onUnhide?.let {
        OutlinedButton(onClick = it, shape = shape) { Text(s.hiddenUnhide, fontSize = 12.sp) }
    }
    actions.deleteLabel?.let { label ->
        OutlinedButton(
            onClick = actions.onDelete,
            shape = shape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
        ) { Text("🗑 " + label, fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun GroupMoreMenu(actions: GroupActions) {
    val s = LocalizationManager.Strings
    var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.foundation.layout.Box {
        OutlinedButton(onClick = { open = true }, shape = RoundedCornerShape(10.dp)) { Text("⋮", fontSize = 14.sp) }
        androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            androidx.compose.material3.DropdownMenuItem(
                text = { Text("⏸ " + s.homePauseAll) },
                enabled = actions.canPause,
                onClick = { open = false; actions.onPauseAll() }
            )
            androidx.compose.material3.DropdownMenuItem(
                text = { Text("⬇ " + s.homeResumeAll) },
                enabled = actions.canResume,
                onClick = { open = false; actions.onResumeAll() }
            )
            actions.onHide?.let { hide ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("🙈 " + s.hiddenHide) },
                    onClick = { open = false; hide() }
                )
            }
            actions.onUnhide?.let { unhide ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(s.hiddenUnhide) },
                    onClick = { open = false; unhide() }
                )
            }
            actions.deleteLabel?.let { label ->
                androidx.compose.material3.HorizontalDivider()
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("🗑 " + label, color = MaterialTheme.colorScheme.error) },
                    onClick = { open = false; actions.onDelete() }
                )
            }
        }
    }
}

@Composable
internal fun isShortScreen(): Boolean {
    val window = androidx.compose.ui.platform.LocalWindowInfo.current
    val height = with(androidx.compose.ui.platform.LocalDensity.current) { window.containerSize.height.toDp() }
    return height < 500.dp
}

@Composable
internal fun GroupHeaderRow(
    title: String,
    summary: String,
    onBack: () -> Unit,
    oneRow: Boolean = false,
    actions: @Composable () -> Unit
) {
    val s = LocalizationManager.Strings
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (oneRow || maxWidth >= 720.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onBack) { Text("◀ " + s.playlistBack, fontSize = 13.sp) }
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(summary, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    compact: Boolean = false,
    actions: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        val line = if (compact && path != null) "$summary · $path" else summary
        GroupHeaderRow(title, line, onBack, oneRow = compact, actions = actions)
        if (path != null && !compact) {
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
