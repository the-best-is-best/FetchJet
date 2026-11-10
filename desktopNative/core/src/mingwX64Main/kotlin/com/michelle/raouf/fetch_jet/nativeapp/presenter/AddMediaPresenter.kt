package com.michelle.raouf.fetch_jet.nativeapp.presenter

import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.core.i18n.start.AppLanguage
import com.michelle.raouf.fetch_jet.core.i18n.start.localizeStep
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.michelle.raouf.fetch_jet.domain.model.MediaQualityEntry
import com.michelle.raouf.fetch_jet.domain.model.SubtitleTrack
import com.michelle.raouf.fetch_jet.domain.model.isAllAudio
import com.michelle.raouf.fetch_jet.domain.model.isAudioOnly
import com.michelle.raouf.fetch_jet.feature.media_extractor.AUDIO_CONVERT_FORMATS
import com.michelle.raouf.fetch_jet.feature.media_extractor.MediaPlaylist
import com.michelle.raouf.fetch_jet.feature.media_extractor.PlaylistQuality
import com.michelle.raouf.fetch_jet.nativeapp.dto.AddMediaDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.ChoiceDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.ClipChipDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.ClipPickerDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.ClipPickerField
import com.michelle.raouf.fetch_jet.nativeapp.dto.MediaStateCode
import com.michelle.raouf.fetch_jet.nativeapp.dto.PlaylistRowDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.QualityRowDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.SubtitleRowDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.TextChoiceDto
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardUiEvent
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardUiState
import com.michelle.raouf.fetch_jet.presentation.dashboard.LinkListEntry
import com.tbib.kmp_localization.LocalizationManager
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class ClipCheck(
    val fullHhMm: String,
    val startSec: Long,
    val endSec: Long,
    val startError: Boolean,
    val endError: Boolean,
    val valid: Boolean,
    val message: String?,
)

internal fun clipCheck(enabled: Boolean, start: String, end: String, durationSeconds: Long): ClipCheck {
    val full = if (durationSeconds > 0) formatSecondsToHhMm(durationSeconds) else "00:10:00"
    val startSec = parseHhMmToSeconds(start.ifBlank { "00:00" })
    val endSec = parseHhMmToSeconds(end.ifBlank { full })
    val startAfterEnd = enabled && startSec >= endSec
    val endExceeds = enabled && durationSeconds > 0L && endSec > durationSeconds
    val startExceeds = enabled && durationSeconds > 0L && startSec >= durationSeconds
    val strings = LocalizationManager.Strings
    val message = when {
        !enabled -> null
        startAfterEnd -> strings.clipStartAfterEnd(start.ifBlank { "00:00" }, end.ifBlank { full })
        endExceeds -> strings.clipEndExceedsVideo(end.ifBlank { full }, full)
        startExceeds -> strings.clipStartExceedsVideo(full)
        else -> null
    }
    return ClipCheck(
        fullHhMm = full,
        startSec = startSec,
        endSec = endSec,
        startError = startAfterEnd || startExceeds,
        endError = startAfterEnd || endExceeds,
        valid = !enabled || (!startAfterEnd && !endExceeds && !startExceeds),
        message = message,
    )
}

internal fun clockText(totalSeconds: Long): String {
    val t = totalSeconds.coerceAtLeast(0L)
    val h = t / 3600
    val m = (t % 3600) / 60
    val s = t % 60
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    else "$m:${s.toString().padStart(2, '0')}"
}

internal data class ClipPicker(
    val which: Int = 0,
    val hours: Int = 0,
    val minutes: Int = 0,
    val seconds: Int = 0,
    val maxSeconds: Long = 0L,
    val minSeconds: Long = 0L,
    val hoursRev: Int = 0,
    val minutesRev: Int = 0,
    val secondsRev: Int = 0,
) {
    val total: Long get() = hours * 3600L + minutes * 60L + seconds

    fun withTotal(value: Long): ClipPicker {
        val t = value.coerceIn(0L, 359_999L)
        return copy(hours = (t / 3600).toInt(), minutes = ((t % 3600) / 60).toInt(), seconds = (t % 60).toInt())
    }

    fun rewritten(): ClipPicker = copy(hoursRev = hoursRev + 1, minutesRev = minutesRev + 1, secondsRev = secondsRev + 1)

    val formatted: String
        get() = hours.toString().padStart(2, '0') + ":" + minutes.toString().padStart(2, '0') + ":" +
            seconds.toString().padStart(2, '0')
}

