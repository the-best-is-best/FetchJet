package com.michelle.raouf.fetch_jet.feature.pdf_reader

import com.michelle.raouf.fetch_jet.feature.media_extractor.OnDemandAsset
import com.michelle.raouf.fetch_jet.feature.media_extractor.OnDemandLibrary
import com.michelle.raouf.fetch_jet.feature.media_extractor.OnDemandStatus
import com.michelle.raouf.fetch_jet.platform.desktop.DesktopOs
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardViewModel
import kotlinx.coroutines.flow.StateFlow

internal object PdfLibraryPin {
    const val VERSION = "8179"
    const val REPOSITORY = "the-best-is-best/fetchjet-tools"
    const val BUILD = "1"
    const val RELEASE_TAG = "pdfium-$VERSION-$BUILD"
    const val WINDOWS_X64_SHA256 = "d317aec06ffcc94be93484f4f01a98506bb733c1e25af9a19b190af10d801055"
    const val LINUX_X64_SHA256 = "eeb3a0b1810cb31c520df714ff7dc96a78d4e599b11ad75f27fe57b8f04c2d7e"
    const val MACOS_ARM64_SHA256 = "b42b032e0b4ab577ee3b7c352f782dc22c9f189f3bd1b4a111e9d2e7d3a9d3d7"
}

internal val pdfLibrary = OnDemandLibrary(
    name = "PDFium",
    stem = "pdfium",
    envPrefix = "FETCHJET_PDFIUM",
    pinned = {
        val (file, platform, sha) = when (DesktopOs.name) {
            "Windows" -> Triple("pdfium.dll", "windows-x64", PdfLibraryPin.WINDOWS_X64_SHA256)
            "macOS" -> Triple("libpdfium.dylib", "macos-arm64", PdfLibraryPin.MACOS_ARM64_SHA256)
            else -> Triple("libpdfium.so", "linux-x64", PdfLibraryPin.LINUX_X64_SHA256)
        }
        OnDemandAsset(
            file,
            "https://github.com/${PdfLibraryPin.REPOSITORY}/releases/download/${PdfLibraryPin.RELEASE_TAG}/" +
                "pdfium-$platform-${PdfLibraryPin.VERSION}.gz",
            sha,
        )
    },
)

fun isPdfLibraryReady(): Boolean = pdfLibrary.isReady()

fun isPdfLibraryOutdated(): Boolean = pdfLibrary.isOutdated()

fun isPdfLibraryAvailable(): Boolean = pdfLibrary.isAvailable()

fun DashboardViewModel.ensurePdfLibrary(toast: Boolean = true, onReady: () -> Unit) = pdfLibrary.ensure(this, toast, onReady)

fun pdfLibraryStatus(): StateFlow<OnDemandStatus> = pdfLibrary.status
