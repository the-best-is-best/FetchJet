@file:OptIn(
    kotlin.experimental.ExperimentalNativeApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class
)

import io.ktor.client.HttpClient
import io.ktor.client.engine.winhttp.WinHttp
import io.ktor.client.request.header
import io.ktor.client.request.head
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.set
import kotlinx.cinterop.toKString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.use
import kotlin.concurrent.AtomicInt
import kotlin.concurrent.AtomicLong
import kotlin.native.CName
import kotlin.time.TimeSource

private object RateLimiter {
    val bytesPerSecond = AtomicLong(20L * 1024)
    private val mutex = Mutex()
    private var allowance = 0.0
    private var last = TimeSource.Monotonic.markNow()

    suspend fun take(bytes: Int) {
        while (true) {
            val limit = bytesPerSecond.value
            if (limit <= 0) return
            val wait = mutex.withLock {
                val now = TimeSource.Monotonic.markNow()
                allowance = minOf(
                    limit.toDouble(),
                    allowance + (now - last).inWholeMicroseconds * limit / 1_000_000.0
                )
                last = now
                if (allowance >= bytes) {
                    allowance -= bytes
                    0L
                } else {
                    ((bytes - allowance) * 1000 / limit).toLong().coerceAtLeast(10)
                }
            }
            if (wait == 0L) return
            delay(wait)
        }
    }
}

private class Task(val id: Int, val url: String, val name: String, val target: Path) {
    val state = AtomicInt(STATE_WAITING)
    val total = AtomicLong(0)
    val done = AtomicLong(0)
    var error = ""
    var job: Job? = null
    var lastDone = 0L
    var lastMark = TimeSource.Monotonic.markNow()
    var speed = 0L
}

private const val STATE_DONE = 0
private const val STATE_DOWNLOADING = 1
private const val STATE_PAUSED = 2
private const val STATE_FAILED = 3
private const val STATE_WAITING = 4
private const val PIECES = 4
private const val BUFFER = 16 * 1024

private object Engine {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val client by lazy { HttpClient(WinHttp) }
    val tasks = mutableListOf<Task>()
    val lock = Mutex()
    var destination: Path = "C:/Users/Public/Downloads".toPath()
    var nextId = 1

    fun start(task: Task) {
        task.state.value = STATE_DOWNLOADING
        task.job = scope.launch {
            runCatching { download(task) }
                .onSuccess { task.state.value = STATE_DONE }
                .onFailure { e ->
                    if (task.state.value == STATE_DOWNLOADING) {
                        task.error = e.message ?: e.toString()
                        task.state.value = STATE_FAILED
                    }
                }
        }
    }

    private suspend fun download(task: Task) {
        if (task.total.value == 0L) {
            val head = client.head(task.url)
            if (!head.status.isSuccess()) throw Exception("HTTP ${head.status.value}")
            task.total.value = head.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: 0L
        }
        val size = task.total.value
        val pieces = if (size > 1024 * 1024) PIECES else 1
        val piece = if (size > 0) size / pieces else 0
        val fs = FileSystem.SYSTEM
        fs.createDirectories(task.target.parent!!)
        val parts = (0 until pieces).map { "${task.target}.part$it".toPath() }
        task.done.value = parts.sumOf { if (fs.exists(it)) fs.metadata(it).size ?: 0L else 0L }
        (0 until pieces).map { i ->
            scope.async {
                val from = i * piece
                val to = if (i == pieces - 1) size - 1 else from + piece - 1
                val have = if (fs.exists(parts[i])) fs.metadata(parts[i]).size ?: 0L else 0L
                if (size > 0 && from + have > to) return@async
                fs.appendingSink(parts[i]).buffer().use { out ->
                    client.prepareGet(task.url) {
                        if (size > 0) header(HttpHeaders.Range, "bytes=${from + have}-$to")
                    }.execute { r ->
                        if (!r.status.isSuccess()) throw Exception("HTTP ${r.status.value}")
                        val ch = r.bodyAsChannel()
                        val buf = ByteArray(BUFFER)
                        while (true) {
                            val n = ch.readAvailable(buf, 0, buf.size)
                            if (n < 0) break
                            if (n == 0) continue
                            RateLimiter.take(n)
                            out.write(buf, 0, n)
                            task.done.addAndGet(n.toLong())
                        }
                    }
                }
            }
        }.awaitAll()
        fs.sink(task.target).buffer().use { out ->
            for (p in parts) {
                fs.source(p).buffer().use { out.writeAll(it) }
                fs.delete(p)
            }
        }
    }
}

private fun cString(text: String): CPointer<ByteVar> {
    val bytes = text.encodeToByteArray()
    val ptr = nativeHeap.allocArray<ByteVar>(bytes.size + 1)
    for (i in bytes.indices) ptr[i] = bytes[i]
    ptr[bytes.size] = 0
    return ptr
}

@CName("fj_set_destination")
fun fjSetDestination(path: CPointer<ByteVar>?) {
    path?.toKString()?.takeIf { it.isNotBlank() }?.let { Engine.destination = it.toPath() }
}

@CName("fj_set_limit")
fun fjSetLimit(bytesPerSecond: Long) {
    RateLimiter.bytesPerSecond.value = bytesPerSecond
}

@CName("fj_add")
fun fjAdd(url: CPointer<ByteVar>?): Int {
    val link = url?.toKString()?.trim().orEmpty()
    if (link.isEmpty()) return -1
    val name = link.substringBefore('?').substringAfterLast('/').ifBlank { "download" }
    val task = Engine.scope.run {
        val id = Engine.nextId++
        Task(id, link, name, Engine.destination / name)
    }
    Engine.tasks.add(0, task)
    Engine.start(task)
    return task.id
}

@CName("fj_toggle")
fun fjToggle(id: Int) {
    val task = Engine.tasks.firstOrNull { it.id == id } ?: return
    when (task.state.value) {
        STATE_DOWNLOADING -> {
            task.state.value = STATE_PAUSED
            task.job?.cancel()
        }

        STATE_PAUSED, STATE_FAILED, STATE_WAITING -> Engine.start(task)
    }
}

@CName("fj_snapshot")
fun fjSnapshot(): CPointer<ByteVar> {
    val now = TimeSource.Monotonic.markNow()
    val text = buildString {
        for (t in Engine.tasks.toList()) {
            val ms = (now - t.lastMark).inWholeMilliseconds
            if (ms >= 500) {
                val d = t.done.value
                t.speed =
                    if (t.state.value == STATE_DOWNLOADING) (d - t.lastDone) * 1000 / ms else 0
                t.lastDone = d
                t.lastMark = now
            }
            append(t.id).append('\t').append(t.name).append('\t').append(t.state.value).append('\t')
            append(t.total.value).append('\t').append(t.done.value).append('\t').append(t.speed)
                .append('\t')
            append(t.error.replace('\t', ' ').replace('\n', ' ')).append('\n')
        }
    }
    return cString(text)
}

@CName("fj_free")
fun fjFree(ptr: CPointer<ByteVar>?) {
    if (ptr != null) nativeHeap.free(ptr.rawValue)
}
