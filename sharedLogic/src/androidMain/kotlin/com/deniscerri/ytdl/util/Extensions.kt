/*
 * This file is derived from YTDLnis (https://github.com/deniscerri/ytdlnis),
 * Copyright (C) Denis Çerri and contributors, licensed under the GNU General Public License v3.0.
 * Modified for FetchJet (Copyright (C) 2026 Michelle Raouf). FetchJet as a whole is distributed
 * under the GNU General Public License v3.0 or later; see the LICENSE file in the repository root.
 */
package com.deniscerri.ytdl.util

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.regex.Pattern

object Extensions {
    fun JSONObject.getIntByAny(vararg tags: String): Int {
        tags.forEach {
            runCatching {
                return this.getInt(it)
            }
        }
        return -1
    }

    fun JSONObject.getStringByAny(vararg tags: String): String {
        tags.forEach {
            runCatching {
                val tmp = this.getString(it)
                if (tmp != "null" && tmp.isNotBlank()) {
                    return tmp
                }
            }
        }
        return ""
    }

    fun Int.toStringDuration(locale: Locale): String {
        var format = String.format(
            locale,
            "%02d:%02d:%02d",
            this / 3600,
            this % 3600 / 60,
            this % 60
        )
        if (this < 600) format = format.substring(4)
        else if (this < 3600) format = format.substring(3)
        else if (this < 36000) format = format.substring(1)
        return format
    }

    fun String.isYoutubeURL(): Boolean {
        return Pattern.compile("((^(https?)://)?(www.)?(m.)?youtu(.be)?)|(^(https?)://(www.)?piped.video)")
            .matcher(this).find()
    }

    fun String.isYoutubeChannelURL(): Boolean {
        return Pattern.compile("((^(https?)://)?(www.)?(m.)?youtu(.be)?(be.com))/@[a-zA-Z]+")
            .matcher(this).find()
    }

    fun String.isYoutubeWatchVideosURL(): Boolean {
        return Pattern.compile("((^(https?)://)?(www.)?(m.)?youtu(.be)?(be.com))/watch_videos\\?video_ids=.*")
            .matcher(this).find()
    }

    fun String.isSoundCloudURL(): Boolean {
        return Pattern.compile("""^(https?://)?(www\.|m\.)?soundcloud\.com/[\w\-.]+(/[\w\-.]+)*/?$""")
            .matcher(this).find()
    }

    fun String.extractURL(): String {
        val res =
            Pattern.compile("(http|ftp|https)://([\\w_-]+(?:\\.[\\w_-]+)+)([\\w.,@?^=%&:/~+#-]*[\\w@?^=%&/~+#-])")
                .matcher(this)
        return if (res.find()) {
            res.group()
        } else {
            this
        }
    }

    fun String.isURL(): Boolean {
        return Pattern.compile("(http|ftp|https)://([\\w_-]+(?:\\.[\\w_-]+)+)([\\w.,@?^=%&:/~+#-]*[\\w@?^=%&/~+#-])")
            .matcher(this).find()
    }

    fun readJsonValue(reader: JsonReader, skipKeys: Set<String> = setOf()): Any {
        return when (reader.peek()) {
            JsonToken.BEGIN_ARRAY -> {
                val array = JSONArray()
                reader.beginArray()
                while (reader.hasNext()) {
                    array.put(readJsonValue(reader))
                }
                reader.endArray()
                array
            }

            JsonToken.BEGIN_OBJECT -> {
                val obj = JSONObject()
                reader.beginObject()
                while (reader.hasNext()) {
                    val name = reader.nextName()
                    if (name in skipKeys) {
                        reader.skipValue()
                        continue
                    }
                    obj.put(name, readJsonValue(reader))
                }
                reader.endObject()
                obj
            }

            JsonToken.STRING -> reader.nextString()
            JsonToken.NUMBER -> {
                val raw = reader.nextString()
                raw.toLongOrNull() ?: raw.toDoubleOrNull() ?: raw
            }

            JsonToken.BOOLEAN -> reader.nextBoolean()
            JsonToken.NULL -> {
                reader.nextNull()
                JSONObject.NULL
            }

            else -> {
                reader.skipValue()
                JSONObject.NULL
            }
        }
    }
}