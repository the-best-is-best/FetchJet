package com.michelle.raouf.fetch_jet.nativeapp.channels

import com.michelle.raouf.fetch_jet.nativeapp.runtime.ChangeNotifier
import kotlin.concurrent.AtomicInt
import kotlin.concurrent.AtomicReference
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

internal val UiJson = Json {
    encodeDefaults = false
    explicitNulls = false
    ignoreUnknownKeys = true
}

internal interface UiChannel {
    val id: Int
    val name: String
    fun snapshot(full: Boolean): String
}

internal class StructChannel<T>(
    override val id: Int,
    override val name: String,
    private val serializer: KSerializer<T>,
    private val notifier: ChangeNotifier,
) : UiChannel {
    private val last = AtomicReference<T?>(null)
    private val json = AtomicReference("{}")

    fun publish(value: T) {
        if (last.value == value) return
        val text = UiJson.encodeToString(serializer, value)
        last.value = value
        json.value = text
        notifier.mark(id)
    }

    val current: T? get() = last.value

    override fun snapshot(full: Boolean): String = json.value
}

internal class ListChannel<T>(
    override val id: Int,
    override val name: String,
    private val serializer: KSerializer<T>,
    private val notifier: ChangeNotifier,
) : UiChannel {
    private val pending = AtomicReference<List<T>>(emptyList())
    private val busy = AtomicInt(0)
    private var delivered: List<T> = emptyList()

    fun publish(rows: List<T>) {
        if (pending.value == rows) return
        pending.value = rows
        notifier.mark(id)
    }

    val current: List<T> get() = pending.value

    override fun snapshot(full: Boolean): String {
        while (!busy.compareAndSet(0, 1)) {
        }
        try {
            val target = pending.value
            val from = if (full) emptyList() else delivered
            val out = StringBuilder(64 + target.size * 8)
            out.append("{\"n\":").append(target.size).append(",\"at\":[")
            val rows = StringBuilder()
            var first = true
            for (index in target.indices) {
                val row = target[index]
                if (index < from.size) {
                    val old = from[index]
                    if (old === row || old == row) continue
                }
                if (!first) {
                    out.append(',')
                    rows.append(',')
                }
                first = false
                out.append(index)
                rows.append(UiJson.encodeToString(serializer, row))
            }
            out.append("],\"rows\":[").append(rows).append("]}")
            if (!full) delivered = target
            return out.toString()
        } finally {
            busy.value = 0
        }
    }
}

internal class QueueChannel<T>(
    override val id: Int,
    override val name: String,
    private val serializer: KSerializer<T>,
    private val notifier: ChangeNotifier,
) : UiChannel {
    private val items = AtomicReference<List<T>>(emptyList())

    fun push(item: T) {
        while (true) {
            val old = items.value
            if (items.compareAndSet(old, old + item)) break
        }
        notifier.mark(id)
    }

    override fun snapshot(full: Boolean): String {
        val taken = items.getAndSet(emptyList())
        return taken.joinToString(",", "[", "]") { UiJson.encodeToString(serializer, it) }
    }
}
