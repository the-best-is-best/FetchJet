@file:OptIn(ExperimentalForeignApi::class, DelicateCoroutinesApi::class)

package com.michelle.raouf.fetch_jet.platform.macos

import com.michelle.raouf.fetch_jet.platform.desktop.OsChildProcess
import com.michelle.raouf.fetch_jet.platform.desktop.OsProcessException
import kotlin.concurrent.AtomicInt
import kotlin.concurrent.AtomicReference
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.cstr
import kotlinx.cinterop.get
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.set
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import platform.posix.EINTR
import platform.posix.FD_CLOEXEC
import platform.posix.F_SETFD
import platform.posix.SIGKILL
import platform.posix.SIGPIPE
import platform.posix.WNOHANG
import platform.posix.close
import platform.posix.errno
import platform.posix.fcntl
import platform.posix.pipe
import platform.posix.read
import platform.posix.strerror
import platform.posix.usleep
import platform.posix.waitpid
import platform.posix.write

private typealias FnSpawn = CFunction<
    (CPointer<IntVar>?, CPointer<ByteVar>?, COpaquePointer?, COpaquePointer?, CPointer<CPointerVar<ByteVar>>?, CPointer<CPointerVar<ByteVar>>?) -> Int
>
private typealias FnOne = CFunction<(COpaquePointer?) -> Int>
private typealias FnDup2 = CFunction<(COpaquePointer?, Int, Int) -> Int>
private typealias FnAddOpen = CFunction<(COpaquePointer?, Int, CPointer<ByteVar>?, Int, UShort) -> Int>
private typealias FnAddChdir = CFunction<(COpaquePointer?, CPointer<ByteVar>?) -> Int>
private typealias FnSetFlags = CFunction<(COpaquePointer?, Short) -> Int>
private typealias FnSetGroup = CFunction<(COpaquePointer?, Int) -> Int>
private typealias FnSetSet = CFunction<(COpaquePointer?, COpaquePointer?) -> Int>

private const val SPAWN_SETPGROUP = 0x02
private const val SPAWN_SETSIGDEF = 0x04
private const val SPAWN_SETSIGMASK = 0x08
private const val SPAWN_CLOEXEC_DEFAULT = 0x4000
private const val OPEN_READ = 0
private const val OPEN_WRITE = 1
private const val OPAQUE_BYTES = 64

private object Spawn {
    val spawnp by lazy { macSymbol<FnSpawn>("posix_spawnp") }
    val actionsInit by lazy { macSymbol<FnOne>("posix_spawn_file_actions_init") }
    val actionsDestroy by lazy { macSymbol<FnOne>("posix_spawn_file_actions_destroy") }
    val addDup2 by lazy { macSymbol<FnDup2>("posix_spawn_file_actions_adddup2") }
    val addOpen by lazy { macSymbol<FnAddOpen>("posix_spawn_file_actions_addopen") }
    val addChdir by lazy { macSymbol<FnAddChdir>("posix_spawn_file_actions_addchdir_np") }
    val attrInit by lazy { macSymbol<FnOne>("posix_spawnattr_init") }
    val attrDestroy by lazy { macSymbol<FnOne>("posix_spawnattr_destroy") }
    val setFlags by lazy { macSymbol<FnSetFlags>("posix_spawnattr_setflags") }
    val setGroup by lazy { macSymbol<FnSetGroup>("posix_spawnattr_setpgroup") }
    val setMask by lazy { macSymbol<FnSetSet>("posix_spawnattr_setsigmask") }
    val setDefault by lazy { macSymbol<FnSetSet>("posix_spawnattr_setsigdefault") }
}

private fun errorText(code: Int): String = strerror(code)?.toKString() ?: "error $code"

private fun MemScope.newPipe(): Pair<Int, Int> {
    val fds = allocArray<IntVar>(2)
    if (pipe(fds) != 0) throw OsProcessException("pipe failed: ${errorText(errno)}")
    fcntl(fds[0], F_SETFD, FD_CLOEXEC)
    fcntl(fds[1], F_SETFD, FD_CLOEXEC)
    return fds[0] to fds[1]
}

