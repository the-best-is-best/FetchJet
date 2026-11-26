package com.michelle.raouf.fetch_jet.ui

import android.net.Uri
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.view.Window
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import com.michelle.raouf.fetch_jet.core.settings.BehaviorSettings
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.michelle.raouf.fetch_jet.feature.media_player.MediaPlayback
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.feature.media_player.PlaybackQueue
import com.michelle.raouf.fetch_jet.feature.media_player.PlayerItem
import com.michelle.raouf.fetch_jet.feature.media_player.PlayerProgress
import com.michelle.raouf.fetch_jet.feature.media_player.PlayerSubtitles
import com.michelle.raouf.fetch_jet.feature.media_player.SubtitleChoice
import com.michelle.raouf.fetch_jet.feature.media_player.SubtitleFile
import com.tbib.kmp_localization.LocalizationManager
import com.michelle.raouf.fetch_jet.feature.media_player.HiddenPlayback
import com.michelle.raouf.fetch_jet.presentation.dashboard.hiddenOpeningText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

internal enum class PlayerGlyph { CLOSE, PLAY, PAUSE, PREVIOUS, NEXT, LIST, SUBTITLES, VOLUME, BRIGHTNESS }

internal fun DrawScope.drawPlayerGlyph(kind: PlayerGlyph, color: Color, stroke: Float) {
    val w = size.width
    val h = size.height
    fun triangle(left: Float, right: Float): Path = Path().apply {
        moveTo(w * left, h * 0.2f)
        lineTo(w * right, h * 0.5f)
        lineTo(w * left, h * 0.8f)
        close()
    }
    when (kind) {
        PlayerGlyph.CLOSE -> {
            drawLine(color, Offset(w * 0.22f, h * 0.22f), Offset(w * 0.78f, h * 0.78f), stroke, StrokeCap.Round)
            drawLine(color, Offset(w * 0.78f, h * 0.22f), Offset(w * 0.22f, h * 0.78f), stroke, StrokeCap.Round)
        }

        PlayerGlyph.PLAY -> drawPath(triangle(0.3f, 0.8f), color, style = Fill)
        PlayerGlyph.PAUSE -> {
            drawRoundRect(color, Offset(w * 0.25f, h * 0.2f), androidx.compose.ui.geometry.Size(w * 0.17f, h * 0.6f),
                androidx.compose.ui.geometry.CornerRadius(stroke))
            drawRoundRect(color, Offset(w * 0.58f, h * 0.2f), androidx.compose.ui.geometry.Size(w * 0.17f, h * 0.6f),
                androidx.compose.ui.geometry.CornerRadius(stroke))
        }

        PlayerGlyph.PREVIOUS -> {
            val p = Path().apply {
                moveTo(w * 0.78f, h * 0.2f); lineTo(w * 0.36f, h * 0.5f); lineTo(w * 0.78f, h * 0.8f); close()
            }
            drawPath(p, color, style = Fill)
            drawLine(color, Offset(w * 0.24f, h * 0.2f), Offset(w * 0.24f, h * 0.8f), stroke * 1.2f, StrokeCap.Round)
        }

        PlayerGlyph.NEXT -> {
            val p = Path().apply {
                moveTo(w * 0.22f, h * 0.2f); lineTo(w * 0.64f, h * 0.5f); lineTo(w * 0.22f, h * 0.8f); close()
            }
            drawPath(p, color, style = Fill)
            drawLine(color, Offset(w * 0.76f, h * 0.2f), Offset(w * 0.76f, h * 0.8f), stroke * 1.2f, StrokeCap.Round)
        }

        PlayerGlyph.LIST -> {
            for (i in 0..2) {
                val y = h * (0.28f + 0.22f * i)
                drawCircle(color, stroke * 0.6f, Offset(w * 0.2f, y))
                drawLine(color, Offset(w * 0.34f, y), Offset(w * 0.82f, y), stroke, StrokeCap.Round)
            }
        }

        PlayerGlyph.SUBTITLES -> {
            drawRoundRect(
                color, Offset(w * 0.1f, h * 0.2f), androidx.compose.ui.geometry.Size(w * 0.8f, h * 0.6f),
                androidx.compose.ui.geometry.CornerRadius(stroke * 1.6f), style = Stroke(stroke)
            )
            drawLine(color, Offset(w * 0.26f, h * 0.44f), Offset(w * 0.46f, h * 0.44f), stroke, StrokeCap.Round)
            drawLine(color, Offset(w * 0.56f, h * 0.44f), Offset(w * 0.74f, h * 0.44f), stroke, StrokeCap.Round)
            drawLine(color, Offset(w * 0.26f, h * 0.6f), Offset(w * 0.62f, h * 0.6f), stroke, StrokeCap.Round)
        }

        PlayerGlyph.VOLUME -> {
            val p = Path().apply {
                moveTo(w * 0.1f, h * 0.38f); lineTo(w * 0.26f, h * 0.38f); lineTo(w * 0.48f, h * 0.18f)
                lineTo(w * 0.48f, h * 0.82f); lineTo(w * 0.26f, h * 0.62f); lineTo(w * 0.1f, h * 0.62f); close()
            }
            drawPath(p, color, style = Fill)
            listOf(0.16f, 0.3f).forEach { r ->
                drawArc(
                    color, -50f, 100f, false,
                    Offset(w * 0.5f - w * r, h * 0.5f - w * r),
                    androidx.compose.ui.geometry.Size(w * r * 2f, w * r * 2f),
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
        }

        PlayerGlyph.BRIGHTNESS -> {
            val c = Offset(w * 0.5f, h * 0.5f)
            drawCircle(color, w * 0.2f, c)
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45.0)
                val dx = kotlin.math.cos(a).toFloat()
                val dy = kotlin.math.sin(a).toFloat()
                drawLine(color, Offset(c.x + dx * w * 0.31f, c.y + dy * w * 0.31f),
                    Offset(c.x + dx * w * 0.44f, c.y + dy * w * 0.44f), stroke, StrokeCap.Round)
            }
        }
    }
}