private data class MediaSlice(
    val visible: Boolean,
    val inputUrl: String,
    val engine: EngineType,
    val inspecting: Boolean,
    val courseReading: Pair<Int, Int>?,
    val qualities: List<MediaQualityEntry>,
    val subtitles: List<SubtitleTrack>,
    val embedSubtitles: Boolean,
    val error: String?,
    val ytDlpInstalled: Boolean,
    val ytDlpStatus: String,
    val downloadingTools: Boolean,
    val clipStart: String,
    val clipEnd: String,
    val clipEnabled: Boolean,
    val duration: Long,
    val clipFastMode: Boolean,
    val audioFormat: String?,
    val playlist: MediaPlaylist?,
    val playlistQuality: PlaylistQuality,
    val linkList: List<LinkListEntry>,
)

private fun DashboardUiState.mediaSlice() = MediaSlice(
    visible = isAddDialogVisible,
    inputUrl = inputUrl,
    engine = detectedEngine,
    inspecting = isInspectingMedia,
    courseReading = courseReading,
    qualities = inspectedMediaQualities,
    subtitles = inspectedSubtitles,
    embedSubtitles = embedSubtitles,
    error = inspectMediaError,
    ytDlpInstalled = isYtDlpInstalled,
    ytDlpStatus = ytDlpStatus,
    downloadingTools = isDownloadingYtDlp,
    clipStart = clipStartTime,
    clipEnd = clipEndTime,
    clipEnabled = isClipRangeEnabled,
    duration = mediaDurationSeconds,
    clipFastMode = clipFastMode,
    audioFormat = audioConvertFormat,
    playlist = inspectedPlaylist,
    playlistQuality = playlistQuality,
    linkList = linkList,
)

private val AudioExtensions = listOf("mp3", "m4a", "flac", "wav", "aac", "ogg", "opus")

internal fun Presenter.startAddMedia() {
    val slices = vm.uiState.map { it.mediaSlice() }.distinctUntilChanged()
    scope.launch {
        combine(slices, AppLanguage.current) { slice, _ -> slice }.collect { publishMedia(it) }
    }
    scope.launch {
        combine(clipPicker, AppLanguage.current) { picker, _ -> buildClipPicker(picker) }.collect { clipPickerChannel.publish(it) }
    }
    scope.launch {
        slices.map { it.visible }.distinctUntilChanged().collect { visible ->
            if (!visible) clipPicker.value = ClipPicker()
        }
    }
}

