package com.michelle.raouf.fetch_jet.ui

import android.net.Uri
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.michelle.raouf.fetch_jet.feature.media_player.MediaPlayback
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.feature.media_player.PlaybackQueue
import com.michelle.raouf.fetch_jet.feature.media_player.PlayerProgress
import com.tbib.kmp_localization.LocalizationManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

private enum class PlayerGlyph { CLOSE, PLAY, PAUSE, PREVIOUS, NEXT, LIST }

private fun DrawScope.drawPlayerGlyph(kind: PlayerGlyph, color: Color, stroke: Float) {
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
    }
}

@Composable
private fun PlayerGlyphButton(
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
internal actual fun MediaPlayerScreen(queue: PlaybackQueue, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setSeekParameters(SeekParameters.CLOSEST_SYNC)
            setMediaItems(queue.items.map { MediaItem.fromUri(Uri.fromFile(File(it.path))) }, queue.index, 0L)
            prepare()
            playWhenReady = true
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
    var failed by remember { mutableStateOf<String?>(null) }
    var touch by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }
    val current = queue.items[index.coerceIn(0, queue.items.lastIndex)]

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                index = player.currentMediaItemIndex
                failed = null
                finished = false
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) {
                    player.pause()
                    controls = true
                }
                queue.items.getOrNull(index)?.let { PlayerProgress.remember(queue.groupKey, it.taskId) }
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) duration = player.duration.coerceAtLeast(0L)
                if (state == Player.STATE_ENDED) {
                    controls = true
                    finished = true
                    PlayerProgress.clear(queue.groupKey)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                failed = error.errorCodeName
                if (player.hasNextMediaItem()) {
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
        PlayerProgress.remember(queue.groupKey, queue.items[queue.index].taskId)
    }

    LaunchedEffect(player) {
        while (isActive) {
            position = player.currentPosition.coerceAtLeast(0L)
            if (player.duration > 0) duration = player.duration
            delay(250)
        }
    }

    LaunchedEffect(controls, playing, touch, showQueue, speedMenu, hold) {
        if (controls && playing && !showQueue && !speedMenu && hold == 0) {
            delay(3500)
            controls = false
        }
    }

    BackHandler {
        if (showQueue) showQueue = false else onClose()
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val view = LocalView.current
        DisposableEffect(view) {
            val window = (view.parent as? DialogWindowProvider)?.window
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
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
                        }
                    }
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
                }
            )

            if (hold != 0) {
                Box(
                    modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 64.dp)
                        .clip(RoundedCornerShape(20.dp)).background(Color(0xAA000000))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(if (hold > 0) "▶▶  2x" else "◀◀  2x", color = Color.White, fontSize = 14.sp)
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

            if (controls) {
                Column(
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
                        .background(Color(0x99000000)).statusBarsPadding().padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PlayerGlyphButton(PlayerGlyph.CLOSE, 24, onClick = onClose)
                        Text(
                            current.title,
                            color = Color.White,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
                        )
                        if (queue.items.size > 1) {
                            Text(
                                "${index + 1}/${queue.items.size}",
                                color = Color.White,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            PlayerGlyphButton(PlayerGlyph.LIST, 24) { showQueue = true }
                        }
                    }
                }
                Column(
                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                        .background(Color(0x99000000)).navigationBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    PlayerTimeline(
                        positionMs = position,
                        durationMs = duration,
                        onSeek = { player.seekTo(it); position = it; touch++ }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box {
                            Text(
                                MediaPlayback.speedLabel(speed),
                                color = Color.White,
                                fontSize = 14.sp,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                    .clickable { speedMenu = true }.padding(horizontal = 12.dp, vertical = 10.dp)
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PlayerGlyphButton(PlayerGlyph.PREVIOUS, 28, enabled = index > 0) {
                                player.seekToPreviousMediaItem(); touch++
                            }
                            Box(
                                modifier = Modifier.padding(horizontal = 8.dp).size(60.dp).clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary).clickable {
                                        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0L)
                                        if (player.isPlaying) player.pause() else player.play()
                                        touch++
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.size(30.dp)) {
                                    drawPlayerGlyph(
                                        if (playing) PlayerGlyph.PAUSE else PlayerGlyph.PLAY,
                                        Color.White,
                                        2.4.dp.toPx()
                                    )
                                }
                            }
                            PlayerGlyphButton(PlayerGlyph.NEXT, 28, enabled = index < queue.items.lastIndex) {
                                player.seekToNextMediaItem(); touch++
                            }
                        }
                        Box(modifier = Modifier.size(64.dp))
                    }
                }
            }

            if (finished && (queue.items.size > 1 || queue.groupKey.isNotEmpty())) {
                Row(
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                        .padding(bottom = if (controls) 170.dp else 24.dp, start = 16.dp, end = 16.dp)
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
                                player.seekTo(0, 0L)
                                player.play()
                            }.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
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
                                    player.seekTo(i, 0L)
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
