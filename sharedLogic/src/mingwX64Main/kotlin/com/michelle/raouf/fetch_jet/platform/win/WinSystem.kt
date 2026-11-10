@file:OptIn(ExperimentalForeignApi::class)

package com.michelle.raouf.fetch_jet.platform.win

import kotlinx.cinterop.CFunction
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.UShortVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.toKStringFromUtf16
import kotlinx.cinterop.toLong
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.cinterop.wcstr
import platform.posix.GUID
import platform.posix.memcpy
import platform.windows.CF_UNICODETEXT
import platform.windows.CRYPTPROTECT_UI_FORBIDDEN
import platform.windows.CloseClipboard
import platform.windows.CloseHandle
import platform.windows.CoInitializeEx
import platform.windows.CoTaskMemFree
import platform.windows.CoUninitialize
import platform.windows.CreateMutexW
import platform.windows.CryptProtectData
import platform.windows.CryptUnprotectData
import platform.windows.DATA_BLOB
import platform.windows.ERROR_ALREADY_EXISTS
import platform.windows.EmptyClipboard
import platform.windows.FreeLibrary
import platform.windows.GMEM_MOVEABLE
import platform.windows.GetClipboardData
import platform.windows.GetClipboardSequenceNumber
import platform.windows.GetLastError
import platform.windows.GetLocalTime
import platform.windows.GetProcAddress
import platform.windows.GetTimeZoneInformation
import platform.windows.GlobalAlloc
import platform.windows.GlobalFree
import platform.windows.GlobalLock
import platform.windows.GlobalUnlock
import platform.windows.HANDLE
import platform.windows.HKEY
import platform.windows.HKEYVar
import platform.windows.HKEY_CURRENT_USER
import platform.windows.HKEY_LOCAL_MACHINE
import platform.windows.ILCreateFromPathW
import platform.windows.ILFree
import platform.windows.IsClipboardFormatAvailable
import platform.windows.KEY_READ
import platform.windows.KEY_SET_VALUE
import platform.windows.LoadLibraryW
import platform.windows.LocalFree
import platform.windows.OpenClipboard
import platform.windows.REG_EXPAND_SZ
import platform.windows.REG_SZ
import platform.windows.RegCloseKey
import platform.windows.RegCreateKeyExW
import platform.windows.RegDeleteTreeW
import platform.windows.RegDeleteValueW
import platform.windows.RegEnumKeyExW
import platform.windows.RegOpenKeyExW
import platform.windows.RegQueryValueExW
import platform.windows.RegSetValueExW
import platform.windows.SHGetKnownFolderPath
import platform.windows.SHOpenFolderAndSelectItems
import platform.windows.SW_SHOWNORMAL
import platform.windows.SYSTEMTIME
import platform.windows.SetClipboardData
import platform.windows.ShellExecuteW
import platform.windows.Sleep
import platform.windows.TIME_ZONE_INFORMATION

fun winDpapiProtect(plain: ByteArray): ByteArray = dpapi(plain, protect = true)

fun winDpapiUnprotect(blob: ByteArray): ByteArray = dpapi(blob, protect = false)

private fun dpapi(input: ByteArray, protect: Boolean): ByteArray = memScoped {
    require(input.isNotEmpty()) { "nothing to protect" }
    val source = alloc<DATA_BLOB>()
    val target = alloc<DATA_BLOB>()
    target.cbData = 0u
    target.pbData = null
    val ok = input.usePinned {
        source.cbData = input.size.toUInt()
        source.pbData = it.addressOf(0).reinterpret()
        if (protect) {
            CryptProtectData(source.ptr, null as String?, null, null, null, CRYPTPROTECT_UI_FORBIDDEN.toUInt(), target.ptr)
        } else {
            CryptUnprotectData(source.ptr, null, null, null, null, CRYPTPROTECT_UI_FORBIDDEN.toUInt(), target.ptr)
        }
    }
    if (ok == 0) winFail(if (protect) "CryptProtectData" else "CryptUnprotectData")
    val data = target.pbData
    val out = if (data == null) ByteArray(0) else data.readBytes(target.cbData.toInt())
    if (data != null) LocalFree(data)
    out
}

enum class WinRegRoot { CURRENT_USER, LOCAL_MACHINE }

const val WIN_REG_VIEW_64 = 0x0100u
const val WIN_REG_VIEW_32 = 0x0200u

private fun rootKey(root: WinRegRoot): HKEY? = when (root) {
    WinRegRoot.CURRENT_USER -> HKEY_CURRENT_USER
    WinRegRoot.LOCAL_MACHINE -> HKEY_LOCAL_MACHINE
}

