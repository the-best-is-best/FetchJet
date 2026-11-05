package com.michelle.raouf.fetch_jet.core.i18n.start

import com.michelle.raouf.fetch_jet.core.i18n.data_provider.AppLocalization
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.tbib.kmp_localization.LocalizationManager

fun localizeStep(text: String): String {
    if (text.isBlank()) return text
    val s = LocalizationManager.Strings
    if (!text.contains(SEPARATOR)) return localizePart(s, text)
    return text.split(SEPARATOR).joinToString(SEPARATOR) { localizePart(s, it) }
}

private const val SEPARATOR = " · "

private val ignoreCase = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
private val trailingEllipsis = Regex("""\s*(\.\.\.|…)$""")
private val stepOf = Regex("""^Step (\d+) of (\d+)$""", ignoreCase)
private val savingAs = Regex("""^Saving as \.(\S+)$""", ignoreCase)
private val joiningParts = Regex("""^Joining (\d+) parts into MP4$""", ignoreCase)
private val noDataFor = Regex("""^No data for (\d+)s$""", ignoreCase)
private val peersSeeds = Regex("""^(\d+) peers \((\d+) seeds\)$""", ignoreCase)
private val downloadingFrom = Regex("""^Downloading from (.+)$""", ignoreCase)
private val noRanges =
    Regex("""^Piece \d+: server answered 200 to a Range request \(no ranges\)$""", ignoreCase)
private val engineNotReady = Regex("""^Media engine not ready: (.+)$""", ignoreCase)
private val torrentEngineFailed = Regex("""^BitTorrent engine failed to start: (.+)$""", ignoreCase)
private val readyWith = Regex("""^Ready: (.+)$""", ignoreCase)
private val labelWithParens = Regex("""^(.+?) \(([^()]+)\)$""", ignoreCase)
private val numbersOnly = Regex("""^[\d:/ .]+$""")

private fun localizePart(s: AppLocalization, part: String): String {
    val trimmed = part.trim()
    val ellipsis = trailingEllipsis.find(trimmed)
    val core = if (ellipsis != null) trimmed.substring(0, ellipsis.range.first) else trimmed
    val mapped = mapCore(s, core) ?: return part
    return if (ellipsis != null) "$mapped…" else mapped
}

private fun mapCore(s: AppLocalization, core: String): String? {
    exact(s, core)?.let { return it }
    stepOf.matchEntire(core)?.let { m ->
        val step = m.groupValues[1].toIntOrNull() ?: return null
        val total = m.groupValues[2].toIntOrNull() ?: return null
        return s.stepNumber(step, total)
    }
    savingAs.matchEntire(core)?.let { return s.stepSavingAs(it.groupValues[1]) }
    joiningParts.matchEntire(core)?.let { m ->
        return s.stepJoiningParts(m.groupValues[1].toIntOrNull() ?: return null)
    }
    noDataFor.matchEntire(core)?.let { m ->
        return s.stepNoDataFor(m.groupValues[1].toIntOrNull() ?: return null)
    }
    peersSeeds.matchEntire(core)?.let { m ->
        val peers = m.groupValues[1].toIntOrNull() ?: return null
        val seeds = m.groupValues[2].toIntOrNull() ?: return null
        return s.stepPeers(peers, seeds)
    }
    if (noRanges.matches(core)) return s.stepServerNoRanges
    downloadingFrom.matchEntire(core)?.let { return s.stepDownloadingFrom(it.groupValues[1]) }
    engineNotReady.matchEntire(core)?.let { return s.stepMediaEngineNotReady(it.groupValues[1]) }
    torrentEngineFailed.matchEntire(core)
        ?.let { return s.stepTorrentEngineFailed(it.groupValues[1]) }
    readyWith.matchEntire(core)?.let { return s.stepReadyWith(it.groupValues[1]) }
    labelWithParens.matchEntire(core)?.let { m ->
        val label = exact(s, m.groupValues[1]) ?: return null
        val inner = m.groupValues[2]
        stepOf.matchEntire(inner)?.let { step ->
            val n = step.groupValues[1].toIntOrNull() ?: return null
            val total = step.groupValues[2].toIntOrNull() ?: return null
            return "$label (${s.stepNumber(n, total)})"
        }
        if (numbersOnly.matches(inner)) return "$label ($inner)"
    }
    return null
}

private fun exact(s: AppLocalization, text: String): String? = when (text.trim().lowercase()) {
    "completed" -> s.stepCompleted
    "seeding" -> s.stepSeeding
    "fetching metadata" -> s.stepFetchingMetadata
    "connecting to peers" -> s.stepConnectingToPeers
    "checking files" -> s.stepCheckingFiles
    "checking resume data" -> s.stepCheckingResumeData
    "downloading" -> s.stepDownloading
    "finished" -> s.stepFinished
    "connecting" -> s.stepConnecting
    "paused" -> s.stepPaused
    "error" -> s.stepError
    "resolving streams" -> s.stepResolvingStreams
    "downloading parts" -> s.stepDownloadingParts
    "preparing download" -> s.stepPreparingDownload
    "clipping video" -> s.stepClippingVideo
    "downloading media" -> s.stepDownloadingMedia
    "already downloaded" -> s.stepAlreadyDownloaded
    "downloading video" -> s.stepDownloadingVideo
    "downloading audio" -> s.stepDownloadingAudio
    "merging audio and video",
    "merging video and audio",
    "merging audio + video",
    "merging audio & video" -> s.stepMergingAudioVideo

    "subtitles" -> s.subtitlesTitle
    "converting to mp3" -> s.stepConvertingToMp3
    "cutting the clip", "cutting clip" -> s.stepCuttingClip
    "saving to folder" -> s.stepSavingToFolder
    "finalizing video" -> s.stepFinalizingVideo
    "converting video" -> s.stepConvertingVideo
    "repackaging video" -> s.stepRepackagingVideo
    "yt-dlp fallback" -> s.stepYtDlpFallback
    "not a plain stream" -> s.stepNotPlainStream
    "single connection" -> s.stepSingleConnection
    "age-restricted: expired cookies in files > fetchjet" -> s.stepAgeRestrictedExpiredCookies
    "age-restricted: place cookies.txt in files > fetchjet" -> s.stepAgeRestrictedPlaceCookies
    "preparing in-app media engine (starting python, loading yt-dlp)" -> s.stepPreparingMediaEngine
    "media engine not started. click 'download tools'." -> s.stepMediaEngineNotStarted
    else -> null
}