private fun Presenter.publishMedia(s: MediaSlice) {
    if (!s.visible) {
        addMedia.publish(AddMediaDto())
        addQualities.publish(emptyList())
        addSubtitles.publish(emptyList())
        addPlaylist.publish(emptyList())
        return
    }
    val strings = LocalizationManager.Strings
    val showsLinkList = s.linkList.isNotEmpty()
    val playlist = s.playlist
    val selectedQuality = s.qualities.firstOrNull { it.isSelected }
    val isAnyAudio = selectedQuality?.isAudioOnly == true

    val audioShown = when {
        showsLinkList -> s.linkList.any { e -> e.isSelected && e.mediaQualities.firstOrNull { it.isSelected }?.isAudioOnly == true }
        playlist != null -> s.playlistQuality == PlaylistQuality.AUDIO
        else -> s.engine == EngineType.MEDIA_STREAM && !s.inspecting && isAnyAudio
    }
    val nativeAudio = when {
        showsLinkList -> s.linkList.filter { it.isSelected }
            .mapNotNull { e -> e.mediaQualities.firstOrNull { it.isSelected && it.isAudioOnly }?.format }
            .distinct().singleOrNull()

        playlist != null -> s.playlistQuality.extension
        else -> selectedQuality?.format
    }?.lowercase()?.ifBlank { null }
    val audioFormats = if (!audioShown) emptyList() else buildList {
        add(TextChoiceDto("", nativeAudio?.uppercase() ?: strings.audioOriginal, s.audioFormat == null || s.audioFormat == nativeAudio))
        for (format in AUDIO_CONVERT_FORMATS) {
            if (format != nativeAudio) add(TextChoiceDto(format, format.uppercase(), s.audioFormat == format))
        }
    }
    val audioTitle = if (showsLinkList || playlist != null) strings.audioConvertAllTo else strings.audioConvertTo

    if (showsLinkList || s.engine != EngineType.MEDIA_STREAM) {
        addMedia.publish(AddMediaDto(audioShown = audioShown, audioTitle = audioTitle, audioFormats = audioFormats))
        addQualities.publish(emptyList())
        addSubtitles.publish(emptyList())
        addPlaylist.publish(emptyList())
        return
    }

    val course = s.courseReading
    val state = when {
        course != null -> MediaStateCode.COURSE_READING
        s.inspecting -> MediaStateCode.INSPECTING
        s.error != null -> MediaStateCode.ERROR
        !s.ytDlpInstalled -> MediaStateCode.ENGINE_MISSING
        playlist != null -> MediaStateCode.PLAYLIST
        s.qualities.isEmpty() -> MediaStateCode.NOT_INSPECTED
        else -> MediaStateCode.QUALITIES
    }
    val preparing = s.downloadingTools || s.ytDlpStatus.startsWith("Preparing", ignoreCase = true)
    val isInputAudio = s.inputUrl.substringBefore('?').substringAfterLast('.', "").lowercase() in AudioExtensions
    val allAudio = s.qualities.isAllAudio()
    val videoTitle = s.qualities.firstOrNull { !it.videoTitle.isNullOrBlank() }?.videoTitle
    val qualitiesTitle = if (!videoTitle.isNullOrBlank()) videoTitle
    else if (allAudio) strings.availableAudioFormats else strings.availableQualities
    val check = clipCheck(s.clipEnabled, s.clipStart, s.clipEnd, s.duration)
    val startDisplay = s.clipStart.ifBlank { "00:00" }
    val endDisplay = s.clipEnd.ifBlank { check.fullHhMm }
    val chips = buildList {
        if (s.duration > 0) add(ClipChipDto(strings.fullMediaChip(isAnyAudio, clockText(s.duration)), "00:00", check.fullHhMm))
        if (s.duration <= 0L || s.duration >= 300L) add(ClipChipDto(strings.clipFirst5Min, "00:00:00", "00:05:00"))
        if (s.duration <= 0L || s.duration >= 900L) add(ClipChipDto(strings.clipFirst15Min, "00:00:00", "00:15:00"))
        if (s.duration <= 0L || s.duration >= 1800L) add(ClipChipDto(strings.clipFirst30Min, "00:00:00", "00:30:00"))
        if (s.duration <= 0L || s.duration >= 3600L) add(ClipChipDto(strings.clipFirst1Hour, "00:00:00", "01:00:00"))
    }
    val fullSizeText = selectedQuality?.size?.takeIf { it > 0L }?.let { " (${formatBytes(it)})" } ?: ""

    if (state == MediaStateCode.QUALITIES) {
        addQualities.publish(s.qualities.map { quality ->
            val sizeText = if (quality.size > 0) " • ${formatSizeDisplay(quality.size)}" else ""
            val durText = if (quality.durationSeconds > 0) " • ⏱ ${formatSecondsToDisplayFriendly(quality.durationSeconds)}" else ""
            QualityRowDto(
                index = quality.index,
                label = quality.qualityLabel,
                line = strings.qualityFormatLine(quality.format.uppercase(), "$sizeText$durText"),
                selected = quality.isSelected,
            )
        })
        addSubtitles.publish(s.subtitles.map { SubtitleRowDto(it.langCode, it.langName, it.isAuto, it.isSelected) })
    } else {
        addQualities.publish(emptyList())
        addSubtitles.publish(emptyList())
    }
    if (state == MediaStateCode.PLAYLIST && playlist != null) {
        addPlaylist.publish(playlist.entries.map { entry ->
            val title = if (playlist.fromBrowser) "${entry.title}.mp4" else entry.title
            PlaylistRowDto(
                index = entry.index,
                number = if (playlist.fromBrowser) "" else "${entry.index + 1}.",
                title = title,
                titleRtl = startsRightToLeft(title),
                duration = if (entry.durationSeconds > 0L) clockText(entry.durationSeconds) else "",
                selected = entry.isSelected,
            )
        })
    } else {
        addPlaylist.publish(emptyList())
    }
    val playlistFolder = playlist?.let { it.folderName ?: it.title }.orEmpty()
    val selectedCount = playlist?.entries?.count { it.isSelected } ?: 0
    addMedia.publish(
        AddMediaDto(
            state = state.ordinal,
            bannerTitle = when (state) {
                MediaStateCode.COURSE_READING -> strings.readingCourseLectures(course?.first ?: 0, course?.second ?: 0)
                MediaStateCode.INSPECTING -> strings.inspectingVideoQualities
                else -> ""
            },
            bannerHint = when (state) {
                MediaStateCode.COURSE_READING -> strings.readingCourseLecturesHint
                MediaStateCode.INSPECTING -> strings.inspectingVideoQualitiesHint
                else -> ""
            },
            error = if (state == MediaStateCode.ERROR) s.error.orEmpty() else "",
            enginePreparing = preparing,
            engineTitle = if (preparing) strings.mediaEngineStarting else strings.mediaEngineNotReady,
            engineStatus = localizeStep(s.ytDlpStatus).ifBlank { strings.mediaEngineRequiresYtDlp },
            inspectIcon = if (isInputAudio) "🎵" else "🎬",
            inspectLabel = if (isInputAudio) strings.inspectAudioFormats else strings.inspectVideoQuality,
            inspectEnabled = s.inputUrl.isNotBlank(),
            qualitiesIcon = if (allAudio) "🎵" else "🎬",
            qualitiesTitle = qualitiesTitle,
            qualitiesTitleRtl = startsRightToLeft(qualitiesTitle),
            qualitiesCount = strings.selectedOfAvailable(s.qualities.count { it.isSelected }, s.qualities.size),
            subtitlesShown = state == MediaStateCode.QUALITIES && s.subtitles.isNotEmpty(),
            subtitlesAnySelected = s.subtitles.any { it.isSelected },
            embedSubtitles = s.embedSubtitles,
            clipAvailable = state == MediaStateCode.QUALITIES && s.duration > 0L,
            clipEnabled = s.clipEnabled,
            clipTitle = strings.clipSectionTitle(isAnyAudio),
            clipFullRange = if (s.duration > 0) strings.mediaDurationFullRange(
                isAnyAudio, clockText(s.duration), formatSecondsToDisplayFriendly(s.duration)
            ) else "",
            clipStart = startDisplay,
            clipStartFriendly = formatSecondsToDisplayFriendly(parseHhMmToSeconds(startDisplay)),
            clipStartError = check.startError,
            clipEnd = endDisplay,
            clipEndFriendly = formatSecondsToDisplayFriendly(parseHhMmToSeconds(endDisplay)),
            clipEndError = check.endError,
            clipError = check.message.orEmpty(),
            clipChips = chips,
            clipFastMode = s.clipFastMode,
            fullThenCutTitle = strings.fullThenCutTitle(isAnyAudio),
            fullThenCutLine1 = strings.fullThenCutLine1(isAnyAudio, fullSizeText),
            audioShown = audioShown,
            audioTitle = audioTitle,
            audioFormats = audioFormats,
            playlistTitle = playlist?.title.orEmpty(),
            playlistTitleRtl = startsRightToLeft(playlist?.title.orEmpty()),
            playlistSummary = if (playlist != null) strings.playlistSummary(playlist.entries.size, selectedCount) else "",
            playlistAllSelected = playlist != null && selectedCount == playlist.entries.size,
            playlistFolder = playlistFolder,
            playlistFolderRev = addFields.playlistFolder.rev(playlistFolder),
            playlistFromBrowser = playlist?.fromBrowser == true,
            playlistQualities = if (playlist == null) emptyList() else PlaylistQuality.entries.map {
                ChoiceDto(it.ordinal, it.label, it == s.playlistQuality)
            },
            playlistCanUseSingle = s.inputUrl.contains("v=") || s.inputUrl.contains("youtu.be/"),
        )
    )
}

