@file:OptIn(NativeRuntimeApi::class, ExperimentalStdlibApi::class)

package com.michelle.raouf.fetch_jet.nativeapp.runtime

import com.michelle.raouf.fetch_jet.core.network.DownloadBody
import com.michelle.raouf.fetch_jet.platform.desktop.DesktopOs
import kotlin.native.runtime.GC
import kotlin.native.runtime.NativeRuntimeApi
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.put

internal object MemoryTuning {
    private const val MB = 1024L * 1024L

    fun apply() {
        GC.targetHeapUtilization = 0.8
        DownloadBody.direct = DesktopOs.getEnv("FETCHJET_BODY_COPY") != "1"
        number("FETCHJET_GC_MIN_MB")?.let { GC.minHeapBytes = (it * MB).toLong() }
        number("FETCHJET_GC_MAX_MB")?.let { GC.maxHeapBytes = (it * MB).toLong() }
        number("FETCHJET_GC_UTIL")?.let { GC.targetHeapUtilization = it }
        number("FETCHJET_GC_TRIGGER")?.let { GC.heapTriggerCoefficient = it }
        number("FETCHJET_GC_INTERVAL_MS")?.let { GC.regularGCInterval = it.toLong().milliseconds }
        number("FETCHJET_GC_TARGET_MB")?.let {
            GC.autotune = false
            GC.targetHeapBytes = (it * MB).toLong()
        }
        println(
            "gc: target=${GC.targetHeapBytes / 1024} KB min=${GC.minHeapBytes / 1024} KB max=${GC.maxHeapBytes / MB} MB " +
                "util=${GC.targetHeapUtilization} trigger=${GC.heapTriggerCoefficient} " +
                "interval=${GC.regularGCInterval.inWholeMilliseconds} ms autotune=${GC.autotune}"
        )
    }

    fun collect() {
        GC.collect()
    }

    fun describe(json: JsonObjectBuilder) {
        json.put("gcTarget", GC.targetHeapBytes)
        val last = GC.lastGCInfo ?: return
        json.put("gcEpoch", last.epoch)
        json.put("gcBefore", last.memoryUsageBefore.values.sumOf { it.totalObjectsSizeBytes })
        json.put("gcAfter", last.memoryUsageAfter.values.sumOf { it.totalObjectsSizeBytes })
    }

    private fun number(name: String): Double? = DesktopOs.getEnv(name)?.trim()?.toDoubleOrNull()
}
