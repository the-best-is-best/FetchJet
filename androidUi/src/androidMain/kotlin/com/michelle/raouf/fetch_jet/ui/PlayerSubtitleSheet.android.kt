package com.michelle.raouf.fetch_jet.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.feature.media_player.PlayerItem
import com.michelle.raouf.fetch_jet.feature.media_player.PlayerSubtitles
import com.michelle.raouf.fetch_jet.feature.media_player.SubtitleChoice
import com.michelle.raouf.fetch_jet.feature.media_player.SubtitleFile
import com.tbib.kmp_localization.LocalizationManager
import java.io.File
import java.util.Locale

internal data class SubtitleTrackEntry(val id: String, val label: String)

internal object AndroidSubtitles {
    private const val SIDE_ID = "fjsub:"

    fun isContent(path: String): Boolean = path.startsWith("content://")

    fun uriOf(path: String): Uri = if (isContent(path) || path.startsWith("file://")) Uri.parse(path) else Uri.fromFile(File(path))

    fun mimeFor(name: String): String? = when (name.substringAfterLast('.', "").lowercase()) {
        "srt" -> MimeTypes.APPLICATION_SUBRIP
        "vtt" -> MimeTypes.TEXT_VTT
        "ass", "ssa" -> MimeTypes.TEXT_SSA
        else -> null
    }

    private fun nameOf(file: SubtitleFile): String = if (isContent(file.path)) file.label else file.path.substringAfterLast('/')

    fun displayName(file: SubtitleFile): String = nameOf(file)

    fun configs(files: List<SubtitleFile>): List<MediaItem.SubtitleConfiguration> = files.mapNotNull { f ->
        val mime = mimeFor(nameOf(f)) ?: return@mapNotNull null
        MediaItem.SubtitleConfiguration.Builder(uriOf(f.path))
            .setMimeType(mime)
            .setId(SIDE_ID + f.path)
            .setLabel(nameOf(f))
            .build()
    }

    fun mediaItem(path: String, files: List<SubtitleFile>): MediaItem =
        MediaItem.Builder().setUri(Uri.fromFile(File(path))).setSubtitleConfigurations(configs(files)).build()

    fun withFiles(item: MediaItem, files: List<SubtitleFile>): MediaItem =
        item.buildUpon().setSubtitleConfigurations(configs(files)).build()

    fun hasFile(item: MediaItem?, path: String): Boolean =
        item?.localConfiguration?.subtitleConfigurations?.any { it.id == SIDE_ID + path } == true

    fun sideFilesOf(item: PlayerItem): List<SubtitleFile> =
        if (item.hidden || item.path.isBlank()) emptyList()
        else PlayerSubtitles.sideFiles(item.path, PlayerSubtitles.appLanguage())