private fun buildClipPicker(p: ClipPicker): ClipPickerDto {
    if (p.which == 0) return ClipPickerDto()
    val strings = LocalizationManager.Strings
    val total = p.total
    val exceeds = p.maxSeconds > 0L && total > p.maxSeconds
    val below = p.minSeconds > 0L && total <= p.minSeconds
    val valid = !exceeds && !below
    val summary = buildList {
        if (p.hours > 0) add("${p.hours}h")
        if (p.minutes > 0) add("${p.minutes}m")
        if (p.seconds > 0) add("${p.seconds}s")
    }.joinToString(" ").ifEmpty { strings.pickerStartZero }
    val endChip = if (p.maxSeconds > 0) {
        val maxH = (p.maxSeconds / 3600).toInt()
        val maxM = ((p.maxSeconds % 3600) / 60).toInt()
        strings.pickerEndOfVideo("${maxH.toString().padStart(2, '0')}:${maxM.toString().padStart(2, '0')}")
    } else ""
    return ClipPickerDto(
        open = true,
        title = if (p.which == 1) strings.setStartTimeTitle else strings.setEndTimeTitle,
        hours = p.hours.toString().padStart(2, '0'),
        minutes = p.minutes.toString().padStart(2, '0'),
        seconds = p.seconds.toString().padStart(2, '0'),
        hoursRev = p.hoursRev,
        minutesRev = p.minutesRev,
        secondsRev = p.secondsRev,
        upEnabled = p.maxSeconds <= 0L || total < p.maxSeconds,
        hoursDownEnabled = p.hours > 0,
        minutesDownEnabled = p.minutes > 0 || p.hours > 0,
        secondsDownEnabled = total > 0,
        summary = strings.pickerSelectedTime(p.formatted, summary),
        error = when {
            exceeds -> strings.pickerExceedsVideo(formatSecondsToHhMm(p.maxSeconds))
            below -> strings.pickerMustBeAfterStart(formatSecondsToHhMm(p.minSeconds))
            else -> ""
        },
        endChip = endChip,
        minChip = if (p.minSeconds > 0L) strings.pickerMin(formatSecondsToHhMm(p.minSeconds + 60L)) else "00:00",
        valid = valid,
        applyLabel = if (valid) strings.pickerApply(p.formatted) else strings.pickerInvalidTime,
    )
}

