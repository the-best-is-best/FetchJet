package com.michelle.raouf.fetch_jet.ui

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.feature.video_cut.VideoCut
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardUiEvent
import com.michelle.raouf.fetch_jet.presentation.dashboard.VideoCutUi
import com.tbib.kmp_localization.LocalizationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File

private object FilmstripCache {
    val frames = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    val frameMs = HashMap<String, Double>()
}

private const val AUDIO_STEP_MS = 10.0

private class VideoInfo(val durationMs: Long, val frameMs: Double)

private fun readVideoInfo(path: String): VideoInfo {
    FilmstripCache.frameMs[path]?.let { cached ->
        return VideoInfo(-1L, cached)
    }
    val r = MediaMetadataRetriever()
    return try {
        r.setDataSource(path)
        val duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        val count = if (Build.VERSION.SDK_INT >= 28)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toLongOrNull() ?: 0L else 0L
        val fps = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull() ?: 0f
        val frame = when {
            count > 0L && duration > 0L -> (duration.toDouble() / count).coerceIn(5.0, 200.0)
            fps > 1f -> (1000.0 / fps).coerceIn(5.0, 200.0)
            else -> 1000.0 / 30.0
        }
        FilmstripCache.frameMs[path] = frame
        VideoInfo(duration, frame)
    } catch (_: Throwable) {
        VideoInfo(0L, 1000.0 / 30.0)
    } finally {
        runCatching { r.release() }
    }
}

private fun scaled(r: MediaMetadataRetriever, timeUs: Long, option: Int): Bitmap? {
    val bmp = if (Build.VERSION.SDK_INT >= 27) {
        r.getScaledFrameAtTime(timeUs, option, 192, 108)
    } else {
        r.getFrameAtTime(timeUs, option)?.let { full ->
            val h = 108
            val w = (full.width * h / full.height.coerceAtLeast(1)).coerceAtLeast(1)
            Bitmap.createScaledBitmap(full, w, h, true).also { if (it != full) full.recycle() }
        }
    }
    return bmp
}

private class FilmstripSource(val path: String) {
    val version = mutableIntStateOf(0)
    val requests = MutableStateFlow<TrimFrameRange?>(null)

    fun key(tileMs: Long, index: Long): String = "$path|$tileMs|$index"

    fun frame(tileMs: Long, index: Long): ImageBitmap? {
        version.intValue
        FilmstripCache.frames.get(key(tileMs, index))?.let { return it.asImageBitmap() }
        val mid = index * tileMs + tileMs / 2
        for (other in TRIM_TILE_LEVELS) {
            if (other == tileMs) continue
            FilmstripCache.frames.get(key(other, mid / other))?.let { return it.asImageBitmap() }
        }
        return null
    }
}

@Composable
private fun rememberFilmstrip(path: String, durationMs: Long, enabled: Boolean): FilmstripSource {
    val source = remember(path) { FilmstripSource(path) }
    LaunchedEffect(source, durationMs, enabled) {
        if (!enabled || durationMs <= 0L) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(path)
                source.requests.filterNotNull().collectLatest { range ->
                    val option = if (range.tileMs < 15_000L) MediaMetadataRetriever.OPTION_CLOSEST
                    else MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                    for (i in range.order()) {
                        currentCoroutineContext().ensureActive()
                        val key = source.key(range.tileMs, i)
                        if (FilmstripCache.frames.get(key) != null) continue
                        val ms = (i * range.tileMs + range.tileMs / 2).coerceAtMost((durationMs - 1L).coerceAtLeast(0L))
                        val bmp = runCatching { scaled(r, ms * 1000L, option) }.getOrNull() ?: continue
                        FilmstripCache.frames.put(key, bmp)
                        withContext(Dispatchers.Main) { source.version.intValue++ }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Throwable) {
            } finally {
                runCatching { r.release() }
            }
        }
    }
    return source
}

