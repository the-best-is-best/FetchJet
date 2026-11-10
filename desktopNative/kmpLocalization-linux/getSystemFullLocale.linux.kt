@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.tbib.kmp_localization.system

import kotlinx.cinterop.toKString
import platform.posix.getenv

actual fun getSystemFullLocale(): String {
    for (name in listOf("LC_ALL", "LC_MESSAGES", "LANGUAGE", "LANG")) {
        val value = getenv(name)?.toKString()?.trim().orEmpty()
        if (value.isEmpty() || value == "C" || value == "POSIX") continue
        val tag = value.substringBefore(':').substringBefore('.').substringBefore('@').replace('_', '-')
        if (tag.isNotEmpty() && tag != "C") return tag
    }
    return "en"
}