private inline fun <T> withRegKey(root: WinRegRoot, path: String, access: UInt, fallback: T, block: (HKEY?) -> T): T =
    memScoped {
        val key = alloc<HKEYVar>()
        if (RegOpenKeyExW(rootKey(root), path, 0u, access, key.ptr) != 0) return fallback
        try {
            block(key.value)
        } finally {
            RegCloseKey(key.value)
        }
    }

fun winRegKeyExists(root: WinRegRoot, path: String, view: UInt = 0u): Boolean =
    withRegKey(root, path, KEY_READ.toUInt() or view, false) { true }

fun winRegReadString(root: WinRegRoot, path: String, name: String?, view: UInt = 0u): String? =
    withRegKey(root, path, KEY_READ.toUInt() or view, null) { key ->
        memScoped {
            val type = alloc<UIntVar>()
            val size = alloc<UIntVar>()
            size.value = 0u
            if (RegQueryValueExW(key, name, null, type.ptr, null, size.ptr) != 0) return@memScoped null
            if (type.value != REG_SZ.toUInt() && type.value != REG_EXPAND_SZ.toUInt()) return@memScoped null
            val bytes = size.value.toInt()
            if (bytes <= 0) return@memScoped ""
            val buffer = allocArray<UShortVar>(bytes / 2 + 2)
            size.value = bytes.toUInt()
            if (RegQueryValueExW(key, name, null, type.ptr, buffer.reinterpret(), size.ptr) != 0) return@memScoped null
            buffer[(size.value.toInt() / 2)] = 0u
            buffer.toKStringFromUtf16()
        }
    }

fun winRegWriteString(root: WinRegRoot, path: String, name: String?, value: String): Boolean = memScoped {
    val key = alloc<HKEYVar>()
    if (RegCreateKeyExW(rootKey(root), path, 0u, null, 0u, KEY_SET_VALUE.toUInt(), null, key.ptr, null) != 0) {
        return false
    }
    try {
        val bytes = ((value.length + 1) * 2).toUInt()
        RegSetValueExW(key.value, name, 0u, REG_SZ.toUInt(), value.wcstr.ptr.reinterpret<UByteVar>(), bytes) == 0
    } finally {
        RegCloseKey(key.value)
    }
}

fun winRegDeleteValue(root: WinRegRoot, path: String, name: String?): Boolean =
    withRegKey(root, path, KEY_SET_VALUE.toUInt(), false) { key -> RegDeleteValueW(key, name) == 0 }

fun winRegDeleteTree(root: WinRegRoot, path: String): Boolean = RegDeleteTreeW(rootKey(root), path) == 0

fun winRegSubKeys(root: WinRegRoot, path: String, view: UInt = 0u): List<String> =
    withRegKey(root, path, KEY_READ.toUInt() or view, emptyList()) { key ->
        memScoped {
            val names = ArrayList<String>()
            val buffer = allocArray<UShortVar>(260)
            val size = alloc<UIntVar>()
            var index = 0u
            while (true) {
                size.value = 260u
                if (RegEnumKeyExW(key, index, buffer, size.ptr, null, null, null, null) != 0) break
                names.add(buffer.toKStringFromUtf16())
                index++
            }
            names
        }
    }

fun winShellExecute(verb: String?, target: String, parameters: String? = null, directory: String? = null): Long =
    ShellExecuteW(null, verb, target, parameters, directory, SW_SHOWNORMAL).toLong()

fun winShellOpen(target: String, parameters: String? = null, directory: String? = null): Boolean =
    winShellExecute("open", target, parameters, directory) > 32

fun winRevealInExplorer(path: String): Boolean {
    val full = winFullPath(path.replace('/', '\\'))
    val initialized = CoInitializeEx(null, 2u)
    try {
        val shown = memScoped {
            val item = ILCreateFromPathW(full.wcstr.ptr) ?: return@memScoped false
            val result = SHOpenFolderAndSelectItems(item, 0u, null, 0u)
            ILFree(item)
            result >= 0
        }
        if (shown) return true
    } finally {
        if (initialized >= 0) CoUninitialize()
    }
    return ShellExecuteW(null, "open", "explorer.exe", "/select,\"$full\"", null, SW_SHOWNORMAL).toLong() > 32
}

