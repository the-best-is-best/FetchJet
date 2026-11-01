package com.michelle.raouf.fetch_jet.core.util

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

object MiniJson {
    private val json = Json { isLenient = true }

    fun parse(text: String): Any? = try {
        json.parseToJsonElement(text).toPlain()
    } catch (e: SerializationException) {
        val firstValue = text.lineSequence().map { it.trim() }
            .firstOrNull { it.startsWith("{") || it.startsWith("[") }
            ?.takeIf { it != text.trim() } ?: throw e
        json.parseToJsonElement(firstValue).toPlain()
    }

    private fun JsonElement.toPlain(): Any? = when (this) {
        is JsonNull -> null
        is JsonObject -> mapValues { it.value.toPlain() }
        is JsonArray -> map { it.toPlain() }
        is JsonPrimitive -> when {
            isString -> content
            else -> booleanOrNull ?: longOrNull ?: doubleOrNull ?: content
        }
    }
}

fun Map<String, Any?>.getString(key: String): String? = this[key] as? String
fun Map<String, Any?>.getInt(key: String): Int? = (this[key] as? Number)?.toInt()
fun Map<String, Any?>.getLong(key: String): Long? = (this[key] as? Number)?.toLong()
fun Map<String, Any?>.getDouble(key: String): Double? = (this[key] as? Number)?.toDouble()

@Suppress("UNCHECKED_CAST")
fun Map<String, Any?>.getArray(key: String): List<Any?>? = this[key] as? List<Any?>

@Suppress("UNCHECKED_CAST")
fun Map<String, Any?>.getObjectArray(key: String): List<Map<String, Any?>> {
    val arr = this[key] as? List<*> ?: return emptyList()
    return arr.filterIsInstance<Map<String, Any?>>()
}