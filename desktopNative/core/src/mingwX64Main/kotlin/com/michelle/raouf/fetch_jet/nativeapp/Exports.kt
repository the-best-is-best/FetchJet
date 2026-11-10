@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)

package com.michelle.raouf.fetch_jet.nativeapp

import com.michelle.raouf.fetch_jet.nativeapp.host.HostFreeFn
import com.michelle.raouf.fetch_jet.nativeapp.host.HostServiceFn
import com.michelle.raouf.fetch_jet.nativeapp.runtime.HostNotifyFn
import com.michelle.raouf.fetch_jet.nativeapp.runtime.HostPostFn
import com.michelle.raouf.fetch_jet.nativeapp.runtime.freeMallocCString
import com.michelle.raouf.fetch_jet.nativeapp.runtime.guarded
import com.michelle.raouf.fetch_jet.nativeapp.runtime.toKStringOrEmpty
import com.michelle.raouf.fetch_jet.nativeapp.runtime.toMallocCString
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.CName
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi

@CName("fj_start")
fun fjStart(
    config: CPointer<ByteVar>?,
    post: HostPostFn?,
    notify: HostNotifyFn?,
    host: HostServiceFn?,
    hostFree: HostFreeFn?,
): Int = guarded("fj_start", 1) {
    if (post == null || notify == null) 3 else NativeApp.start(config.toKStringOrEmpty(), post, notify, host, hostFree)
}

@CName("fj_prepare")
fun fjPrepare(config: CPointer<ByteVar>?): CPointer<ByteVar>? =
    guarded<CPointer<ByteVar>?>("fj_prepare", null) { NativeApp.prepare(config.toKStringOrEmpty()).toMallocCString() }

@CName("fj_snapshot")
fun fjSnapshot(channel: Int): CPointer<ByteVar>? =
    guarded<CPointer<ByteVar>?>("fj_snapshot", null) { NativeApp.snapshot(channel).toMallocCString() }

@CName("fj_action")
fun fjAction(action: CPointer<ByteVar>?) {
    guarded("fj_action", Unit) { NativeApp.action(action.toKStringOrEmpty()) }
}

@CName("fj_call")
fun fjCall(name: CPointer<ByteVar>?, args: CPointer<ByteVar>?): CPointer<ByteVar>? =
    guarded<CPointer<ByteVar>?>("fj_call", null) {
        NativeApp.call(name.toKStringOrEmpty(), args.toKStringOrEmpty()).toMallocCString()
    }

@CName("fj_free")
fun fjFree(pointer: CPointer<ByteVar>?) {
    freeMallocCString(pointer)
}

@CName("fj_shutdown")
fun fjShutdown() {
    guarded("fj_shutdown", Unit) { NativeApp.shutdown() }
}