enum class WinKnownFolder(internal val guid: String) {
    DOWNLOADS("374DE290-123F-4565-9164-39C4925E467B"),
    DOCUMENTS("FDD39AD0-238F-46AF-ADB4-6C85480369C7"),
    DESKTOP("B4BFCC3A-DB2C-424C-B029-7FE99A87C641"),
    VIDEOS("18989B1D-99B5-455B-841C-AB7C74E4DDFC"),
    MUSIC("4BD8D571-6D19-48D3-BE97-422220080E43"),
    PICTURES("33E28130-4E1E-4676-835A-98395C3BC3BB"),
    LOCAL_APP_DATA("F1B32785-6FBA-4FCF-9D55-7B8E7F157091"),
    ROAMING_APP_DATA("3EB685DB-65F9-4CF6-A03A-E3EF65729F3D"),
    PROGRAM_FILES("905E63B6-C1BF-494E-B29C-65B732D3D21A"),
    PROGRAM_FILES_X86("7C5A40EF-A0FB-4BFC-874A-C0F2E0B9FA8E"),
}

fun winKnownFolder(folder: WinKnownFolder): String? = memScoped {
    val text = folder.guid.replace("-", "")
    val id = alloc<GUID>()
    id.Data1 = text.substring(0, 8).toUInt(16)
    id.Data2 = text.substring(8, 12).toUShort(16)
    id.Data3 = text.substring(12, 16).toUShort(16)
    for (i in 0 until 8) id.Data4[i] = text.substring(16 + i * 2, 18 + i * 2).toUByte(16)
    val out = alloc<CPointerVar<UShortVar>>()
    if (SHGetKnownFolderPath(id.ptr, 0u, null, out.ptr) < 0) return null
    val path = out.value?.toKStringFromUtf16()
    CoTaskMemFree(out.value)
    path
}

fun winClipboardSequence(): UInt = GetClipboardSequenceNumber()

private fun openClipboard(): Boolean {
    repeat(5) {
        if (OpenClipboard(null) != 0) return true
        Sleep(10u)
    }
    return false
}

fun winClipboardText(): String? {
    if (IsClipboardFormatAvailable(CF_UNICODETEXT.toUInt()) == 0) return null
    if (!openClipboard()) return null
    try {
        val handle = GetClipboardData(CF_UNICODETEXT.toUInt()) ?: return null
        val locked = GlobalLock(handle)?.reinterpret<UShortVar>() ?: return null
        val text = locked.toKStringFromUtf16()
        GlobalUnlock(handle)
        return text
    } finally {
        CloseClipboard()
    }
}

fun winSetClipboardText(text: String): Boolean {
    if (!openClipboard()) return false
    try {
        EmptyClipboard()
        val bytes = (text.length + 1) * 2
        val memory = GlobalAlloc(GMEM_MOVEABLE.toUInt(), bytes.convert()) ?: return false
        val locked = GlobalLock(memory)
        if (locked == null) {
            GlobalFree(memory)
            return false
        }
        memScoped { memcpy(locked, text.wcstr.ptr, bytes.convert()) }
        GlobalUnlock(memory)
        if (SetClipboardData(CF_UNICODETEXT.toUInt(), memory) == null) {
            GlobalFree(memory)
            return false
        }
        return true
    } finally {
        CloseClipboard()
    }
}

fun winLocalUtcOffsetMs(): Long = memScoped {
    val zone = alloc<TIME_ZONE_INFORMATION>()
    val kind = GetTimeZoneInformation(zone.ptr)
    val extra = when (kind) {
        2u -> zone.DaylightBias
        1u -> zone.StandardBias
        else -> 0
    }
    -(zone.Bias + extra) * 60_000L
}

fun winLocalMinutesOfDay(): Int = memScoped {
    val now = alloc<SYSTEMTIME>()
    GetLocalTime(now.ptr)
    now.wHour.toInt() * 60 + now.wMinute.toInt()
}

fun winCanSuspend(): Boolean {
    val library = LoadLibraryW("powrprof.dll") ?: return false
    val found = GetProcAddress(library, "SetSuspendState") != null
    FreeLibrary(library)
    return found
}

fun winSuspend(hibernate: Boolean = false): Boolean {
    val library = LoadLibraryW("powrprof.dll") ?: return false
    try {
        val function = GetProcAddress(library, "SetSuspendState")
            ?.reinterpret<CFunction<(UByte, UByte, UByte) -> UByte>>() ?: return false
        return function(if (hibernate) 1u else 0u, 0u, 0u) != 0.toUByte()
    } finally {
        FreeLibrary(library)
    }
}

class WinNamedMutex(val handle: HANDLE?, val alreadyExisted: Boolean) {
    fun close() {
        if (handle != null) CloseHandle(handle)
    }
}

fun winCreateNamedMutex(name: String): WinNamedMutex {
    val handle = CreateMutexW(null, 0, name)
    return WinNamedMutex(handle, handle != null && GetLastError() == ERROR_ALREADY_EXISTS.toUInt())
}