private fun MemScope.cArray(items: List<String>): CPointer<CPointerVar<ByteVar>> {
    val array = allocArray<CPointerVar<ByteVar>>(items.size + 1)
    items.forEachIndexed { i, item -> array[i] = item.cstr.ptr }
    array[items.size] = null
    return array
}

private fun reapLater(pid: Int) {
    GlobalScope.launch(Dispatchers.IO) {
        memScoped {
            val status = alloc<IntVar>()
            while (waitpid(pid, status.ptr, 0) < 0 && errno == EINTR) {
            }
        }
    }
}

fun macStartProcess(
    command: List<String>,
    environment: Map<String, String?>,
    workDir: String?,
    pipeStdin: Boolean,
    mergeStderr: Boolean,
    captureOutput: Boolean,
    detached: Boolean,
): OsChildProcess = memScoped {
    if (command.isEmpty()) throw OsProcessException("empty command")
    val spawn = Spawn.spawnp ?: throw OsProcessException("posix_spawnp is not available")
    val argv = if (workDir != null && Spawn.addChdir == null) {
        listOf("/bin/sh", "-c", "cd \"\$0\" && exec \"\$@\"", workDir) + command
    } else {
        command
    }
    val merged = LinkedHashMap<String, String>(macEnvironment())
    for ((name, value) in environment) if (value == null) merged.remove(name) else merged[name] = value
    val envp = cArray(merged.map { (k, v) -> "$k=$v" })

    val parentEnds = ArrayList<Int>()
    val childEnds = ArrayList<Int>()
    var started = false
    val actions = allocArray<ByteVar>(OPAQUE_BYTES)
    val attributes = allocArray<ByteVar>(OPAQUE_BYTES)
    Spawn.actionsInit!!(actions)
    Spawn.attrInit!!(attributes)
    try {
        var stdinWrite = -1
        var stdoutRead = -1
        var stderrRead = -1
        val nullDevice = "/dev/null".cstr.ptr
        if (pipeStdin) {
            val (r, w) = newPipe()
            stdinWrite = w
            parentEnds.add(w)
            childEnds.add(r)
            Spawn.addDup2!!(actions, r, 0)
        } else {
            Spawn.addOpen!!(actions, 0, nullDevice, OPEN_READ, 0u)
        }
        if (captureOutput) {
            val (r, w) = newPipe()
            stdoutRead = r
            parentEnds.add(r)
            childEnds.add(w)
            Spawn.addDup2!!(actions, w, 1)
            if (mergeStderr) {
                Spawn.addDup2!!(actions, w, 2)
            } else {
                val (er, ew) = newPipe()
                stderrRead = er
                parentEnds.add(er)
                childEnds.add(ew)
                Spawn.addDup2!!(actions, ew, 2)
            }
        } else {
            Spawn.addOpen!!(actions, 1, nullDevice, OPEN_WRITE, 0u)
            Spawn.addOpen!!(actions, 2, nullDevice, OPEN_WRITE, 0u)
        }
        if (workDir != null) Spawn.addChdir?.invoke(actions, workDir.cstr.ptr)

        val noSignals = alloc<UIntVar>()
        noSignals.value = 0u
        val defaults = alloc<UIntVar>()
        defaults.value = 1u shl (SIGPIPE - 1)
        Spawn.setMask!!(attributes, noSignals.ptr)
        Spawn.setDefault!!(attributes, defaults.ptr)
        Spawn.setGroup!!(attributes, 0)
        Spawn.setFlags!!(attributes, (SPAWN_SETPGROUP or SPAWN_SETSIGMASK or SPAWN_SETSIGDEF or SPAWN_CLOEXEC_DEFAULT).toShort())

        val pid = alloc<IntVar>()
        val code = spawn(pid.ptr, argv[0].cstr.ptr, actions, attributes, cArray(argv), envp)
        if (code != 0) throw OsProcessException("could not start ${command[0]}: ${errorText(code)}")
        started = true
        if (detached) {
            reapLater(pid.value)
            parentEnds.forEach { close(it) }
            MacChildProcess(pid.value, -1, -1, -1, reaped = true)
        } else {
            MacChildProcess(pid.value, stdinWrite, stdoutRead, stderrRead, reaped = false)
        }
    } finally {
        childEnds.forEach { close(it) }
        if (!started) parentEnds.forEach { close(it) }
        Spawn.actionsDestroy?.invoke(actions)
        Spawn.attrDestroy?.invoke(attributes)
    }
}

