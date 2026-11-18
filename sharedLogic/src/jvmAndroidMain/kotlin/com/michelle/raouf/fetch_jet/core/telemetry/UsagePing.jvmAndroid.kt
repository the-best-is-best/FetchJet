package com.michelle.raouf.fetch_jet.core.telemetry

internal actual fun usagePlatform(): String? {
    if (System.getenv(USAGE_PING_OFF_ENV) == "1") return null
    val vm = System.getProperty("java.vm.name").orEmpty().lowercase()
    val vendor = System.getProperty("java.vendor").orEmpty().lowercase()
    val os = System.getProperty("os.name").orEmpty().lowercase()
    return when {
        vm.contains("dalvik") || vendor.contains("android") ->
            if (isAndroidEmulator() || isDebuggableAndroidApp()) null else "android"
        os.contains("mac") -> "macos"
        os.contains("win") -> "windows"
        os.contains("linux") || os.contains("nux") -> "linux"
        else -> null
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
