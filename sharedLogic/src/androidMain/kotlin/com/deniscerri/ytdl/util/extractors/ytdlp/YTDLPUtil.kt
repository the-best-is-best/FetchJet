package com.deniscerri.ytdl.util.extractors.ytdlp

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.util.Patterns
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.core.RuntimeManager
import com.deniscerri.ytdl.core.models.YTDLRequest
import com.deniscerri.ytdl.database.models.ChapterItem
import com.deniscerri.ytdl.database.models.Format
import com.deniscerri.ytdl.database.models.ResultItem
import com.deniscerri.ytdl.util.Extensions.getIntByAny
import com.deniscerri.ytdl.util.Extensions.getStringByAny
import com.deniscerri.ytdl.util.Extensions.isSoundCloudURL
import com.deniscerri.ytdl.util.Extensions.isURL
import com.deniscerri.ytdl.util.Extensions.isYoutubeURL
import com.deniscerri.ytdl.util.Extensions.isYoutubeWatchVideosURL
import com.deniscerri.ytdl.util.Extensions.readJsonValue
import com.deniscerri.ytdl.util.Extensions.toStringDuration
import com.deniscerri.ytdl.util.FileUtil
import com.google.gson.Gson
import com.google.gson.Strictness
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStreamReader
import java.lang.reflect.Type
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.CRC32
import kotlin.coroutines.cancellation.CancellationException

