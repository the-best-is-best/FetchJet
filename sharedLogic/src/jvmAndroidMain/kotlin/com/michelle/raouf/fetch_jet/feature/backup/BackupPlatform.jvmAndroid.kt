package com.michelle.raouf.fetch_jet.feature.backup

internal actual fun devicePlatformId(): String {
    val vm = System.getProperty("java.vm.name").orEmpty().lowercase()
    val vendor = System.getProperty("java.vendor").orEmpty().lowercase()
    val os = System.getProperty("os.name").orEmpty().lowercase()
    return when {
        vm.contains("dalvik") || vendor.contains("android") -> "android"
        os.contains("mac") -> "macos"
        os.contains("win") -> "windows"
        os.contains("linux") || os.contains("nux") -> "linux"
        else -> "unknown"
    }
}
