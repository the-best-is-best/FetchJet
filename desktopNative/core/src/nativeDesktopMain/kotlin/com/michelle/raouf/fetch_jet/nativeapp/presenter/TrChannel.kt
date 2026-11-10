package com.michelle.raouf.fetch_jet.nativeapp.presenter

import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.core.i18n.start.AppLanguage
import com.michelle.raouf.fetch_jet.nativeapp.channels.UiChannel
import com.michelle.raouf.fetch_jet.nativeapp.generated.trValues
import com.michelle.raouf.fetch_jet.nativeapp.runtime.ChangeNotifier
import com.tbib.kmp_localization.LocalizationManager
import kotlin.concurrent.AtomicReference
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

internal fun uiFontFor(language: String): String = when (language.substringBefore('-')) {
    "zh" -> "Microsoft YaHei UI"
    else -> "FetchJet Sans"
}

internal class TrChannel(
    override val id: Int,
    override val name: String,
    private val notifier: ChangeNotifier,
) : UiChannel {
    private val json = AtomicReference("{}")

    fun publishCurrent() {
        val language = AppLanguage.current.value
        val strings = LocalizationManager.Strings
        val text = buildJsonObject {
            put("lang", language)
            put("rtl", AppLanguage.isRtl)
            put("font", uiFontFor(language))
            putJsonArray("values") {
                for (value in trValues(strings)) add(value)
            }
        }.toString()
        if (text != json.value) {
            json.value = text
            notifier.mark(id)
        }
    }

    override fun snapshot(full: Boolean): String = json.value
}
