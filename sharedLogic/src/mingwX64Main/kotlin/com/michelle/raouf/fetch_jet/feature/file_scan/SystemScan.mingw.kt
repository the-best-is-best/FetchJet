package com.michelle.raouf.fetch_jet.feature.file_scan

import com.michelle.raouf.fetch_jet.platform.desktop.DesktopOs
import com.michelle.raouf.fetch_jet.platform.desktop.exists
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.UShortVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.value
import kotlinx.cinterop.wcstr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.posix.GUID
import platform.windows.CoCreateInstance
import platform.windows.CoInitializeEx
import platform.windows.CoUninitialize

private const val CLSID_ATTACHMENT_SERVICES = "4125DD96-E03A-4103-8F70-E0597D803B9C"
private const val IID_ATTACHMENT_EXECUTE = "73DB1241-1E85-4581-8E4F-A81E1D0F8C57"
private const val FETCHJET_CLIENT_GUID = "6E2C5A1B-8F3D-4B7A-9C1E-2D4F6A8B0C3E"
private const val INET_E_SECURITY_PROBLEM = 0x800C000E.toInt()
private const val CLSCTX_INPROC_SERVER = 1u
private const val COINIT_APARTMENTTHREADED = 2u

private const val SLOT_RELEASE = 2
private const val SLOT_SET_CLIENT_TITLE = 3
private const val SLOT_SET_CLIENT_GUID = 4
private const val SLOT_SET_LOCAL_PATH = 5
private const val SLOT_SET_SOURCE = 7
private const val SLOT_SAVE = 11

internal actual fun systemMarkFromInternet(path: String, sourceUrl: String, pageUrl: String?) = Unit

internal actual fun systemScannerAvailable(): Boolean = true

@OptIn(ExperimentalForeignApi::class)
internal actual suspend fun systemScan(path: String, sourceUrl: String): ScanResult? = withContext(Dispatchers.IO) {
    val initialized = CoInitializeEx(null, COINIT_APARTMENTTHREADED)
    try {
        val hr = attachmentSave(path, sourceUrl) ?: return@withContext null
        when {
            hr >= 0 && DesktopOs.exists(path) -> ScanResult(ScanOutcome.CLEAN)
            hr == INET_E_SECURITY_PROBLEM -> ScanResult(ScanOutcome.BLOCKED, "blocked by Windows policy")
            !DesktopOs.exists(path) -> ScanResult(ScanOutcome.THREAT, "removed by the antivirus")
            else -> ScanResult(ScanOutcome.FAILED, "0x" + hr.toUInt().toString(16))
        }
    } finally {
        if (initialized >= 0) CoUninitialize()
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun attachmentSave(path: String, sourceUrl: String): Int? = memScoped {
    val out = alloc<COpaquePointerVar>()
    val created = CoCreateInstance(guid(CLSID_ATTACHMENT_SERVICES).ptr, null, CLSCTX_INPROC_SERVER, guid(IID_ATTACHMENT_EXECUTE).ptr, out.ptr)
    val self = out.value
    if (created < 0 || self == null) return@memScoped null
    val table = self.reinterpret<COpaquePointerVar>().pointed.value!!.reinterpret<COpaquePointerVar>()
    try {
        val setText = { slot: Int, text: String ->
            table[slot]!!.reinterpret<CFunction<(COpaquePointer?, CPointer<UShortVar>?) -> Int>>()(self, text.wcstr.ptr)
        }
        table[SLOT_SET_CLIENT_GUID]!!.reinterpret<CFunction<(COpaquePointer?, COpaquePointer?) -> Int>>()(self, guid(FETCHJET_CLIENT_GUID).ptr)
        setText(SLOT_SET_CLIENT_TITLE, "FetchJet")
        if (setText(SLOT_SET_LOCAL_PATH, path) < 0) return@memScoped null
        val source = sourceUrl.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) || it.startsWith("ftp://", true) }
            ?: "about:internet"
        setText(SLOT_SET_SOURCE, source)
        table[SLOT_SAVE]!!.reinterpret<CFunction<(COpaquePointer?) -> Int>>()(self)
    } finally {
        table[SLOT_RELEASE]!!.reinterpret<CFunction<(COpaquePointer?) -> UInt>>()(self)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun MemScope.guid(text: String): GUID {
    val hex = text.replace("-", "")
    val id = alloc<GUID>()
    id.Data1 = hex.substring(0, 8).toUInt(16)
    id.Data2 = hex.substring(8, 12).toUShort(16)
    id.Data3 = hex.substring(12, 16).toUShort(16)
    for (i in 0 until 8) id.Data4[i] = hex.substring(16 + i * 2, 18 + i * 2).toUByte(16)
    return id
}
