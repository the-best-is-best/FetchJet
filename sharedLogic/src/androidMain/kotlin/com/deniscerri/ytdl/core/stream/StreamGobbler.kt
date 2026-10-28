/*
 * This file is derived from YTDLnis (https://github.com/deniscerri/ytdlnis),
 * Copyright (C) Denis Çerri and contributors, licensed under the GNU General Public License v3.0.
 * Modified for FetchJet (Copyright (C) 2026 Michelle Raouf). FetchJet as a whole is distributed
 * under the GNU General Public License v3.0 or later; see the LICENSE file in the repository root.
 */
package com.deniscerri.ytdl.core.stream

import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.nio.charset.StandardCharsets

internal class StreamGobbler(private val buffer: StringBuffer, private val stream: InputStream) :
    Thread() {
    init {
        start()
    }

    override fun run() {
        try {
            val `in`: Reader = InputStreamReader(stream, StandardCharsets.UTF_8)
            var nextChar: Int
            while (`in`.read().also { nextChar = it } != -1) {
                buffer.append(nextChar.toChar())
            }
        } catch (e: IOException) {
            Log.e(TAG, "failed to read stream", e)
        }
    }

    companion object {
        private val TAG = StreamGobbler::class.java.simpleName
    }
}