@Composable
internal actual fun VideoTrimScreen(ui: VideoCutUi, path: String, onEvent: (DashboardUiEvent) -> Unit) {
    val s = LocalizationManager.Strings
    val context = LocalContext.current
    val close = { onEvent(DashboardUiEvent.OnDismissVideoCut) }
    val player = remember(path) {
        ExoPlayer.Builder(context).build().apply {
            setSeekParameters(SeekParameters.EXACT)
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
            prepare()
            playWhenReady = false
        }
    }
    var info by remember(path) { mutableStateOf(VideoInfo(ui.durationMs, 1000.0 / 30.0)) }
    val audio = ui.isAudio
    LaunchedEffect(path) {
        if (audio) {
            info = VideoInfo(ui.durationMs, AUDIO_STEP_MS)
            return@LaunchedEffect
        }
        val read = withContext(Dispatchers.IO) { readVideoInfo(path) }
        info = VideoInfo(if (ui.durationMs > 0L) ui.durationMs else read.durationMs, read.frameMs)
    }
    var playerDuration by remember { mutableLongStateOf(0L) }
    val duration = if (info.durationMs > 0L) info.durationMs else playerDuration
    val minLength = (info.frameMs * 2).toLong().coerceAtLeast(com.michelle.raouf.fetch_jet.feature.video_cut.VideoCut.MIN_LENGTH_MS)
    var start by remember(ui.taskId) { mutableLongStateOf(ui.startMs.coerceAtLeast(0L)) }
    var end by remember(ui.taskId) { mutableLongStateOf(ui.endMs) }
    LaunchedEffect(duration) {
        if (duration > 0L && (end <= 0L || end > duration)) end = duration
        if (start >= end && duration > 0L) start = 0L
    }
    var position by remember { mutableLongStateOf(start) }
    var selected by remember { mutableStateOf(TrimHandle.START) }
    var playing by remember { mutableStateOf(false) }
    var rangePlay by remember { mutableStateOf(false) }
    val timeline = rememberTrimTimelineState()

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
                if (!isPlaying) rangePlay = false
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY && player.duration > 0) playerDuration = player.duration
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player) {
        if (start > 0L) player.seekTo(start)
        while (isActive) {
            if (player.isPlaying) {
                position = player.currentPosition.coerceAtLeast(0L)
                if (rangePlay && position >= end) {
                    player.pause()
                    player.seekTo(end)
                    position = end
                    rangePlay = false
                }
            }
            delay(if (rangePlay) 20 else 100)
        }
    }

    fun seek(ms: Long) {
        position = ms
        player.seekTo(ms)
    }

    fun frameIndex(ms: Long): Long = kotlin.math.round(ms / info.frameMs).toLong()

    fun frameStart(index: Long): Long = kotlin.math.floor(index * info.frameMs + 0.0001).toLong()

    fun lastKeptFrame(endMs: Long): Long = frameStart(frameIndex(endMs) - 1).coerceAtLeast(start)

    fun snap(ms: Long): Long = if (duration > 0L && ms >= duration) duration else frameStart(frameIndex(ms)).coerceAtMost(duration)

    fun setHandle(handle: TrimHandle, ms: Long) {
        if (player.isPlaying) player.pause()
        val value = snap(ms)
        if (handle == TrimHandle.START) start = value else end = value
        seek(if (handle == TrimHandle.END) lastKeptFrame(value) else value)
    }

    fun nudge(dir: Int) {
        if (selected == TrimHandle.START) {
            val next = frameStart(frameIndex(start) + dir)
            setHandle(TrimHandle.START, next.coerceIn(0L, (end - minLength).coerceAtLeast(0L)))
            timeline.reveal(start)
        } else {
            val next = if (end >= duration && dir > 0) duration else frameStart(frameIndex(end) + dir)
            setHandle(TrimHandle.END, next.coerceIn((start + minLength).coerceAtMost(duration), duration))
            timeline.reveal(end)
        }
    }

    fun choose(handle: TrimHandle) {
        selected = handle
        seek(if (handle == TrimHandle.START) start else lastKeptFrame(end))
        timeline.reveal(if (handle == TrimHandle.START) start else end)
    }

    BackHandler { close() }

    Dialog(
        onDismissRequest = close,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val view = LocalView.current
        DisposableEffect(view) {
            val window = (view.parent as? DialogWindowProvider)?.window
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        }
        Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlayerGlyphButton(PlayerGlyph.CLOSE, 24, onClick = close)
                Column(modifier = Modifier.weight(1f).padding(horizontal = 6.dp)) {
                    Text(if (audio) s.audioCutTitle else s.videoCutOnTimeline, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(
                        ui.fileName,
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Button(
                    enabled = !ui.reading && duration > 0L && end - start >= minLength,
                    shape = RoundedCornerShape(10.dp),
                    onClick = {
                        player.pause()
                        onEvent(DashboardUiEvent.OnVideoCutRangeChanged(start, end))
                        onEvent(DashboardUiEvent.OnVideoCutStartClicked)
                    },
                    modifier = Modifier.padding(end = 6.dp)
                ) { Text("✂ ${s.videoCutApply}", fontWeight = FontWeight.Bold) }
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (audio) {
                    Text("♪", color = Color.White.copy(alpha = 0.35f), fontSize = 96.sp)
                } else AndroidView(
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
                        }
                    }
                )
            }
            Box(
                modifier = Modifier.fillMaxWidth().background(Color(0xFF101016)).navigationBarsPadding(),
                contentAlignment = Alignment.Center
            ) {
                val filmstrip = rememberFilmstrip(path, duration, !audio)
                val bars = (duration / 1000L * 20L).coerceIn(40L, 4000L).toInt()
                var wave by remember(path) { mutableStateOf<List<Float>?>(if (audio) emptyList() else null) }
                if (audio) LaunchedEffect(path, bars, duration) {
                    if (duration > 0L) wave = VideoCut.waveform(path, bars, duration)
                }
                Column(
                    modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(46.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable {
                                    if (player.isPlaying) {
                                        player.pause()
                                    } else {
                                        rangePlay = true
                                        if (position < start || position >= end - 50L) seek(start)
                                        player.play()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.size(22.dp)) {
                                drawPlayerGlyph(if (playing) PlayerGlyph.PAUSE else PlayerGlyph.PLAY, Color.White, 2.4.dp.toPx())
                            }
                        }
                        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(s.videoCutPlayRange, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                com.michelle.raouf.fetch_jet.utils.ltr("${formatTrimTime(position)} / ${formatTrimTime(duration)}"),
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 12.sp
                            )
                        }
                    }
                    TrimTimeline(
                        state = timeline,
                        durationMs = duration,
                        startMs = start,
                        endMs = if (end > 0L) end else duration,
                        positionMs = position,
                        wave = wave,
                        frameAt = if (audio) null else filmstrip::frame,
                        onNeedFrames = { filmstrip.requests.value = it },
                        following = playing,
                        selected = selected,
                        minLengthMs = minLength,
                        onSelect = { choose(it) },
                        onHandleMoved = { handle, ms -> setHandle(handle, ms) },
                        onDragFinished = {},
                        onScrub = { ms ->
                            if (player.isPlaying) player.pause()
                            seek(ms)
                        },
                        modifier = Modifier
                            .windowInsetsPadding(WindowInsets.systemGestures.only(WindowInsetsSides.Horizontal))
                            .systemGestureExclusion()
                    )
                    CutMessage(ui, dark = true)
                    TrimNudgeRow(
                        startLabel = s.videoCutFrom,
                        endLabel = s.videoCutTo,
                        lengthLabel = s.videoCutDuration,
                        startMs = start,
                        endMs = end,
                        selected = selected,
                        onSelect = { choose(it) },
                        onNudge = { nudge(it) }
                    )
                }
            }
        }
    }
}
