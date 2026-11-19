package com.michelle.raouf.fetch_jet.core.i18n.start

enum class AppFontFamily(val familyName: String, val fileStem: String) {
    BASE("FetchJet Sans", "FetchJetSans"),
    SC("FetchJet Sans SC", "FetchJetSansSC"),
    JP("FetchJet Sans JP", "FetchJetSansJP"),
    KR("FetchJet Sans KR", "FetchJetSansKR");

    fun postScriptName(weight: AppFontWeight): String = "$fileStem-${weight.style}"
}

enum class AppFontWeight(val style: String, val value: Int) {
    REGULAR("Regular", 400),
    MEDIUM("Medium", 500),
    SEMI_BOLD("SemiBold", 600),
    BOLD("Bold", 700),
}

object AppFont {
    fun familyFor(language: String): AppFontFamily = when (language.substringBefore('-').lowercase()) {
        "zh" -> AppFontFamily.SC
        "ja" -> AppFontFamily.JP
        "ko" -> AppFontFamily.KR
        else -> AppFontFamily.BASE
    }

    val current: AppFontFamily get() = familyFor(AppLanguage.current.value)
}
