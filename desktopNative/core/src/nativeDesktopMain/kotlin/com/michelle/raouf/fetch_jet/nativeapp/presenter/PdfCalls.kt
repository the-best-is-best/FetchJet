package com.michelle.raouf.fetch_jet.nativeapp.presenter

import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.feature.hidden.HiddenMedia
import com.michelle.raouf.fetch_jet.feature.pdf_reader.PdfMode
import com.michelle.raouf.fetch_jet.feature.pdf_reader.PdfReader
import com.michelle.raouf.fetch_jet.feature.pdf_reader.ensurePdfLibrary
import com.michelle.raouf.fetch_jet.feature.pdf_reader.isPdfLibraryOutdated
import com.michelle.raouf.fetch_jet.feature.pdf_reader.isPdfLibraryReady
import com.michelle.raouf.fetch_jet.nativeapp.NativeApp
import com.michelle.raouf.fetch_jet.nativeapp.channels.UiJson
import com.michelle.raouf.fetch_jet.nativeapp.dto.ChoiceDialogDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.ChoiceDialogKind
import com.michelle.raouf.fetch_jet.nativeapp.dto.EffectKind
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardUiEvent
import com.tbib.kmp_localization.LocalizationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
private data class PdfOpenJson(val id: String, val title: String, val path: String, val page: Int, val hidden: Boolean)

internal data class PdfAsk(val task: DownloadTask)

internal val pdfAsk = MutableStateFlow<PdfAsk?>(null)

internal val hiddenPdf = MutableStateFlow(false)

internal fun Presenter.readPdf(task: DownloadTask): Boolean {
    if (!PdfReader.canRead(task) || PdfMode.isOff()) return false
    if (task.hidden && !vm.uiState.value.hidden.unlocked) return false
    if (!task.hidden && !pathResolver.fileExists(taskFilePath(task))) return false
    when {
        isPdfLibraryReady() -> pushPdfEffect(task)
        PdfMode.isOn() || isPdfLibraryOutdated() -> waitForPdf(task)
        else -> pdfAsk.value = PdfAsk(task)
    }
    return true
}

internal fun pdfAskDialog(ask: PdfAsk): ChoiceDialogDto {
    val s = LocalizationManager.Strings
    val name = ask.task.fileName
    return ChoiceDialogDto(
        kind = ChoiceDialogKind.PDF_READER.ordinal,
        icon = "📄",
        title = s.pdfAskTitle,
        name = name,
        nameRtl = startsRightToLeft(name),
        question = s.pdfAskText + "\n" + s.playerAskLater,
        primaryIcon = "⬇",
        primaryLabel = s.pdfAskDownload,
        secondaryIcon = "↗",
        secondaryLabel = s.playerAskSystem,
        cancelLabel = s.cancel,
    )
}

internal fun Presenter.pdfAskDownload() {
    val ask = pdfAsk.value ?: return
    pdfAsk.value = null
    PdfMode.set(PdfMode.ON)
    waitForPdf(ask.task)
}

private fun Presenter.waitForPdf(task: DownloadTask) {
    waitForLibrary(
        LibraryWait(
            pdf = true,
            name = task.fileName,
            secondaryIcon = "↗",
            secondaryLabel = { LocalizationManager.Strings.playerAskSystem },
            open = { pushPdfEffect(task) },
            other = { openPdfInSystemApp(task) },
        )
    )
}

internal fun Presenter.pdfAskSystem() {
    val ask = pdfAsk.value ?: return
    pdfAsk.value = null
    PdfMode.set(PdfMode.OFF)
    openPdfInSystemApp(ask.task)
}

internal fun Presenter.pdfLibraryDownload() {
    vm.ensurePdfLibrary { settingsPoke() }
}

private fun Presenter.openPdfInSystemApp(task: DownloadTask) {
    if (task.hidden) return vm.onEvent(DashboardUiEvent.OnHiddenOpenFile(task.id))
    if (headless) return
    val path = taskFilePath(task)
    scope.launch(Dispatchers.IO) {
        if (!pathResolver.openFile(path)) pathResolver.openFileLocation(path)
    }
}

private fun Presenter.pushPdfEffect(task: DownloadTask) {
    val current = vm.uiState.value.findTask(task.id) ?: task
    if (!current.hidden) {
        val json = PdfOpenJson(current.id, current.fileName, taskFilePath(current), PdfReader.lastPage(current), false)
        return effect(EffectKind.OPEN_PDF, text = UiJson.encodeToString(json))
    }
    scope.launch {
        val path = withContext(Dispatchers.IO) {
            runCatching { HiddenMedia.openPlayable(current.id, current.fileName, pathResolver = pathResolver) }.getOrNull()
        }
        if (path == null || !vm.uiState.value.hidden.unlocked) return@launch
        hiddenPdf.value = true
        effect(EffectKind.OPEN_PDF, text = UiJson.encodeToString(PdfOpenJson(current.id, current.fileName, path, 0, true)))
    }
}

internal fun Presenter.closeHiddenPdf() {
    if (!hiddenPdf.value) return
    hiddenPdf.value = false
    effect(EffectKind.CLOSE_PDF)
}

internal fun pdfCall(name: String, args: String): String {
    val obj: JsonObject = try {
        UiJson.parseToJsonElement(args.ifBlank { "{}" }).jsonObject
    } catch (_: Throwable) {
        return ""
    }
    fun text(key: String) = obj[key]?.jsonPrimitive?.content.orEmpty()
    fun number(key: String) = text(key).toIntOrNull() ?: text(key).toDoubleOrNull()?.toInt() ?: 0
    val presenter = NativeApp.presenter
    return when (name) {
        "page" -> {
            val task = presenter.vm.uiState.value.findTask(text("id")) ?: return ""
            if (task.hidden || text("hidden") == "true") return ""
            PdfReader.rememberPage(task, number("page"))
            "ok"
        }
        "label" -> PdfReader.pageLabel(number("page"), number("count"))
        "goto" -> (PdfReader.parsePage(text("text"), number("count")) ?: -1).toString()
        "zoom" -> {
            val zoom = text("zoom").toFloatOrNull() ?: 1f
            (if (text("in") == "true") PdfReader.zoomIn(zoom) else PdfReader.zoomOut(zoom)).toString()
        }
        "system" -> {
            val task = presenter.vm.uiState.value.findTask(text("id"))
            if (task != null) presenter.openPdfInSystemApp(task)
            else if (text("path").isNotEmpty() && !presenter.headless) com.michelle.raouf.fetch_jet.core.filesystem.PathResolver().openFile(text("path"))
            "ok"
        }
        "closed" -> {
            hiddenPdf.value = false
            "ok"
        }
        else -> ""
    }
}