@Composable
internal fun PlayerGlyphButton(
    kind: PlayerGlyph,
    sizeDp: Int,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val tint = Color.White.copy(alpha = if (enabled) 1f else 0.35f)
    Box(
        modifier = Modifier.size((sizeDp + 20).dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(sizeDp.dp)) { drawPlayerGlyph(kind, tint, 2.4.dp.toPx()) }
    }
}

@Composable
private fun MiniSwitch(on: Boolean, accent: Color) {
    Canvas(modifier = Modifier.size(width = 28.dp, height = 16.dp)) {
        val r = size.height / 2f
        drawRoundRect(
            if (on) accent else Color(0x66FFFFFF),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r)
        )
        val rtl = layoutDirection == LayoutDirection.Rtl
        val atEnd = on != rtl
        drawCircle(Color.White, r - 2.dp.toPx(), Offset(if (atEnd) size.width - r else r, r))
    }
}

@Composable
internal actual fun MediaPlayerScreen(queue: PlaybackQueue, onClose: () -> Unit) {
    androidx.compose.runtime.key(queue) { MediaPlayerContent(queue, onClose) }
}

@Composable
private fun MediaPlayerContent(queue: PlaybackQueue, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hiddenQueue = remember(queue) { queue.items.any { it.hidden } }
    val extraSubs = remember {
        mutableStateMapOf<String, List<SubtitleFile>>().apply {
            if (!hiddenQueue) queue.items.forEach { item ->
                AndroidSubtitles.savedContentFile(context, item)?.let { put(item.taskId, listOf(it)) }
            }
        }
    }
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setSeekParameters(SeekParameters.CLOSEST_SYNC)
            if (!hiddenQueue) {
                setMediaItems(queue.items.map {
                    AndroidSubtitles.mediaItem(it.path, AndroidSubtitles.sideFilesOf(it) + extraSubs[it.taskId].orEmpty())
                }, queue.index, 0L)
                prepare()
                playWhenReady = true
            }
        }
    }
    var index by remember { mutableIntStateOf(queue.index) }
    var playing by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var speed by remember { mutableFloatStateOf(1f) }
    var controls by remember { mutableStateOf(true) }
    var showQueue by remember { mutableStateOf(false) }
    var speedMenu by remember { mutableStateOf(false) }
    var hold by remember { mutableStateOf(0) }
    var wantPlay by remember { mutableStateOf(true) }
    var seeking by remember { mutableStateOf(false) }
    var swipe by remember { mutableStateOf<PlayerGlyph?>(null) }
    var swiping by remember { mutableStateOf(false) }
    var swipeLevel by remember { mutableFloatStateOf(0f) }
    val autoplay by BehaviorSettings.playerAutoplay.collectAsState()
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val dialogWindow = remember { arrayOfNulls<Window>(1) }
    val originalBrightness = remember { arrayOfNulls<Float>(1) }
    var failed by remember { mutableStateOf<String?>(null) }
    var touch by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }
    var opening by remember { mutableStateOf<Float?>(null) }
    val openJob = remember { arrayOfNulls<Job>(1) }
    val prepared = remember { mutableSetOf<String>() }
    val current = queue.items[index.coerceIn(0, queue.items.lastIndex)]
    var subMenu by remember { mutableStateOf(false) }
    var subNotice by remember { mutableStateOf<String?>(null) }
    var tracks by remember { mutableStateOf(Tracks.EMPTY) }
    val pendingSub = remember { arrayOfNulls<SubtitleChoice>(1) }
    val sideSubs = remember(current.taskId) { AndroidSubtitles.sideFilesOf(current) }
    val subFiles = sideSubs + extraSubs[current.taskId].orEmpty().filter { e -> sideSubs.none { it.path == e.path } }

    fun filesFor(item: PlayerItem): List<SubtitleFile> {
        val side = if (item.taskId == current.taskId) sideSubs else AndroidSubtitles.sideFilesOf(item)
        return side + extraSubs[item.taskId].orEmpty().filter { e -> side.none { it.path == e.path } }
    }

    fun tryPendingSub() {
        val choice = pendingSub[0] ?: return
        if (player.currentTracks.isEmpty) return
        pendingSub[0] = null
        AndroidSubtitles.apply(player, choice)
    }

    fun startSubtitles(item: PlayerItem, applyNow: Boolean) {
        val choice = AndroidSubtitles.initialChoice(item, filesFor(item))
        AndroidSubtitles.reset(player, choice == SubtitleChoice.Off)
        pendingSub[0] = choice?.takeIf { it != SubtitleChoice.Off }
        if (applyNow) tryPendingSub()
    }

    fun pickSubtitle(choice: SubtitleChoice) {
        val item = current
        if (choice is SubtitleChoice.File && !AndroidSubtitles.hasFile(player.currentMediaItem, choice.path)) {
            val i = player.currentMediaItemIndex
            val now = player.currentMediaItem ?: return
            val pos = player.currentPosition
            val list = (0 until player.mediaItemCount).map {
                if (it == i) AndroidSubtitles.withFiles(now, filesFor(item)) else player.getMediaItemAt(it)
            }
            player.setMediaItems(list, i, pos)
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            pendingSub[0] = choice
        } else if (!AndroidSubtitles.apply(player, choice)) {
            pendingSub[0] = choice
        }
        if (!item.hidden) PlayerSubtitles.remember(item.taskId, choice)
    }

    val resumeAfterPick = remember { booleanArrayOf(false) }
    val subPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val resume = resumeAfterPick[0]
        resumeAfterPick[0] = false
        if (uri != null) {
            val name = AndroidSubtitles.displayNameOf(context, uri) ?: uri.lastPathSegment.orEmpty()
            if (AndroidSubtitles.mimeFor(name) == null || !AndroidSubtitles.canRead(context, uri)) {
                subNotice = LocalizationManager.Strings.playerSubtitlesFailed
            } else {
                if (!current.hidden) AndroidSubtitles.keepAccess(context, uri)
                val file = SubtitleFile(uri.toString(), name)
                extraSubs[current.taskId] = extraSubs[current.taskId].orEmpty().filter { it.path != file.path } + file
                pickSubtitle(SubtitleChoice.File(file.path))
            }
        }
        if (resume) player.play()
    }

    fun openAt(target: Int, play: Boolean) {
        val i = target.coerceIn(0, queue.items.lastIndex)
        val item = queue.items[i]
        openJob[0]?.cancel()
        player.stop()
        player.clearMediaItems()
        prepared.filter { it != item.taskId }.forEach {
            HiddenPlayback.release(it)
            prepared.remove(it)
        }
        index = i
        failed = null
        finished = false
        controls = play
        wantPlay = play
        position = 0L
        duration = 0L
        PlayerProgress.remember(queue.groupKey, item.taskId)
        openJob[0] = scope.launch {
            val path = if (item.hidden && HiddenPlayback.needsPrepare(item)) {
                opening = 0f
                try {
                    withContext(Dispatchers.IO) { HiddenPlayback.prepare(item.taskId) { opening = it } }
                        ?.also { prepared.add(item.taskId) }
                } catch (e: CancellationException) {
                    HiddenPlayback.release(item.taskId)
                    throw e
                } finally {
                    opening = null
                }
            } else item.path
            if (path.isNullOrBlank()) {
                failed = LocalizationManager.Strings.fileMissingTitle
                return@launch
            }
            startSubtitles(item, false)
            player.setMediaItem(AndroidSubtitles.mediaItem(path, filesFor(item)))
            player.prepare()
            player.playWhenReady = play
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                wantPlay = playWhenReady
                if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM && player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                    controls = false
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (hiddenQueue) return
                index = player.currentMediaItemIndex
                failed = null
                finished = false
                if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                    queue.items.getOrNull(index)?.let { startSubtitles(it, false) }
                }
                player.pauseAtEndOfMediaItems = !BehaviorSettings.playerAutoplay.value && player.hasNextMediaItem()
                if ((reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) &&
                    !BehaviorSettings.playerAutoplay.value
                ) {
                    player.pause()
                    controls = false
                }
                queue.items.getOrNull(index)?.let { PlayerProgress.remember(queue.groupKey, it.taskId) }
            }

            override fun onTracksChanged(newTracks: Tracks) {
                tracks = newTracks
                tryPendingSub()
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) duration = player.duration.coerceAtLeast(0L)
                if (state == Player.STATE_ENDED && hiddenQueue && index < queue.items.lastIndex) {
                    openAt(index + 1, BehaviorSettings.playerAutoplay.value)
                } else if (state == Player.STATE_ENDED) {
                    controls = true
                    finished = true
                    PlayerProgress.clear(queue.groupKey)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                failed = error.errorCodeName
                if (hiddenQueue) {
                    if (index < queue.items.lastIndex) openAt(index + 1, true)
                } else if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                    player.prepare()
                    player.play()
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
            if (hiddenQueue) {
                val job = openJob[0]
                job?.cancel()
                HiddenPlayback.releaseAll()
                if (job != null && job.isActive) {
                    CoroutineScope(Dispatchers.IO).launch {
                        job.join()
                        HiddenPlayback.releaseAll()
                    }
                }
            }
        }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.pause()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        if (hiddenQueue) openAt(queue.index, true)
        else {
            PlayerProgress.remember(queue.groupKey, queue.items[queue.index].taskId)
            startSubtitles(queue.items[queue.index], true)
        }
    }

    LaunchedEffect(subNotice) {
        if (subNotice != null) {
            delay(3000)
            subNotice = null
        }
    }

    LaunchedEffect(player) {
        while (isActive) {
            position = player.currentPosition.coerceAtLeast(0L)
            if (player.duration > 0) duration = player.duration
            delay(250)
        }
    }

    LaunchedEffect(controls, wantPlay, finished, touch, showQueue, speedMenu, hold, subMenu, seeking, current.isVideo) {
        if (controls && wantPlay && !finished && current.isVideo && !showQueue && !speedMenu && !subMenu && hold == 0 && !seeking) {
            delay(3000)
            controls = false
        }
    }

    LaunchedEffect(autoplay) {
        if (!hiddenQueue) player.pauseAtEndOfMediaItems = !autoplay && player.hasNextMediaItem()
    }

    LaunchedEffect(swiping) {
        if (!swiping) {
            delay(700)
            swipe = null
        }
    }

    fun volumeLevel(): Float {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
    }

    fun setVolume(level: Float): Float {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val step = (level * max).roundToInt().coerceIn(0, max)
        if (step != audio.getStreamVolume(AudioManager.STREAM_MUSIC)) {
            runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, step, 0) }
        }
        return step.toFloat() / max
    }

    fun brightnessLevel(): Float {
        val own = dialogWindow[0]?.attributes?.screenBrightness ?: -1f
        if (own >= 0f) return own
        return runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
        }.getOrDefault(0.5f).coerceIn(0f, 1f)
    }

    fun setBrightness(level: Float): Float {
        val window = dialogWindow[0] ?: return level
        val attrs = window.attributes
        if (originalBrightness[0] == null) originalBrightness[0] = attrs.screenBrightness
        attrs.screenBrightness = level.coerceIn(0.01f, 1f)
        window.attributes = attrs
        return level
    }

    fun togglePlay() {
        if (player.playbackState == Player.STATE_ENDED) {
            finished = false
            player.seekTo(0L)
            player.play()
        } else if (player.playWhenReady) player.pause() else player.play()
        touch++
    }

    BackHandler {
        if (subMenu) subMenu = false else if (showQueue) showQueue = false else onClose()
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val view = LocalView.current
        DisposableEffect(view) {
            val window = (view.parent as? DialogWindowProvider)?.window
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (hiddenQueue) window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            dialogWindow[0] = window
            onDispose {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                originalBrightness[0]?.let { original ->
                    window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = original } }
                }
                dialogWindow[0] = null
            }
        }
        val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        DisposableEffect(view, landscape) {
            val window = (view.parent as? DialogWindowProvider)?.window
            val bars = window?.let { WindowCompat.getInsetsController(it, view) }
            if (landscape) {
                bars?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                bars?.hide(WindowInsetsCompat.Type.systemBars())
            }
            onDispose { if (landscape) bars?.show(WindowInsetsCompat.Type.systemBars()) }
        }
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            if (current.isVideo) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            this.player = player
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            AndroidSubtitles.style(subtitleView)
                        }
                    },
                    update = { it.subtitleView?.setBottomPaddingFraction(if (controls) 0.16f else 0.06f) }
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier.size(140.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        val c = MaterialTheme.colorScheme.primary
                        Canvas(modifier = Modifier.size(64.dp)) {
                            val w = size.width
                            val h = size.height
                            drawCircle(c, w * 0.16f, Offset(w * 0.28f, h * 0.76f))
                            drawLine(c, Offset(w * 0.4f, h * 0.76f), Offset(w * 0.4f, h * 0.18f), w * 0.1f, StrokeCap.Round)
                            drawLine(c, Offset(w * 0.4f, h * 0.18f), Offset(w * 0.82f, h * 0.3f), w * 0.1f, StrokeCap.Round)
                        }
                    }
                    Text(
                        current.title,
                        color = Color.White,
                        fontSize = 18.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 24.dp)
                    )
                }
            }

            Box(
                modifier = Modifier.fillMaxSize().pointerInput(player) {
                    var held = false
                    var rewindJob: Job? = null
                    detectTapGestures(
                        onPress = { offset ->
                            held = false
                            val left = offset.x < size.width / 2f
                            var wasPlaying = false
                            val starter = scope.launch {
                                delay(350)
                                held = true
                                if (left) {
                                    hold = -1
                                    wasPlaying = player.isPlaying
                                    player.pause()
                                    rewindJob = launch {
                                        while (isActive) {
                                            player.seekTo((player.currentPosition - 200L).coerceAtLeast(0L))
                                            delay(100)
                                        }
                                    }
                                } else {
                                    hold = 1
                                    player.playbackParameters = PlaybackParameters(MediaPlayback.HOLD_SPEED)
                                }
                            }
                            tryAwaitRelease()
                            starter.cancel()
                            rewindJob?.cancel()
                            if (hold == 1) player.playbackParameters = PlaybackParameters(speed)
                            if (hold == -1 && wasPlaying) player.play()
                            hold = 0
                        },
                        onTap = {
                            if (!held) {
                                controls = !controls
                                touch++
                            }
                        },
                        onDoubleTap = { offset ->
                            val delta = if (offset.x < size.width / 2f) -MediaPlayback.SKIP_MS else MediaPlayback.SKIP_MS
                            player.seekTo((player.currentPosition + delta).coerceIn(0L, player.duration.coerceAtLeast(0L)))
                        }
                    )
                }.pointerInput(player) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val slop = viewConfiguration.touchSlop * 1.5f
                        val right = down.position.x >= size.width / 2f
                        var mode = 0
                        var start = 0f
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.count { it.pressed } > 1 && mode == 0) mode = -1
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val dx = change.position.x - down.position.x
                                val dy = change.position.y - down.position.y
                                if (mode == 0) {
                                    if (abs(dy) > slop && abs(dy) > abs(dx) * 2f && hold == 0) {
                                        mode = 1
                                        start = if (right) volumeLevel() else brightnessLevel()
                                        swipe = if (right) PlayerGlyph.VOLUME else PlayerGlyph.BRIGHTNESS
                                        swipeLevel = start
                                        swiping = true
                                    } else if (abs(dx) > slop) mode = -1
                                }
                                if (mode == 1) {
                                    change.consume()
                                    val level = (start - dy / (size.height * 0.6f)).coerceIn(0f, 1f)
                                    swipeLevel = if (right) setVolume(level) else setBrightness(level)
                                }
                            }
                        } finally {
                            if (mode == 1) swiping = false
                        }
                    }
                }
            )

            swipe?.let { kind ->
                Column(
                    modifier = Modifier.align(Alignment.Center).width(128.dp).clip(RoundedCornerShape(16.dp))
                        .background(Color(0xB3000000)).padding(horizontal = 18.dp, vertical = 14.dp)
                        .semantics {
                            contentDescription = if (kind == PlayerGlyph.VOLUME) LocalizationManager.Strings.playerVolume
                            else LocalizationManager.Strings.playerBrightness
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Canvas(modifier = Modifier.size(30.dp)) { drawPlayerGlyph(kind, Color.White, 2.4.dp.toPx()) }
                    Canvas(modifier = Modifier.fillMaxWidth().height(4.dp)) {
                        val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2f)
                        drawRoundRect(Color(0x4DFFFFFF), cornerRadius = r)
                        drawRoundRect(
                            Color.White,
                            size = androidx.compose.ui.geometry.Size(size.width * swipeLevel, size.height),
                            cornerRadius = r
                        )
                    }
                    Text("${(swipeLevel * 100).roundToInt()}%", color = Color.White, fontSize = 13.sp)
                }
            }

            if (hold != 0) {
                Box(
                    modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 64.dp)
                        .clip(RoundedCornerShape(20.dp)).background(Color(0xAA000000))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(if (hold > 0) "▶▶  2x" else "◀◀  2x", color = Color.White, fontSize = 14.sp)
                }
            }

            opening?.let { fraction ->
                Column(
                    modifier = Modifier.align(Alignment.Center).width(240.dp).clip(RoundedCornerShape(12.dp))
                        .background(Color(0xCC000000)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(hiddenOpeningText(fraction), color = Color.White, fontSize = 14.sp)
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                }
            }

            failed?.let {
                Text(
                    it,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center).clip(RoundedCornerShape(8.dp))
                        .background(Color(0xCC000000)).padding(12.dp)
                )
            }

            val showPause = wantPlay && !finished
            if (swipe == null && (controls || (!showPause && opening == null))) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(28.dp)
                    ) {
                        if (controls) {
                            PlayerGlyphButton(PlayerGlyph.PREVIOUS, 24, enabled = index > 0) {
                                if (hiddenQueue) openAt(index - 1, autoplay) else player.seekToPreviousMediaItem()
                                touch++
                            }
                        }
                        val big = !controls
                        Box(
                            modifier = Modifier.size(if (big) 72.dp else 56.dp).clip(CircleShape)
                                .background(Color(0x73000000)).clickable { togglePlay() },
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.size(if (big) 34.dp else 26.dp)) {
                                drawPlayerGlyph(
                                    if (showPause) PlayerGlyph.PAUSE else PlayerGlyph.PLAY,
                                    Color.White,
                                    2.4.dp.toPx()
                                )
                            }
                        }
                        if (controls) {
                            PlayerGlyphButton(PlayerGlyph.NEXT, 24, enabled = index < queue.items.lastIndex) {
                                if (hiddenQueue) openAt(index + 1, autoplay) else player.seekToNextMediaItem()
                                touch++
                            }
                        }
                    }
                }
            }

            if (controls) {
                Row(
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
                        .background(Brush.verticalGradient(listOf(Color(0xB3000000), Color.Transparent)))
                        .statusBarsPadding().displayCutoutPadding()
                        .padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PlayerGlyphButton(PlayerGlyph.CLOSE, 18, onClick = onClose)
                    Text(
                        current.title,
                        color = Color.White,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                    )
                    if (queue.items.size > 1) {
                        Text(
                            "${index + 1}/${queue.items.size}",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp
                        )
                        PlayerGlyphButton(PlayerGlyph.LIST, 20) { showQueue = true }
                    }
                }
                Column(
                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                        .navigationBarsPadding().displayCutoutPadding()
                        .padding(start = 14.dp, end = 14.dp, top = 24.dp, bottom = 2.dp)
                ) {
                    PlayerTimeline(
                        positionMs = position,
                        durationMs = duration,
                        onSeek = { player.seekTo(it); position = it; touch++ },
                        onDragging = { seeking = it }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().height(36.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            Text(
                                MediaPlayback.speedLabel(speed),
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clip(RoundedCornerShape(6.dp))
                                    .clickable { speedMenu = true }.padding(horizontal = 6.dp, vertical = 6.dp)
                            )
                            DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                                MediaPlayback.SPEEDS.forEach { s ->
                                    DropdownMenuItem(
                                        text = { Text(MediaPlayback.speedLabel(s)) },
                                        onClick = {
                                            speed = s
                                            player.playbackParameters = PlaybackParameters(s)
                                            speedMenu = false
                                        }
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(6.dp))
                                .toggleable(value = autoplay, role = Role.Switch) {
                                    BehaviorSettings.setPlayerAutoplay(it)
                                    touch++
                                }.padding(horizontal = 6.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MiniSwitch(autoplay, MaterialTheme.colorScheme.primary)
                            Text(
                                LocalizationManager.Strings.playerAutoplay,
                                color = Color.White,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        if (current.isVideo) {
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { subMenu = true }
                                    .padding(horizontal = 6.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    "CC",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.border(1.5.dp, Color.White, RoundedCornerShape(3.dp))
                                        .padding(horizontal = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (finished && (queue.items.size > 1 || queue.groupKey.isNotEmpty())) {
                Row(
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                        .padding(bottom = if (controls) 110.dp else 24.dp, start = 16.dp, end = 16.dp)
                        .clip(RoundedCornerShape(16.dp)).background(Color(0xEE1E1E2E))
                        .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        LocalizationManager.Strings.playerAllFinished,
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f, fill = false).padding(end = 12.dp)
                    )
                    Text(
                        LocalizationManager.Strings.playerStartOver,
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable {
                                finished = false
                                if (hiddenQueue) openAt(0, true)
                                else {
                                    player.seekTo(0, 0L)
                                    player.play()
                                }
                            }.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }

            subNotice?.let {
                Text(
                    it,
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 64.dp, start = 16.dp, end = 16.dp)
                        .clip(RoundedCornerShape(12.dp)).background(Color(0xCC000000)).padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }

            if (subMenu) {
                PlayerSubtitleSheet(
                    tracks = AndroidSubtitles.embeddedTracks(tracks),
                    files = subFiles,
                    selected = AndroidSubtitles.selected(tracks),
                    onPick = {
                        pickSubtitle(it)
                        subMenu = false
                    },
                    onOpenFile = {
                        subMenu = false
                        resumeAfterPick[0] = player.isPlaying
                        try {
                            subPicker.launch(arrayOf("*/*"))
                        } catch (_: Throwable) {
                            resumeAfterPick[0] = false
                            subNotice = LocalizationManager.Strings.playerSubtitlesFailed
                        }
                    },
                    onDismiss = { subMenu = false }
                )
            }

            if (showQueue) {
                val listState = rememberLazyListState()
                LaunchedEffect(Unit) { listState.scrollToItem((index - 2).coerceAtLeast(0)) }
                Box(
                    modifier = Modifier.fillMaxSize().background(Color(0x99000000))
                        .clickable { showQueue = false }
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(380.dp)
                            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                            .background(MaterialTheme.colorScheme.surface).navigationBarsPadding()
                    ) {
                        itemsIndexed(queue.items) { i, item ->
                            val active = i == index
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    if (hiddenQueue) openAt(i, false) else player.seekTo(i, 0L)
                                    showQueue = false
                                }.background(
                                    if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent
                                ).padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${i + 1}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(end = 14.dp)
                                )
                                Text(
                                    item.title,
                                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(if (item.isVideo) "VID" else "AUD", fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
