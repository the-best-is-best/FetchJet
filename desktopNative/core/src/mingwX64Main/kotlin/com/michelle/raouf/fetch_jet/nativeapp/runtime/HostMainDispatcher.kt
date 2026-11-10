@file:OptIn(ExperimentalForeignApi::class)

package com.michelle.raouf.fetch_jet.nativeapp.runtime

import kotlin.concurrent.AtomicInt
import kotlin.concurrent.AtomicLong
import kotlin.coroutines.CoroutineContext
import kotlin.time.TimeSource
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.invoke
import kotlinx.cinterop.staticCFunction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import platform.windows.GetCurrentThreadId

typealias HostTaskFn = CPointer<CFunction<(COpaquePointer?) -> Unit>>
typealias HostPostFn = CPointer<CFunction<(HostTaskFn?, COpaquePointer?) -> Unit>>

class HostMainDispatcher private constructor(
    private val core: Core,
    private val isImmediate: Boolean,
) : MainCoroutineDispatcher() {

    constructor(post: HostPostFn, uiThreadId: UInt = GetCurrentThreadId()) : this(Core(post, uiThreadId), false)

    private val immediateDispatcher: HostMainDispatcher =
        if (isImmediate) this else HostMainDispatcher(core, true)

    override val immediate: MainCoroutineDispatcher get() = immediateDispatcher

    val uiThreadId: UInt get() = core.uiThreadId
    val hostPosts: Long get() = core.hostPosts.value
    val tasksRun: Long get() = core.tasksRun.value
    val isClosed: Boolean get() = core.closed.value != 0

    fun isUiThread(): Boolean = GetCurrentThreadId() == core.uiThreadId

    override fun isDispatchNeeded(context: CoroutineContext): Boolean = !isImmediate || !isUiThread()

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        if (!core.enqueue(block)) {
            context.cancel(CancellationException("The UI thread is gone: $this is closed"))
            Dispatchers.Default.dispatch(context, block)
        }
    }

    fun close() {
        core.close()
    }

    fun drainNow(): Int = core.drain(Int.MAX_VALUE, Long.MAX_VALUE)

    override fun toString(): String = if (isImmediate) "HostMain.immediate" else "HostMain"

    internal class Core(val post: HostPostFn, val uiThreadId: UInt) {
        val closed = AtomicInt(0)
        val hostPosts = AtomicLong(0)
        val tasksRun = AtomicLong(0)
        private val queue = Channel<Runnable>(Channel.UNLIMITED)
        private val wakePending = AtomicInt(0)
        private val self = StableRef.create(this)

        fun enqueue(block: Runnable): Boolean {
            if (closed.value != 0) return false
            if (!queue.trySend(block).isSuccess) return false
            wake()
            return true
        }

        private fun wake() {
            if (wakePending.compareAndSet(0, 1)) {
                hostPosts.incrementAndGet()
                post.invoke(drainEntry, self.asCPointer())
            }
        }

        fun drain(maxTasks: Int, maxMicros: Long): Int {
            wakePending.value = 0
            val mark = TimeSource.Monotonic.markNow()
            var count = 0
            while (true) {
                val block = queue.tryReceive().getOrNull() ?: break
                count++
                tasksRun.incrementAndGet()
                try {
                    block.run()
                } catch (e: Throwable) {
                    CrashLog.report("Main dispatcher task", e)
                }
                if (count >= maxTasks || (count and 31 == 0 && mark.elapsedNow().inWholeMicroseconds >= maxMicros)) {
                    wake()
                    break
                }
            }
            return count
        }

        fun close() {
            closed.value = 1
        }
    }

    companion object {
        const val DRAIN_MAX_TASKS = 4096
        const val DRAIN_MAX_MICROS = 8000L
    }
}

private val drainEntry: HostTaskFn = staticCFunction { arg: COpaquePointer? ->
    val core = arg?.asStableRef<HostMainDispatcher.Core>()?.get()
    if (core != null) {
        try {
            core.drain(HostMainDispatcher.DRAIN_MAX_TASKS, HostMainDispatcher.DRAIN_MAX_MICROS)
        } catch (e: Throwable) {
            CrashLog.report("Main dispatcher drain", e)
        }
    }
}
