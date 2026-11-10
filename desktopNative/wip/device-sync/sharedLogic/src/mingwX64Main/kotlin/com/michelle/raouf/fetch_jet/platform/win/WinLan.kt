@file:OptIn(ExperimentalForeignApi::class)

package com.michelle.raouf.fetch_jet.platform.win

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.UShortVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKStringFromUtf16
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.posix.AF_INET
import platform.posix.INVALID_SOCKET
import platform.posix.SOCK_DGRAM
import platform.posix.WSAData
import platform.posix.WSAGetLastError
import platform.posix.WSAIoctl
import platform.posix.WSAStartup
import platform.posix.closesocket
import platform.posix.socket
import platform.windows.COMPUTER_NAME_FORMAT
import platform.windows.GetComputerNameExW

private const val SIO_GET_INTERFACE_LIST_CODE = 0x4004747Fu
private const val SIO_UDP_CONNRESET_CODE = 0x9800000Cu
private const val INTERFACE_INFO_BYTES = 76
private const val INTERFACE_ADDRESS_AT = 8
private const val INTERFACE_MASK_AT = 56
private const val INTERFACE_UP = 0x1
private const val INTERFACE_LOOPBACK = 0x4
private const val WSA_FAULT = 10014

private val winsockStarted: Boolean by lazy {
    memScoped {
        val data = alloc<WSAData>()
        WSAStartup(0x0202u, data.ptr) == 0
    }
}

class WinIpv4Interface(
    val address: String,
    val prefixBits: Int,
    val broadcast: String,
    val up: Boolean,
    val loopback: Boolean,
)

fun winHostName(): String = memScoped {
    val size = alloc<UIntVar>()
    size.value = 0u
    GetComputerNameExW(COMPUTER_NAME_FORMAT.ComputerNameDnsHostname, null, size.ptr)
    val needed = size.value.toInt()
    if (needed <= 0) return ""
    val buffer = allocArray<UShortVar>(needed + 1)
    size.value = (needed + 1).toUInt()
    if (GetComputerNameExW(COMPUTER_NAME_FORMAT.ComputerNameDnsHostname, buffer, size.ptr) == 0) ""
    else buffer.toKStringFromUtf16()
}

private fun ipv4At(bytes: ByteArray, at: Int): Int =
    ((bytes[at].toInt() and 0xFF) shl 24) or ((bytes[at + 1].toInt() and 0xFF) shl 16) or
        ((bytes[at + 2].toInt() and 0xFF) shl 8) or (bytes[at + 3].toInt() and 0xFF)

private fun ipv4Text(value: Int): String =
    "${value ushr 24}.${(value ushr 16) and 0xFF}.${(value ushr 8) and 0xFF}.${value and 0xFF}"

fun winIpv4Interfaces(): List<WinIpv4Interface> {
    if (!winsockStarted) return emptyList()
    val handle = socket(AF_INET, SOCK_DGRAM, 0)
    if (handle == INVALID_SOCKET) return emptyList()
    try {
        var capacity = 32
        while (capacity <= 2048) {
            val bytes = ByteArray(capacity * INTERFACE_INFO_BYTES)
            val used = memScoped {
                val returned = alloc<UIntVar>()
                returned.value = 0u
                val status = bytes.usePinned {
                    WSAIoctl(
                        handle, SIO_GET_INTERFACE_LIST_CODE, null, 0u, it.addressOf(0), bytes.size.toUInt(),
                        returned.ptr, null, null
                    )
                }
                when {
                    status == 0 -> returned.value.toInt()
                    WSAGetLastError() == WSA_FAULT -> -2
                    else -> -1
                }
            }
            if (used == -2) {
                capacity *= 4
                continue
            }
            if (used < 0) return emptyList()
            val found = ArrayList<WinIpv4Interface>()
            var at = 0
            while (at + INTERFACE_INFO_BYTES <= used) {
                val flags = bytes[at].toInt() and 0xFF
                val address = ipv4At(bytes, at + INTERFACE_ADDRESS_AT)
                val mask = ipv4At(bytes, at + INTERFACE_MASK_AT)
                found.add(
                    WinIpv4Interface(
                        address = ipv4Text(address),
                        prefixBits = mask.countOneBits(),
                        broadcast = ipv4Text((address and mask) or mask.inv()),
                        up = (flags and INTERFACE_UP) != 0,
                        loopback = (flags and INTERFACE_LOOPBACK) != 0,
                    )
                )
                at += INTERFACE_INFO_BYTES
            }
            return found
        }
        return emptyList()
    } finally {
        closesocket(handle)
    }
}

fun winBroadcastAddresses(): List<String> =
    winIpv4Interfaces().filter { it.up && !it.loopback && it.prefixBits in 1..31 }.map { it.broadcast }.distinct()

fun winUdpIgnoreUnreachable(socketHandle: ULong): Boolean = memScoped {
    val off = alloc<UIntVar>()
    off.value = 0u
    val returned = alloc<UIntVar>()
    returned.value = 0u
    WSAIoctl(socketHandle, SIO_UDP_CONNRESET_CODE, off.ptr, 4u, null, 0u, returned.ptr, null, null) == 0
}
