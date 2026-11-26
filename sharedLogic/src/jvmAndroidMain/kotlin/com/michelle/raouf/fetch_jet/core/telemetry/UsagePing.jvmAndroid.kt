package com.michelle.raouf.fetch_jet.core.telemetry

import com.michelle.raouf.fetch_jet.feature.backup.devicePlatformId

internal actual fun usagePlatform(): String? {
    if (System.getenv(USAGE_PING_OFF_ENV) == "1") return null
    val id = devicePlatformId()
    return when (id) {
        "android" -> if (isAndroidEmulator() || isDebuggableAndroidApp()) null else id
        "unknown" -> null
        else -> id
    }
}

private fun androidBuildField(name: String): String = runCatching {
    Class.forName("android.os.Build").getField(name).get(null) as? String
}.getOrNull().orEmpty().lowercase()

internal fun isAndroidEmulator(): Boolean = looksLikeEmulator(
    androidBuildField("FINGERPRINT"), androidBuildField("HARDWARE"), androidBuildField("PRODUCT"), androidBuildField("MODEL")
)

internal fun looksLikeEmulator(fingerprint: String, hardware: String, product: String, model: String): Boolean {
    val f = fingerprint.lowercase()
    val h = hardware.lowercase()
    val p = product.lowercase()
    val m = model.lowercase()
    return f.startsWith("generic") || f.contains("emulator") || f.contains("/sdk_gphone") ||
        h == "goldfish" || h == "ranchu" ||
        p.startsWith("sdk") || p.contains("emulator") || p.contains("simulator") ||
        m.contains("emulator") || m.contains("android sdk built for") || m.startsWith("sdk_gphone")
}

internal expect fun isDebuggableAndroidApp(): Boolean
