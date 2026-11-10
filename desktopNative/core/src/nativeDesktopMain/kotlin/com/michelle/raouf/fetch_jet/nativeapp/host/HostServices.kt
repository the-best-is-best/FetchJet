@file:OptIn(ExperimentalForeignApi::class)

package com.michelle.raouf.fetch_jet.nativeapp.host

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cstr
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject

typealias HostServiceFn = CPointer<CFunction<(CPointer<ByteVar>?, CPointer<ByteVar>?) -> CPointer<ByteVar>?>>
typealias HostFreeFn = CPointer<CFunction<(CPointer<ByteVar>?) -> Unit>>

internal class HostServices(private val fn: HostServiceFn, private val free: HostFreeFn?) {

    fun call(name: String, args: String = "{}"): String = memScoped {
        val result = fn.invoke(name.cstr.ptr, args.cstr.ptr) ?: return@memScoped ""
        val text = result.toKString()
        free?.invoke(result)
        text
    }

    fun call(name: String, args: JsonObjectBuilder.() -> Unit): String =
        call(name, buildJsonObject(args).toString())
}
