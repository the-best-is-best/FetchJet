package com.michelle.raouf.fetch_jet.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.LruCache
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.feature.pdf_reader.PdfReader
import com.tbib.kmp_localization.LocalizationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.sqrt

private class PdfDocument(
    val renderer: PdfRenderer,
    val descriptor: ParcelFileDescriptor,
    val sizes: List<IntSize>
) {
    fun close() {
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
    }
}

private sealed interface PdfLoad {
    data object Loading : PdfLoad
    data class Ready(val doc: PdfDocument) : PdfLoad
    data class Failed(val password: Boolean) : PdfLoad
}

private class PdfPageCache(maxBytes: Int) {
    private val handler = Handler(Looper.getMainLooper())
    var visible: Set<Int> = emptySet()

    private val lru = object : LruCache<Int, Bitmap>(maxBytes) {
        override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount

        override fun entryRemoved(evicted: Boolean, key: Int, oldValue: Bitmap, newValue: Bitmap?) {
            if (oldValue === newValue) return
            if (evicted && key in visible) return
            recycleLater(oldValue)
        }
    }

    fun get(page: Int): Bitmap? = lru.get(page)?.takeIf { !it.isRecycled }

    fun put(page: Int, bitmap: Bitmap) {
        lru.put(page, bitmap)
    }

    fun clear() {
        lru.evictAll()
    }

    private fun recycleLater(bitmap: Bitmap) {
        handler.postDelayed({ if (!bitmap.isRecycled) bitmap.recycle() }, 600)
    }
}

private const val MAX_PAGE_PIXELS = 12_000_000L
private const val MAX_RENDER_FACTOR = 2.5f
private const val DOUBLE_TAP_ZOOM = 2.5f
private val ReaderBackground = Color(0xFF1B1B1F)
private val BarBackground = Color(0xE6000000)

private fun openDocument(path: String): PdfLoad {
    return try {
        val descriptor = ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)
        try {
            val renderer = PdfRenderer(descriptor)
            val sizes = (0 until renderer.pageCount).map { i ->
                renderer.openPage(i).use { IntSize(it.width.coerceAtLeast(1), it.height.coerceAtLeast(1)) }
            }
            PdfLoad.Ready(PdfDocument(renderer, descriptor, sizes))
        } catch (t: Throwable) {
            runCatching { descriptor.close() }
            throw t
        }
    } catch (_: SecurityException) {
        PdfLoad.Failed(password = true)
    } catch (_: Throwable) {
        PdfLoad.Failed(password = false)
    }
}

private fun renderSize(page: IntSize, width: Int): IntSize {
    var w = width.coerceAtLeast(1)
    var h = (w.toLong() * page.height / page.width).toInt().coerceAtLeast(1)
    val pixels = w.toLong() * h
    if (pixels > MAX_PAGE_PIXELS) {
        val f = sqrt(MAX_PAGE_PIXELS.toDouble() / pixels)
        w = (w * f).toInt().coerceAtLeast(1)
        h = (h * f).toInt().coerceAtLeast(1)
    }
    return IntSize(w, h)
}

