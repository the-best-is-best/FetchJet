@file:OptIn(ExperimentalForeignApi::class)

package com.michelle.raouf.fetch_jet.nativeapp.runtime

import kotlin.concurrent.AtomicInt
import kotlin.concurrent.AtomicLong
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.invoke
import kotlinx.cinterop.staticCFunction

typealias HostNotifyFn = CPointer<CFunction<(UInt) -> Unit>>

class ChangeNotifier(private val post: HostPostFn, private val notify: HostNotifyFn) {
    private val pending = AtomicInt(0)
    private val stopped = AtomicInt(0)
    private val self = StableRef.create(this)
    val marks = AtomicLong(0)
    val posts = AtomicLong(0)
    val deliveries = AtomicLong(0)

    fun mark(channel: Int) {
        marks.incrementAndGet()
        val bit = 1 shl channel
        while (true) {
            val old = pending.value
            if (old and bit != 0) return
            if (pending.compareAndSet(old, old or bit)) {
                if (old == 0 && stopped.value == 0) {
                    posts.incrementAndGet()
                    post.invoke(deliverEntry, self.asCPointer())
                }
                return
            }
        }
    }

    fun deliver() {
        val mask = pending.getAndSet(0)
        if (mask != 0 && stopped.value == 0) {
            deliveries.incrementAndGet()
            notify.invoke(mask.toUInt())
        }
    }

    fun stop() {
        stopped.value = 1
    }
}

private val deliverEntry: HostTaskFn = staticCFunction { arg: COpaquePointer? ->
    val notifier = arg?.asStableRef<ChangeNotifier>()?.get()
    if (notifier != null) guarded("ChangeNotifier.deliver", Unit) { notifier.deliver() }
}
