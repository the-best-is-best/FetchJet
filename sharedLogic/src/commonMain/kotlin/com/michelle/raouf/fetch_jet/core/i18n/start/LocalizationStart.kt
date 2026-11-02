package com.michelle.raouf.fetch_jet.core.i18n.start

import com.michelle.raouf.fetch_jet.core.i18n.data_provider.AppLocalization
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.ArabicProvider
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.ChineseProvider
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.EnglishProvider
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.FrenchProvider
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.GermanProvider
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.HindiProvider
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.IndonesianProvider
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.ItalianProvider
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.JapaneseProvider
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.KoreanProvider
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.PersianProvider
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.PortugueseProvider
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.RussianProvider
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.SpanishProvider
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.TurkishProvider
import com.tbib.kmp_localization.LocalizationManager
import com.tbib.kmp_localization.system.getSystemFullLocale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

val appLanguages: List<AppLocalization> = listOf(
    EnglishProvider,
    ArabicProvider,
    FrenchProvider,
    GermanProvider,
    SpanishProvider,
    PortugueseProvider,
    ItalianProvider,
    RussianProvider,
    TurkishProvider,
    IndonesianProvider,
    ChineseProvider,
    JapaneseProvider,
    KoreanProvider,
    HindiProvider,
    PersianProvider
)

private var started = false

fun LocalizationManager.start() {
    if (started) return
    started = true
    registerProviders(*appLanguages.toTypedArray())
    setDefaultLanguage("en")
    AppLanguage.change(
        runCatching {
            com.michelle.raouf.fetch_jet.core.settings.AppSettingsRepository()
                .loadSettings().language
        }
            .getOrDefault(AppLanguage.SYSTEM)
    )
}

object AppLanguage {
    const val SYSTEM = "system"

    private val rtl = setOf("ar", "fa", "ur", "he")

    private val _current = MutableStateFlow("en")
    val current: StateFlow<String> = _current.asStateFlow()

    var choice: String = SYSTEM
        private set

    val isRtl: Boolean get() = _current.value.substringBefore('-') in rtl

    fun change(choice: String) {
        this.choice = if (appLanguages.any { it.localeCode == choice }) choice else SYSTEM
        LocalizationManager.changeLanguage(if (this.choice == SYSTEM) getSystemFullLocale() else this.choice)
        _current.value = LocalizationManager.getCurrentLanguage()
    }
}