internal fun Presenter.clipPickerOpen(end: Boolean) {
    val state = vm.uiState.value
    val check = clipCheck(state.isClipRangeEnabled, state.clipStartTime, state.clipEndTime, state.mediaDurationSeconds)
    val duration = state.mediaDurationSeconds
    val initial = if (end) state.clipEndTime.ifBlank { check.fullHhMm } else state.clipStartTime.ifBlank { "00:00" }
    val parts = initial.split(":")
    val maxForStart = if (check.endSec > 0L) {
        if (duration > 0L) minOf(duration, check.endSec - 1L) else check.endSec - 1L
    } else duration
    clipPicker.value = ClipPicker(
        which = if (end) 2 else 1,
        hours = (parts.getOrNull(0)?.toIntOrNull() ?: 0).coerceAtLeast(0),
        minutes = (parts.getOrNull(1)?.toIntOrNull() ?: 0).coerceIn(0, 59),
        seconds = (parts.getOrNull(2)?.toIntOrNull() ?: 0).coerceIn(0, 59),
        maxSeconds = if (end) duration else maxForStart.coerceAtLeast(0L),
        minSeconds = if (end) check.startSec else 0L,
        hoursRev = clipPicker.value.hoursRev + 1,
        minutesRev = clipPicker.value.minutesRev + 1,
        secondsRev = clipPicker.value.secondsRev + 1,
    )
}