private class MacChildProcess(
    private val processId: Int,
    stdinWrite: Int,
    stdoutRead: Int,
    stderrRead: Int,
    reaped: Boolean,
) : OsChildProcess {
    private val stdinFd = AtomicInt(stdinWrite)
    private val stdoutFd = AtomicInt(stdoutRead)
    private val stderrFd = AtomicInt(stderrRead)
    private val status = AtomicReference<Int?>(if (reaped) 0 else null)

    override val pid: Long get() = processId.toLong()

    override val hasStderr: Boolean get() = stderrFd.value >= 0

    override val isAlive: Boolean get() = !reap(block = false)

    override fun writeStdin(bytes: ByteArray): Boolean {
        val fd = stdinFd.value
        if (fd < 0) return false
        var done = 0
        while (done < bytes.size) {
            val wrote = bytes.usePinned { write(fd, it.addressOf(done), (bytes.size - done).convert()) }
            if (wrote < 0 && errno == EINTR) continue
            if (wrote <= 0) return false
            done += wrote.toInt()
        }
        return true
    }

    override fun closeStdin() {
        val fd = stdinFd.getAndSet(-1)
        if (fd >= 0) platform.posix.close(fd)
    }

    override fun readStdout(buffer: ByteArray): Int = readFrom(stdoutFd.value, buffer)

    override fun readStderr(buffer: ByteArray): Int = readFrom(stderrFd.value, buffer)

    private fun readFrom(fd: Int, buffer: ByteArray): Int {
        if (fd < 0 || buffer.isEmpty()) return -1
        while (true) {
            val count = buffer.usePinned { read(fd, it.addressOf(0), buffer.size.convert()) }
            if (count < 0 && errno == EINTR) continue
            return if (count <= 0) -1 else count.toInt()
        }
    }

    private fun reap(block: Boolean): Boolean {
        if (status.value != null) return true
        memScoped {
            val raw = alloc<IntVar>()
            while (true) {
                val result = waitpid(processId, raw.ptr, if (block) 0 else WNOHANG)
                if (result == processId) {
                    val value = raw.value
                    val code = if (value and 0x7f == 0) (value shr 8) and 0xff else 128 + (value and 0x7f)
                    status.compareAndSet(null, code)
                    return true
                }
                if (result == 0) return false
                if (errno == EINTR) continue
                while (status.value == null) usleep(1000u)
                return true
            }
        }
    }

    override fun waitFor(timeoutMs: Long): Boolean {
        if (timeoutMs < 0) return reap(block = true)
        val end = kotlin.time.TimeSource.Monotonic.markNow() + timeoutMs.milliseconds
        while (true) {
            if (reap(block = false)) return true
            if (end.hasPassedNow()) return false
            usleep(10_000u)
        }
    }

    override fun exitCode(): Int? = if (reap(block = false)) status.value else null

    override fun kill() {
        if (status.value != null) return
        platform.posix.kill(-processId, SIGKILL)
        platform.posix.kill(processId, SIGKILL)
    }

    override fun close() {
        closeStdin()
        stdoutFd.getAndSet(-1).let { if (it >= 0) platform.posix.close(it) }
        stderrFd.getAndSet(-1).let { if (it >= 0) platform.posix.close(it) }
    }
}