    fun displayNameOf(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    fun canRead(context: Context, uri: Uri): Boolean = runCatching {
        context.contentResolver.openInputStream(uri)?.use { true } ?: false
    }.getOrDefault(false)

    fun keepAccess(context: Context, uri: Uri) {
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    fun savedContentFile(context: Context, item: PlayerItem): SubtitleFile? {
        if (item.hidden) return null
        val saved = PlayerSubtitles.savedChoice(item.taskId) as? SubtitleChoice.File ?: return null
        if (!isContent(saved.path)) return null
        val uri = Uri.parse(saved.path)
        if (!canRead(context, uri)) return null
        return SubtitleFile(saved.path, displayNameOf(context, uri) ?: saved.path.substringAfterLast('/'))
    }

    fun initialChoice(item: PlayerItem, files: List<SubtitleFile>): SubtitleChoice? {
        val lang = PlayerSubtitles.appLanguage()
        if (!item.hidden) {
            val saved = PlayerSubtitles.savedChoice(item.taskId)
            if (saved is SubtitleChoice.File && isContent(saved.path)) {
                return if (files.any { it.path == saved.path }) saved
                else PlayerSubtitles.initialChoice("", files.filter { !isContent(it.path) }, lang, remember = false)
            }
        }
        return PlayerSubtitles.initialChoice(item.taskId, files, lang, remember = !item.hidden)
    }

    private fun languageName(code: String?): String? {
        if (code.isNullOrBlank() || code == "und") return null
        val app = Locale.forLanguageTag(PlayerSubtitles.appLanguage())
        val name = Locale.forLanguageTag(code).getDisplayName(app)
        return name.takeIf { it.isNotBlank() && it != code }?.replaceFirstChar { it.titlecase(app) } ?: code
    }

    fun embeddedTracks(tracks: Tracks): List<SubtitleTrackEntry> {
        val out = mutableListOf<SubtitleTrackEntry>()
        tracks.groups.forEachIndexed { gi, group ->
            if (group.type != C.TRACK_TYPE_TEXT) return@forEachIndexed
            for (ti in 0 until group.length) {
                val format = group.getTrackFormat(ti)
                if (format.id?.contains(SIDE_ID) == true) continue
                val label = format.label?.takeIf { it.isNotBlank() } ?: languageName(format.language)
                    ?: "${LocalizationManager.Strings.playerSubtitlesTrack} ${out.size + 1}"
                out += SubtitleTrackEntry("$gi:$ti", label)
            }
        }
        return out
    }

    fun selected(tracks: Tracks): SubtitleChoice {
        tracks.groups.forEachIndexed { gi, group ->
            if (group.type != C.TRACK_TYPE_TEXT) return@forEachIndexed
            for (ti in 0 until group.length) {
                if (!group.isTrackSelected(ti)) continue
                val id = group.getTrackFormat(ti).id
                return if (id != null && id.contains(SIDE_ID)) SubtitleChoice.File(id.substringAfter(SIDE_ID))
                else SubtitleChoice.Track("$gi:$ti")
            }
        }
        return SubtitleChoice.Off
    }

    fun reset(player: Player, off: Boolean) {
        val p = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, off)
            .build()
        if (p != player.trackSelectionParameters) player.trackSelectionParameters = p
    }

    fun apply(player: Player, choice: SubtitleChoice): Boolean {
        if (choice == SubtitleChoice.Off) {
            reset(player, true)
            return true
        }
        val tracks = player.currentTracks
        tracks.groups.forEachIndexed { gi, group ->
            if (group.type != C.TRACK_TYPE_TEXT) return@forEachIndexed
            for (ti in 0 until group.length) {
                val id = group.getTrackFormat(ti).id
                val hit = when (choice) {
                    is SubtitleChoice.File -> id != null && id.contains(SIDE_ID) && id.substringAfter(SIDE_ID) == choice.path
                    is SubtitleChoice.Track -> choice.id == "$gi:$ti" && id?.contains(SIDE_ID) != true
                    else -> false
                }
                if (!hit) continue
                val p = player.trackSelectionParameters.buildUpon()
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, ti))
                    .build()
                if (p != player.trackSelectionParameters) player.trackSelectionParameters = p
                return true
            }
        }
        return false
    }

    fun style(view: SubtitleView?) {
        view ?: return
        view.setApplyEmbeddedFontSizes(false)
        view.setStyle(
            CaptionStyleCompat(
                AndroidColor.WHITE,
                0x80000000.toInt(),
                AndroidColor.TRANSPARENT,
                CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                AndroidColor.BLACK,
                null
            )
        )
        view.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * 1.3f)
    }
}

@Composable
internal fun PlayerSubtitleSheet(
    tracks: List<SubtitleTrackEntry>,
    files: List<SubtitleFile>,
    selected: SubtitleChoice,
    onPick: (SubtitleChoice) -> Unit,
    onOpenFile: () -> Unit,
    onDismiss: () -> Unit
) {
    val rows = buildList<Pair<String, (() -> Unit)?>> {
        add(LocalizationManager.Strings.playerSubtitlesOff to { onPick(SubtitleChoice.Off) })
        if (tracks.isNotEmpty()) {
            add(LocalizationManager.Strings.playerSubtitlesInVideo to null)
            tracks.forEach { t -> add(t.label to { onPick(SubtitleChoice.Track(t.id)) }) }
        }
        if (files.isNotEmpty()) {
            add(LocalizationManager.Strings.playerSubtitlesFiles to null)
            files.forEach { f -> add(AndroidSubtitles.displayName(f) to { onPick(SubtitleChoice.File(f.path)) }) }
        }
    }
    val checked = buildList {
        add(selected == SubtitleChoice.Off)
        if (tracks.isNotEmpty()) {
            add(false)
            tracks.forEach { add(selected == SubtitleChoice.Track(it.id)) }
        }
        if (files.isNotEmpty()) {
            add(false)
            files.forEach { add(selected == SubtitleChoice.File(it.path)) }
        }
    }
    Box(modifier = Modifier.fillMaxSize().background(Color(0x99000000)).clickable(onClick = onDismiss)) {
        LazyColumn(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = 560.dp)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(MaterialTheme.colorScheme.surface).navigationBarsPadding()
                .pointerInput(Unit) { detectTapGestures { } }
        ) {
            item {
                Text(
                    LocalizationManager.Strings.playerSubtitles,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp)
                )
            }
            items(rows.indices.toList()) { i ->
                val (label, action) = rows[i]
                if (action == null) {
                    Text(
                        label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
                    )
                } else {
                    val active = checked[i]
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable(onClick = action)
                            .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (active) "✓" else "",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 15.sp,
                            modifier = Modifier.width(28.dp)
                        )
                        Text(
                            label,
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenFile)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("+", color = MaterialTheme.colorScheme.primary, fontSize = 17.sp, modifier = Modifier.width(28.dp))
                    Text(LocalizationManager.Strings.playerSubtitlesOpenFile, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}
