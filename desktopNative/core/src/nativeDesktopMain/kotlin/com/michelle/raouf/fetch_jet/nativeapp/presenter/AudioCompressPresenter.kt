package com.michelle.raouf.fetch_jet.nativeapp.presenter

import com.michelle.raouf.fetch_jet.core.i18n.start.AppLanguage
import com.michelle.raouf.fetch_jet.feature.audio_compress.AudioCompress
import com.michelle.raouf.fetch_jet.feature.audio_compress.AudioCompressMode
import com.michelle.raouf.fetch_jet.feature.audio_compress.AudioCompressSettings
import com.michelle.raouf.fetch_jet.nativeapp.dto.AudioCompressDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.AudioCopyDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.ChoiceDto
import com.michelle.raouf.fetch_jet.presentation.dashboard.AudioCompressUi
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardUiEvent
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.tbib.kmp_localization.LocalizationManager
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private fun buildAudioCompress(page: AudioCompressUi?): AudioCompressDto {
    if (page == null) return AudioCompressDto()
    val s = LocalizationManager.Strings
    val settings = page.settings
    val bitrates = AudioCompress.BITRATES.map { ChoiceDto(it, "${it}k", it == settings.bitrateKbps) }
    val rates = AudioCompress.SAMPLE_RATES.map {
        ChoiceDto(it, if (it == 0) s.audioCompressAuto else "${it}Hz", it == settings.sampleRate)
    }
    val channelChoices = AudioCompress.CHANNELS.map {
        ChoiceDto(
            it,
            when (it) {
                1 -> s.audioCompressMono
                2 -> s.audioCompressStereo
                else -> s.audioCompressAuto
            },
            it == settings.channels
        )
    }
    val known = !page.reading && page.estimatedBytes > 0L && page.originalBytes > 0L
    val share = if (known) (page.estimatedBytes.toFloat() / page.originalBytes.toFloat()).coerceIn(0f, 1f) else 0f
    val levelIndex = (settings.level - 1).coerceIn(0, AudioCompress.LEVELS.lastIndex)
    return AudioCompressDto(
        open = true,
        saving = if (known && share < 1f) ltr("−" + (100 - (share * 100f).toInt()).coerceIn(0, 99) + "%") else "",
        ratio = share,
        level = levelIndex + 1,
        levelCount = AudioCompress.LEVELS.size,
        levelLabel = ltr((levelIndex + 1).toString() + " · " + AudioCompress.LEVELS[levelIndex].bitrateKbps + " kbps"),
        bitrates = bitrates,
        bitrateOptions = bitrates.map { it.label },
        bitratePick = bitrates.indexOfFirst { it.selected } + 1,
        rates = rates,
        rateOptions = rates.map { it.label },
        ratePick = rates.indexOfFirst { it.selected } + 1,
        channelOptions = channelChoices.map { it.label },
        channelPick = channelChoices.indexOfFirst { it.selected } + 1,
        progressText = if (page.running) ltr((page.progress * 100f).toInt().coerceIn(0, 100).toString() + "%") else "",
        fileName = page.fileName,
        originalSize = formatBytes(page.originalBytes),
        newSize = when {
            page.reading -> s.audioCompressReading
            page.estimatedBytes > 0L -> "≈ " + formatBytes(page.estimatedBytes)
            else -> "—"
        },
        simple = settings.mode == AudioCompressMode.SIMPLE,
        levels = AudioCompress.LEVELS.indices.map {
            ChoiceDto(it + 1, (it + 1).toString(), it + 1 == settings.level)
        },
        bitratesTop = bitrates.take(5),
        bitratesBottom = bitrates.drop(5),
        ratesTop = rates.take(4),
        ratesBottom = rates.drop(4),
        channels = channelChoices,
        running = page.running,
        progress = page.progress,
        failed = page.failed,
        canStart = !page.running && !page.reading,
        copies = page.copies.map { AudioCopyDto(it.path, it.name, formatBytes(it.bytes)) },
    )
}

internal fun Presenter.startAudioCompress() {
    scope.launch {
        combine(vm.uiState.map { it.audioCompress }.distinctUntilChanged(), AppLanguage.current) { page, _ ->
            buildAudioCompress(page)
        }.collect { audioCompress.publish(it) }
    }
}

internal fun Presenter.audioCompressChange(change: (AudioCompressSettings) -> AudioCompressSettings) {
    val page = vm.uiState.value.audioCompress ?: return
    vm.onEvent(DashboardUiEvent.OnAudioCompressSettingsChanged(change(page.settings)))
}