class YTDLPUtil(private val context: Context) {
    private var sharedPreferences: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)

    fun YTDLRequest.applyDefaultOptionsForFetchingData(url: String?) {
        addOption("--skip-download")
        addOption("--quiet")
        addOption("--ignore-errors")
        addOption("--no-warnings")
        addOption("-R", "1")

        val compatOptions = mutableListOf("manifest-filesize-approx")
        if (url?.isYoutubeURL() == true) {
            compatOptions.add("no-youtube-unavailable-videos")
        }
        addOption("--compat-options", compatOptions.joinToString(","))

        val socketTimeout = sharedPreferences.getString("socket_timeout", "10")!!.ifEmpty { "10" }
        addOption("--socket-timeout", socketTimeout)

        if (sharedPreferences.getBoolean("force_ipv4", false)) {
            addOption("-4")
        }

        FileUtil.getCookieFile(this@YTDLPUtil.context) {
            addOption("--cookies", it)
        }
        val useHeader = sharedPreferences.getBoolean("use_header", false)
        val header = sharedPreferences.getString("useragent_header", "")
        if (useHeader && !header.isNullOrBlank()) {
            addOption("--add-header", "User-Agent:${header}")
        }

        val proxy = sharedPreferences.getString("proxy", "")
        if (!proxy.isNullOrBlank()) {
            addOption("--proxy", proxy)
        }
        addOption("-P", FileUtil.getCachePath(this@YTDLPUtil.context) + "/tmp")
    }

    private fun canApplyFlatPlaylist(url: String): Boolean {
        var noFlatPlaylist = sharedPreferences.getBoolean("no_flat_playlist", false)
        if (url.isSoundCloudURL()) {
            noFlatPlaylist = true
        }
        // Generic URLs often fail extraction if --flat-playlist is applied
        if (!url.isYoutubeURL() && !url.isSoundCloudURL()) {
            noFlatPlaylist = true
        }
        return !noFlatPlaylist
    }

    @SuppressLint("RestrictedApi")
    suspend fun getFromYTDL(
        query: String,
        singleItem: Boolean = false,
        resultsGenerated: suspend (pagedResults: List<ResultItem>) -> Unit
    ): List<ResultItem> {
        val searchEngine = sharedPreferences.getString("search_engine", "ytsearch") ?: "ytsearch"

        val request: YTDLRequest
        if (query.contains("http")) {
            if (query.isYoutubeWatchVideosURL()) {
                request = YTDLRequest(emptyList())
                val config =
                    File(context.cacheDir.absolutePath + "/config" + System.currentTimeMillis() + "##url.txt")
                config.writeText(query)
                request.addOption("--config", config.absolutePath)
            } else {
                request = YTDLRequest(query)
            }
            request.addWriteInfoJson(query)
        } else {
            request = YTDLRequest(emptyList())
            when (searchEngine) {
                "ytsearchmusic" -> {
                    request.addOption("--default-search", "https://music.youtube.com/search?q=")
                    request.addOption("ytsearch25:\"${query}\"")
                }

                else -> {
                    request.addOption("${searchEngine}25:\"${query}\"")
                }
            }
        }

        if (searchEngine == "ytsearch" || query.isYoutubeURL()) {
            request.setYoutubeExtractorArgs(query)
        }

        if (canApplyFlatPlaylist(query)) {
            request.addOption("--flat-playlist")
        }

        request.addOption("--lazy-playlist")
        request.addOption(if (singleItem) "-J" else "-j")
        request.applyDefaultOptionsForFetchingData(if (query.isURL()) query else null)
        val usePlaylistOriginalURL =
            sharedPreferences.getBoolean("use_original_url_playlist", false)

        val finalResults = mutableListOf<ResultItem>()
        var postedProgress = false
        val callerJob = currentCoroutineContext().job

        RuntimeManager.getInstance().runKillable { processId ->
            RuntimeManager.getInstance().execute(request, processId) { progress, _, line ->
                if (!callerJob.isActive) return@execute
                runCatching {
                    val generatedResults = parseYTDLPListResults(listOf(line), query)
                    if (generatedResults.isNotEmpty()) {
                        generatedResults.forEach {
                            if (!it.playlistURL.isNullOrBlank() && usePlaylistOriginalURL && query.contains(
                                    "http"
                                )
                            ) {
                                it.playlistURL = query
                            }
                        }
                        finalResults.addAll(generatedResults)
                        if (!postedProgress) {
                            if (finalResults.size > 1) {
                                runBlocking {
                                    resultsGenerated(finalResults)
                                }
                                postedProgress = true
                            }
                        } else {
                            runBlocking {
                                resultsGenerated(generatedResults)
                            }
                        }
                    }
                }
            }
        }

        currentCoroutineContext().ensureActive()
        if (!postedProgress) resultsGenerated(finalResults)
        return finalResults
    }

    fun parseYTDLPListResults(results: List<String?>, query: String = ""): ArrayList<ResultItem> {
        val items = arrayListOf<ResultItem>()

        for (result in results) {
            if (result.isNullOrBlank()) continue
            val jsonObject = JSONObject(result)
            var title = jsonObject.getStringByAny("alt_title", "title", "webpage_url_basename")
            if (title == "[Private video]" || title == "[Deleted video]") continue

            var playlistTitle = jsonObject.getStringByAny("playlist_title")
            var playlistURL: String? = ""
            var playlistIndex: Int? = null

            if (playlistTitle.removeSurrounding("\"") == query) playlistTitle = ""

            if (playlistTitle.isNotBlank()) {
                playlistURL = jsonObject.getStringByAny("playlist_webpage_url").ifEmpty { query }
                runCatching { playlistIndex = jsonObject.getInt("playlist_index") }
            }
            val url = if (jsonObject.has("url") && results.size > 1) {
                jsonObject.getString("url")
            } else {
                if (Patterns.WEB_URL.matcher(query).matches() && playlistURL?.isEmpty() == true) {
                    query
                } else {
                    jsonObject.getStringByAny("webpage_url", "original_url", "url")
                }
            }

            val authorTags =
                mutableListOf("uploader", "channel", "playlist_uploader", "uploader_id")
            if (url.isYoutubeURL()) {
                authorTags.addAll(0, listOf("artists", "artist", "creators"))
            }
            var author = jsonObject.getStringByAny(*authorTags.map { it }.toTypedArray())
                .removeSuffix(" - Topic")
            var duration = jsonObject.getIntByAny("duration").toString()
            if (duration != "-1") {
                duration = jsonObject.getInt("duration").toStringDuration(Locale.US)
            }

            var thumb: String? = ""
            if (jsonObject.has("thumbnail")) {
                thumb = jsonObject.getString("thumbnail")
            } else if (jsonObject.has("thumbnails")) {
                val thumbs = jsonObject.getJSONArray("thumbnails")
                if (thumbs.length() > 0) {
                    thumb = thumbs.getJSONObject(thumbs.length() - 1).getString("url")
                }
            }

            if (author.isEmpty()) {
                runCatching {
                    val firstEntry = jsonObject.getJSONArray("entries").getJSONObject(0)
                    author = firstEntry.getStringByAny(
                        "uploader",
                        "channel",
                        "playlist_uploader",
                        "uploader_id"
                    )
                    val thumbs = firstEntry.getJSONArray("thumbnails")
                    if (thumbs.length() > 0) {
                        thumb = thumbs.getJSONObject(thumbs.length() - 1).getString("url")
                    }
                }
            }

            var website = jsonObject.getStringByAny("extractor_key", "extractor", "ie_key")
            if (website == "Generic" || website == "HTML5MediaEmbed") website =
                jsonObject.getStringByAny("webpage_url_domain")

            val formatsInJSON =
                if (jsonObject.has("formats") && jsonObject.get("formats") is JSONArray) jsonObject.getJSONArray(
                    "formats"
                ) else null
            val formats: ArrayList<Format> = parseYTDLFormats(formatsInJSON)

            val chaptersInJSON =
                if (jsonObject.has("chapters") && jsonObject.get("chapters") is JSONArray) jsonObject.getJSONArray(
                    "chapters"
                ) else null
            val listType: Type = object : TypeToken<List<ChapterItem>>() {}.type
            var chapters: ArrayList<ChapterItem> = arrayListOf()

            if (chaptersInJSON != null) {
                chapters = Gson().fromJson(chaptersInJSON.toString(), listType)
            }

            var urls = ""
            if (jsonObject.has("requested_formats")) {
                val requestedFormats = jsonObject.getJSONArray("requested_formats")
                val urlList = mutableListOf<String>()
                val length = requestedFormats.length() - 1
                for (i in length downTo 0) {
                    urlList.add(requestedFormats.getJSONObject(i).getString("url"))
                }
                urls = urlList.joinToString("\n")
            }

            val type = jsonObject.getStringByAny("_type")
            if (type == "playlist") {
                if (playlistTitle.isEmpty()) {
                    playlistTitle = title
                }
                title = ""
                author = ""
            }

            val availableSubtitles = mutableListOf<String>()
            if (jsonObject.has("subtitles")) {
                runCatching {
                    availableSubtitles.addAll(
                        jsonObject.getJSONObject("subtitles").keys().asSequence().toList()
                    )
                }
            }

            val res = ResultItem(
                id = 0,
                url = url,
                title = title,
                author = author,
                duration = duration,
                thumb = thumb ?: "",
                website = website,
                playlistTitle = playlistTitle,
                formats = formats,
                urls = urls,
                chapters = chapters,
                playlistURL = playlistURL,
                playlistIndex = playlistIndex,
                availableSubtitles = availableSubtitles
            )

            items.add(res)
        }

        return items
    }

    suspend fun getFormats(url: String, ignoreInfoJSON: Boolean = false): List<Format> {
        val request = YTDLRequest(url)
        request.addOption("--print", "%(formats)j")
        request.addOption("--print", "%(duration)s")
        request.applyDefaultOptionsForFetchingData(url)
        if (url.isYoutubeURL()) {
            request.setYoutubeExtractorArgs(url)
        }

        val canUseWriteInfoJson =
            !ignoreInfoJSON &&
                    !sharedPreferences.getBoolean("disable_write_info_json", false) &&
                    !request.toString().contains("--download-sections")

        if (canUseWriteInfoJson) {
            val infoJsonFile = getInfoJsonFile(url)
            if (infoJsonFile != null && System.currentTimeMillis() - infoJsonFile.lastModified() <= (1000 * 60 * 60 * 5)) {
                request.addOption("--load-info-json", infoJsonFile.absolutePath)
            } else {
                request.addWriteInfoJson(url)
            }
        }

        val formats = try {
            RuntimeManager.getInstance().runKillable { processId ->
                RuntimeManager.getInstance().executeStreaming(request, processId) { stream ->
                    JsonReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader ->
                        reader.strictness = Strictness.LENIENT
                        readFormatsList(reader)
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            arrayListOf()
        }

        if (formats.isEmpty()) {
            runCatching {
                getInfoJsonFile(url)?.apply { this.delete() }
            }
        }

        return formats
    }

    fun readFormatsList(reader: JsonReader): ArrayList<Format> {
        val formats = arrayListOf<Format>()
        val seenFormatIds = HashSet<String>()

        reader.beginArray()
        while (reader.hasNext()) {
            val obj = readFormatObject(reader)
            val id = obj.optString("format_id").ifBlank { obj.optString("itag") }
            if (id.isNotBlank() && !seenFormatIds.add(id)) {
                continue
            }
            val formatProper = parseOneFormat(obj) ?: continue
            formats.add(formatProper)
        }
        reader.endArray()

        formats.reverse()
        return formats
    }

    private fun readFormatObject(reader: JsonReader): JSONObject {
        val obj = JSONObject()
        reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName()
            obj.put(name, readJsonValue(reader))
        }
        reader.endObject()
        return obj
    }

    private fun parseOneFormat(format: JSONObject): Format? {
        runCatching {
            if (format.get("filesize").toString() == "None") format.remove("filesize")
        }
        runCatching {
            if (format.get("filesize_approx").toString() == "None") format.remove("filesize_approx")
        }

        val exactSize = format.optLong("filesize").takeIf { it > 0L }
        val reportedApprox = format.optLong("filesize_approx").takeIf { it > 0L }

        runCatching {
            if (format.get("format_note").toString() == "null") format.remove("format_note")
        }

        val formatProper = Gson().fromJson(format.toString(), Format::class.java)
        if (formatProper.format_note == null) formatProper.format_note = ""

        formatProper.filesize = exactSize ?: reportedApprox ?: 0
        formatProper.isFilesizeApprox = exactSize == null

        val resolution = format.optString("resolution")
        if (format.has("format_note")) {
            if (!formatProper.format_note.contains("audio only", true)) {
                formatProper.format_note = format.getString("format_note")
            } else {
                if (!formatProper.format_note.endsWith("audio", true)) {
                    formatProper.format_note =
                        format.getString("format_note").uppercase().removeSuffix("AUDIO")
                            .trim() + " AUDIO"
                }
            }
            if (!resolution.isNullOrBlank() && resolution != "audio only") {
                formatProper.format_note = "${formatProper.format_note} (${resolution})"
            }
        }

        if (formatProper.format_note.contains("storyboard", ignoreCase = true)) return null

        formatProper.format_note = formatProper.format_note.trim()
        formatProper.container = format.optString("ext")
        if (formatProper.tbr == "None") formatProper.tbr = ""
        if (!formatProper.tbr.isNullOrBlank()) {
            formatProper.tbr += "k"
        }

        if (formatProper.vcodec.isNullOrEmpty() || formatProper.vcodec == "null") {
            if (formatProper.acodec.isNullOrEmpty() || formatProper.acodec == "null") {
                formatProper.vcodec =
                    format.getStringByAny("video_ext", "ext").ifEmpty { "unknown" }
            }
        }

        return formatProper
    }

    fun parseYTDLFormats(formatsInJSON: JSONArray?): ArrayList<Format> {
        val formats = arrayListOf<Format>()
        if (formatsInJSON != null) {
            for (f in formatsInJSON.length() - 1 downTo 0) {
                val formatRaw = formatsInJSON.getJSONObject(f)
                val format = parseOneFormat(formatRaw) ?: continue
                formats.add(format)
            }
        }
        return formats
    }

    suspend fun getStreamingUrlAndChapters(url: String): Result<Pair<List<String>, List<ChapterItem>?>> {
        return try {
            val request = YTDLRequest(url)
            request.addOption("--print", "%(.{urls,chapters})s")
            request.addOption("-S", "res:720,+proto:m3u8")
            request.applyDefaultOptionsForFetchingData(url)
            if (url.isYoutubeURL()) {
                request.setYoutubeExtractorArgs(url)
            }

            val ytdlResponse = RuntimeManager.getInstance().runKillable { processId ->
                RuntimeManager.getInstance().execute(request, processId)
            }
            val json = JSONObject(ytdlResponse.out)
            val urls = if (json.has("urls")) {
                json.getString("urls").split("\n")
            } else {
                listOf()
            }

            val chapters = if (json.has("chapters")) {
                val arr = json.getJSONArray("chapters")
                val list = mutableListOf<ChapterItem>()
                for (i in 0 until arr.length()) {
                    list.add(
                        Gson().fromJson(arr.getJSONObject(i).toString(), ChapterItem::class.java)
                    )
                }
                list
            } else {
                null
            }

            Result.success(Pair(urls, chapters))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun YTDLRequest.addWriteInfoJson(url: String) {
        val cache = File(FileUtil.getInfoJsonPath(this@YTDLPUtil.context))
        cache.mkdirs()
        addOption("--write-info-json")
        addOption("-P", cache.absolutePath)
        addOption("-o", "%(id)s##${hash(url)}.%(ext)s")
    }

    fun hash(text: String): String {
        val bytes = text.toByteArray()
        val crc32 = CRC32()
        crc32.update(bytes)
        return crc32.value.toString()
    }

    private fun getInfoJsonFile(url: String): File? {
        val cache = File(FileUtil.getInfoJsonPath(this@YTDLPUtil.context))
        val hash = hash(url)
        return cache.listFiles()?.firstOrNull { it.name.contains(hash) && it.extension == "json" }
    }

    private fun YTDLRequest.setYoutubeExtractorArgs(url: String?) {
        val playerClient = sharedPreferences.getString("youtube_player_client", "")
        if (!playerClient.isNullOrBlank()) {
            addOption("--extractor-args", "youtube:player_client=$playerClient")
        } else {
            addOption(
                "--extractor-args",
                "youtube:player_client=tv_samsung,web_embedded,ios,android,web"
            )
        }
    }
}