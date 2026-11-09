@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.tbib.kmp_localization.system

import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import platform.windows.GetUserDefaultLocaleName
import platform.windows.GetUserDefaultUILanguage
import platform.windows.LCIDToLocaleName
import platform.windows.LOCALE_NAME_MAX_LENGTH
import platform.windows.WCHARVar

actual fun getSystemFullLocale(): String = memScoped {
    val buffer = allocArray<WCHARVar>(LOCALE_NAME_MAX_LENGTH)
    val uiLanguage = GetUserDefaultUILanguage().toUInt()
    if (LCIDToLocaleName(uiLanguage, buffer, LOCALE_NAME_MAX_LENGTH, 0u) > 1) {
        return@memScoped buffer.toKString()
    }
    if (GetUserDefaultLocaleName(buffer, LOCALE_NAME_MAX_LENGTH) > 1) {
        return@memScoped buffer.toKString()
    }
    "en"
}