internal fun Presenter.clipPickerType(field: Int, text: String) {
    val digits = text.filter { it.isDigit() }.take(2)
    val parsed = digits.toIntOrNull() ?: 0
    clipPicker.update { p ->
        when (ClipPickerField.entries.getOrNull(field)) {
            ClipPickerField.HOURS -> p.copy(hours = parsed.coerceAtMost(99), hoursRev = p.hoursRev + if (digits != text) 1 else 0)
            ClipPickerField.MINUTES -> p.copy(
                minutes = parsed.coerceAtMost(59), minutesRev = p.minutesRev + if (digits != text || parsed > 59) 1 else 0
            )

            ClipPickerField.SECONDS -> p.copy(
                seconds = parsed.coerceAtMost(59), secondsRev = p.secondsRev + if (digits != text || parsed > 59) 1 else 0
            )

            null -> p
        }
    }
}

internal fun Presenter.clipPickerStep(field: Int, up: Boolean) {
    clipPicker.update { p ->
        val max = p.maxSeconds
        val total = p.total
        when (ClipPickerField.entries.getOrNull(field)) {
            ClipPickerField.HOURS -> if (up) {
                val next = (p.hours + 1) * 3600L + p.minutes * 60L + p.seconds
                if (max in 1..next) p.withTotal(max) else p.copy(hours = p.hours + 1)
            } else if (p.hours > 0) p.copy(hours = p.hours - 1) else p

            ClipPickerField.MINUTES -> if (up) {
                val next = total + 60L
                if (max in 1..next) p.withTotal(max)
                else if (p.minutes < 59) p.copy(minutes = p.minutes + 1) else p.copy(minutes = 0, hours = p.hours + 1)
            } else if (p.minutes > 0) p.copy(minutes = p.minutes - 1)
            else if (p.hours > 0) p.copy(hours = p.hours - 1, minutes = 59) else p

            ClipPickerField.SECONDS -> if (up) {
                val next = total + 1L
                if (max in 1..next) p.withTotal(max) else p.withTotal(total + 1L)
            } else if (total > 0) p.withTotal(total - 1L) else p

            null -> p
        }.rewritten()
    }
}

internal fun Presenter.clipPickerQuick(code: Int) {
    clipPicker.update { p ->
        val max = p.maxSeconds
        fun plusMinutes(add: Int): ClipPicker {
            val targetSec = (p.hours * 60 + p.minutes + add) * 60L
            return if (max in 1..targetSec) p.withTotal(max) else p.withTotal(targetSec)
        }
        when (code) {
            0 -> plusMinutes(5)
            1 -> plusMinutes(15)
            2 -> plusMinutes(30)
            3 -> {
                val targetSec = (p.hours * 60 + p.minutes + 60) * 60L
                if (max in 1..targetSec) p.withTotal(max) else p.copy(hours = p.hours + 1)
            }

            4 -> if (max > 0) p.withTotal(max) else p
            5 -> if (p.minSeconds > 0L) p.withTotal((p.minSeconds / 60 + 1) * 60) else p.withTotal(0L)
            else -> p
        }.rewritten()
    }
}

internal fun Presenter.clipPickerApply() {
    val p = clipPicker.value
    if (p.which == 0) return
    val exceeds = p.maxSeconds > 0L && p.total > p.maxSeconds
    val below = p.minSeconds > 0L && p.total <= p.minSeconds
    if (exceeds || below) return
    clipPicker.value = ClipPicker()
    if (p.which == 1) vm.onEvent(DashboardUiEvent.OnClipStartTimeChanged(p.formatted))
    else vm.onEvent(DashboardUiEvent.OnClipEndTimeChanged(p.formatted))
}
