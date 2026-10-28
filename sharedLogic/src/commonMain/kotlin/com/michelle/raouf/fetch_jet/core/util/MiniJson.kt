package com.michelle.raouf.fetch_jet.core.util

class MiniJsonParser(private val json: String) {
    private var pos = 0
    private val len = json.length

    private fun skipWhitespace() {
        while (pos < len && json[pos].isWhitespace()) pos++
    }

    private fun parseString(): String {
        pos++ // skip opening quote
        val sb = StringBuilder()
        while (pos < len) {
            val c = json[pos++]
            if (c == '"') return sb.toString()
            if (c == '\\' && pos < len) {
                when (val esc = json[pos++]) {
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    '/' -> sb.append('/')
                    'b' -> sb.append('\b')
                    'f' -> sb.append('\u000c')
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    'u' -> {
                        if (pos + 4 <= len) {
                            val hex = json.substring(pos, pos + 4)
                            pos += 4
                            hex.toIntOrNull(16)?.let { sb.append(it.toChar()) }
                        }
                    }

                    else -> sb.append(esc)
                }
            } else {
                sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun parseNumberOrLiteral(): Any? {
        val start = pos
        while (pos < len && json[pos] !in ",:]}\" \t\r\n") pos++
        if (pos == start) {
            // Nothing consumed (e.g. "[:]" or "[}"): advancing avoids an endless loop in the
            // array/object parsers; the caller gets an error token instead.
            throw IllegalArgumentException("Unexpected character '${json[pos]}' at $pos")
        }
        val token = json.substring(start, pos).trim()
        if (token == "null") return null
        if (token == "true") return true
        if (token == "false") return false
        token.toLongOrNull()?.let { return it }
        token.toDoubleOrNull()?.let { return it }
        return token
    }

    fun parseValue(): Any? {
        skipWhitespace()
        if (pos >= len) return null
        return when (json[pos]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> parseString()
            else -> parseNumberOrLiteral()
        }
    }

    private fun parseArray(): List<Any?> {
        pos++ // skip '['
        val list = mutableListOf<Any?>()
        while (pos < len) {
            skipWhitespace()
            if (pos < len && json[pos] == ']') {
                pos++
                return list
            }
            val value = parseValue()
            list.add(value)
            skipWhitespace()
            if (pos < len && json[pos] == ',') {
                pos++
            } else if (pos < len && json[pos] == ']') {
                pos++
                return list
            }
        }
        return list
    }

    private fun parseObject(): Map<String, Any?> {
        pos++ // skip '{'
        val map = mutableMapOf<String, Any?>()
        while (pos < len) {
            skipWhitespace()
            if (pos < len && json[pos] == '}') {
                pos++
                return map
            }
            if (pos < len && json[pos] == '"') {
                val key = parseString()
                skipWhitespace()
                if (pos < len && json[pos] == ':') pos++
                val value = parseValue()
                map[key] = value
            } else {
                pos++
            }
            skipWhitespace()
            if (pos < len && json[pos] == ',') {
                pos++
            } else if (pos < len && json[pos] == '}') {
                pos++
                return map
            }
        }
        return map
    }
}

object MiniJson {
    fun parse(json: String): Any? = MiniJsonParser(json).parseValue()
}

fun Map<String, Any?>.getString(key: String): String? = this[key] as? String
fun Map<String, Any?>.getInt(key: String): Int? = (this[key] as? Number)?.toInt()
fun Map<String, Any?>.getLong(key: String): Long? = (this[key] as? Number)?.toLong()
fun Map<String, Any?>.getDouble(key: String): Double? = (this[key] as? Number)?.toDouble()

@Suppress("UNCHECKED_CAST")
fun Map<String, Any?>.getObject(key: String): Map<String, Any?>? = this[key] as? Map<String, Any?>

@Suppress("UNCHECKED_CAST")
fun Map<String, Any?>.getArray(key: String): List<Any?>? = this[key] as? List<Any?>

@Suppress("UNCHECKED_CAST")
fun Map<String, Any?>.getObjectArray(key: String): List<Map<String, Any?>> {
    val arr = this[key] as? List<*> ?: return emptyList()
    return arr.filterIsInstance<Map<String, Any?>>()
}