@Composable
internal actual fun PdfReaderScreen(request: PdfOpen, onClose: () -> Unit, onOpenOutside: () -> Unit) {
    val dispatcher = remember { Executors.newSingleThreadExecutor().asCoroutineDispatcher() }
    val opened = remember { AtomicReference<PdfDocument?>(null) }
    val cache = remember {
        val budget = (Runtime.getRuntime().maxMemory() / 5).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        PdfPageCache(budget)
    }
    var load by remember { mutableStateOf<PdfLoad>(PdfLoad.Loading) }

    LaunchedEffect(request.path) {
        load = withContext(dispatcher) {
            openDocument(request.path).also { if (it is PdfLoad.Ready) opened.set(it.doc) }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cache.clear()
            CoroutineScope(dispatcher).launch {
                opened.getAndSet(null)?.close()
            }.invokeOnCompletion { dispatcher.close() }
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val view = LocalView.current
        DisposableEffect(view) {
            val window = (view.parent as? DialogWindowProvider)?.window
            if (request.hidden) window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            onDispose { }
        }
        val listState = rememberLazyListState()
        val scope = rememberCoroutineScope()
        val ready = load as? PdfLoad.Ready
        val pageCount = ready?.doc?.sizes?.size ?: 0
        var showGoTo by remember { mutableStateOf(false) }
        var zoom by remember { mutableFloatStateOf(1f) }
        var fitRequests by remember { mutableIntStateOf(0) }
        var barHeight by remember { mutableIntStateOf(0) }
        val currentPage by remember { derivedStateOf { centerPage(listState) } }

        Box(modifier = Modifier.fillMaxSize().background(ReaderBackground)) {
            when (val state = load) {
                PdfLoad.Loading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )

                is PdfLoad.Failed -> PdfFailure(state.password, Modifier.align(Alignment.Center), onOpenOutside)

                is PdfLoad.Ready -> PdfPages(
                    doc = state.doc,
                    request = request,
                    listState = listState,
                    cache = cache,
                    dispatcher = dispatcher,
                    zoom = zoom,
                    onZoom = { zoom = it },
                    fitRequests = fitRequests,
                    topPadding = barHeight
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).background(BarBackground)
                    .onSizeChanged { barHeight = it.height }
                    .statusBarsPadding().padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlayerGlyphButton(PlayerGlyph.CLOSE, 24, onClick = onClose)
                Text(
                    request.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
                )
                if (ready != null && abs(zoom - 1f) > 0.01f) {
                    Text(
                        LocalizationManager.Strings.pdfFitWidth,
                        color = Color.White,
                        fontSize = 13.sp,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { fitRequests++ }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
                if (pageCount > 0) {
                    Text(
                        PdfReader.pageLabel(PdfReader.clampPage(currentPage, pageCount), pageCount),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(end = 6.dp).clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.16f))
                            .clickable { showGoTo = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        if (showGoTo && pageCount > 0) {
            GoToPageDialog(
                pageCount = pageCount,
                onDismiss = { showGoTo = false },
                onGo = { page ->
                    showGoTo = false
                    scope.launch { listState.scrollToItem(page) }
                }
            )
        }
    }
}

private fun centerPage(listState: LazyListState): Int {
    val info = listState.layoutInfo
    val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
    return info.visibleItemsInfo.firstOrNull { it.offset <= center && it.offset + it.size > center }?.index
        ?: listState.firstVisibleItemIndex
}

@Composable
private fun PdfPages(
    doc: PdfDocument,
    request: PdfOpen,
    listState: LazyListState,
    cache: PdfPageCache,
    dispatcher: kotlinx.coroutines.CoroutineDispatcher,
    zoom: Float,
    onZoom: (Float) -> Unit,
    fitRequests: Int,
    topPadding: Int
) {
    val pageCount = doc.sizes.size
    val density = LocalDensity.current
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var panX by remember { mutableFloatStateOf(0f) }
    var settledZoom by remember { mutableFloatStateOf(zoom) }
    val zoomNow by rememberUpdatedState(zoom)
    val onZoomNow by rememberUpdatedState(onZoom)

    LaunchedEffect(zoom) {
        delay(250)
        settledZoom = zoom
    }

    LaunchedEffect(doc) {
        val start = PdfReader.clampPage(request.task?.let { PdfReader.lastPage(it) } ?: 0, pageCount)
        if (start > 0) listState.scrollToItem(start)
    }

    LaunchedEffect(doc) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.index }.toSet() }
            .distinctUntilChanged()
            .collect { cache.visible = it }
    }

    val task = request.task
    if (task != null && !request.hidden) {
        LaunchedEffect(doc) {
            snapshotFlow { centerPage(listState) }.distinctUntilChanged().collectLatest { page ->
                delay(400)
                withContext(Dispatchers.IO) { PdfReader.rememberPage(task, page) }
            }
        }
        DisposableEffect(doc) {
            onDispose { PdfReader.rememberPage(task, PdfReader.clampPage(centerPage(listState), pageCount)) }
        }
    }

    val itemWidth = (viewport.width * zoom).toInt().coerceAtLeast(1)
    val maxPan = (itemWidth - viewport.width).coerceAtLeast(0).toFloat()
    if (panX > maxPan) panX = maxPan

    fun applyZoom(target: Float, focus: Offset) {
        val old = zoomNow
        val next = target.coerceIn(PdfReader.MIN_ZOOM, PdfReader.MAX_ZOOM)
        if (next == old || viewport.width <= 0) return
        val ratio = next / old
        val nextWidth = viewport.width * next
        val nextMax = (nextWidth - viewport.width).coerceAtLeast(0f)
        panX = ((panX + focus.x) * ratio - focus.x).coerceIn(0f, nextMax)
        val listY = focus.y - topPadding
        listState.dispatchRawDelta((listState.firstVisibleItemScrollOffset + listY) * (ratio - 1f))
        onZoomNow(next)
    }

    LaunchedEffect(fitRequests) {
        if (fitRequests > 0) applyZoom(1f, Offset(0f, topPadding.toFloat()))
    }

    val horizontal = rememberScrollableState { delta ->
        val before = panX
        val limit = ((viewport.width * zoomNow) - viewport.width).coerceAtLeast(0f)
        panX = (panX - delta).coerceIn(0f, limit)
        before - panX
    }

    val renderWidth = (viewport.width * settledZoom)
        .coerceAtMost(viewport.width * MAX_RENDER_FACTOR).toInt().coerceAtLeast(1)

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = Modifier.fillMaxSize().clipToBounds()
                .onSizeChanged { viewport = it }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.count { it.pressed } >= 2) {
                                val factor = event.calculateZoom()
                                val centroid = event.calculateCentroid(useCurrent = true)
                                if (factor != 1f && centroid != Offset.Unspecified) applyZoom(zoomNow * factor, centroid)
                                val pan = event.calculatePan()
                                if (pan != Offset.Zero) {
                                    val limit = ((viewport.width * zoomNow) - viewport.width).coerceAtLeast(0f)
                                    panX = (panX - pan.x).coerceIn(0f, limit)
                                    listState.dispatchRawDelta(-pan.y)
                                }
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { offset ->
                            applyZoom(if (zoomNow > 1.01f) 1f else DOUBLE_TAP_ZOOM, offset)
                        }
                    )
                }
                .scrollable(horizontal, Orientation.Horizontal)
        ) {
            if (viewport.width > 0) {
                val navBottom = WindowInsets.navigationBars.getBottom(density)
                LazyColumn(
                    state = listState,
                    contentPadding = with(density) {
                        PaddingValues(top = topPadding.toDp() + 8.dp, bottom = navBottom.toDp() + 16.dp)
                    },
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.layout { measurable, constraints ->
                        val placeable = measurable.measure(Constraints.fixed(itemWidth, constraints.maxHeight))
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            val x = if (itemWidth <= constraints.maxWidth) (constraints.maxWidth - itemWidth) / 2
                            else -panX.toInt()
                            placeable.place(IntOffset(x, 0))
                        }
                    }
                ) {
                    items(pageCount, key = { it }) { index ->
                        PdfPage(
                            doc = doc,
                            index = index,
                            renderWidth = renderWidth,
                            cache = cache,
                            dispatcher = dispatcher
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPage(
    doc: PdfDocument,
    index: Int,
    renderWidth: Int,
    cache: PdfPageCache,
    dispatcher: kotlinx.coroutines.CoroutineDispatcher
) {
    val size = doc.sizes[index]
    var bitmap by remember(index) { mutableStateOf(cache.get(index)) }
    LaunchedEffect(index, renderWidth) {
        val have = bitmap?.takeIf { !it.isRecycled } ?: cache.get(index)
        if (have != null) bitmap = have
        val target = renderSize(size, renderWidth)
        if (have != null && abs(have.width - target.width) <= target.width / 10) return@LaunchedEffect
        if (have != null) delay(80)
        val fresh = withContext(dispatcher) {
            ensureActive()
            try {
                val page = doc.renderer.openPage(index)
                try {
                    Bitmap.createBitmap(target.width, target.height, Bitmap.Config.ARGB_8888).also {
                        it.eraseColor(android.graphics.Color.WHITE)
                        page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                } finally {
                    page.close()
                }
            } catch (_: Throwable) {
                null
            }
        } ?: return@LaunchedEffect
        cache.put(index, fresh)
        bitmap = fresh
    }
    Box(
        modifier = Modifier.fillMaxWidth()
            .aspectRatio(size.width.toFloat() / size.height.toFloat())
            .background(Color.White)
            .drawBehind {
                val b = bitmap
                if (b != null && !b.isRecycled) {
                    drawImage(
                        image = b.asImageBitmap(),
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(b.width, b.height),
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(this.size.width.toInt(), this.size.height.toInt()),
                        filterQuality = FilterQuality.Medium
                    )
                }
            }
    )
}

@Composable
private fun PdfFailure(password: Boolean, modifier: Modifier, onOpenOutside: () -> Unit) {
    Column(
        modifier = modifier.padding(32.dp).clip(RoundedCornerShape(14.dp))
            .background(Color(0xCC000000)).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            if (password) LocalizationManager.Strings.pdfPasswordProtected else LocalizationManager.Strings.pdfOpenFailed,
            color = Color.White,
            fontSize = 15.sp,
            textAlign = TextAlign.Center
        )
        Button(onClick = onOpenOutside, shape = RoundedCornerShape(10.dp)) {
            Text(LocalizationManager.Strings.playerModeOff)
        }
    }
}

@Composable
private fun GoToPageDialog(pageCount: Int, onDismiss: () -> Unit, onGo: (Int) -> Unit) {
    var text by remember { mutableStateOf("") }
    val page = PdfReader.parsePage(text, pageCount)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(18.dp),
        title = { Text(LocalizationManager.Strings.pdfGoToPage, fontWeight = FontWeight.Bold) },
        text = {
            FetchJetTextField(
                value = text,
                onValueChange = { v -> text = v.filter { it.isDigit() }.take(6) },
                label = LocalizationManager.Strings.pdfPage,
                placeholder = "1 - $pageCount",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                keyboardActions = KeyboardActions(onDone = { page?.let(onGo) }),
                error = if (text.isNotEmpty() && page == null) "1 - $pageCount" else null
            )
        },
        confirmButton = {
            Button(onClick = { page?.let(onGo) }, enabled = page != null, shape = RoundedCornerShape(10.dp)) {
                Text(LocalizationManager.Strings.ok)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(10.dp)) {
                Text(LocalizationManager.Strings.cancel)
            }
        }
    )
}
