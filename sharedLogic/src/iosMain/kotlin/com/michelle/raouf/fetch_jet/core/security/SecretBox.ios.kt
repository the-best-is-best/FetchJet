package com.michelle.raouf.fetch_jet.core.security

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreCrypto.CCCrypt
import platform.CoreCrypto.CCHmac
import platform.CoreCrypto.kCCAlgorithmAES
import platform.CoreCrypto.kCCDecrypt
import platform.CoreCrypto.kCCEncrypt
import platform.CoreCrypto.kCCHmacAlgSHA256
import platform.CoreCrypto.kCCOptionPKCS7Padding
import platform.CoreCrypto.kCCSuccess
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.create
import platform.Foundation.dataWithBytes
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecRandomDefault
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.posix.memcpy

/**
 * AES-256-CBC + HMAC-SHA256 (encrypt, then MAC). The 64-byte key is a Keychain item of this
 * device only (`AfterFirstUnlockThisDeviceOnly`): it is not in iCloud / computer backups.
 * An app installed without a signing identity (unsigned simulator build, some sideload tools)
 * gets errSecMissingEntitlement from the Keychain: the key then lives in a file in the app's
 * Library (not Documents, which the Files app shows), excluded from backups.
 */
@OptIn(ExperimentalForeignApi::class)
internal actual object SecretBox {
    private const val SERVICE = "FetchJet"
    private const val ACCOUNT = "secrets-key"
    private const val IV_BYTES = 16
    private const val MAC_BYTES = 32

    private const val MISSING_ENTITLEMENT = -34018

    private val keys: ByteArray by lazy {
        try {
            loadKey() ?: random(64).also {
                storeKey(it)
                check(loadKey()?.contentEquals(it) == true) { "key was not saved" }
            }
        } catch (e: KeychainUnavailable) {
            KeyFile.load() ?: random(64).also { KeyFile.store(it) }
        }
    }

    private class KeychainUnavailable : Exception("no Keychain access (unsigned app)")

    private fun keychainError(what: String, status: Int): Nothing {
        if (status == MISSING_ENTITLEMENT) throw KeychainUnavailable()
        error("Keychain $what $status")
    }

    private val encKey get() = keys.copyOfRange(0, 32)
    private val macKey get() = keys.copyOfRange(32, 64)

    actual fun seal(plain: ByteArray): ByteArray {
        val iv = random(IV_BYTES)
        val body = iv + aes(kCCEncrypt.convert(), iv, plain)
        return body + hmac(body)
    }

    actual fun open(sealed: ByteArray): ByteArray {
        require(sealed.size > IV_BYTES + MAC_BYTES) { "too short" }
        val body = sealed.copyOfRange(0, sealed.size - MAC_BYTES)
        val mac = sealed.copyOfRange(sealed.size - MAC_BYTES, sealed.size)
        var diff = 0
        hmac(body).forEachIndexed { i, b -> diff = diff or (b.toInt() xor mac[i].toInt()) }
        require(diff == 0) { "wrong MAC" }
        return aes(
            kCCDecrypt.convert(),
            body.copyOfRange(0, IV_BYTES),
            body.copyOfRange(IV_BYTES, body.size)
        )
    }

    private fun aes(op: UInt, iv: ByteArray, input: ByteArray): ByteArray {
        val out = ByteArray(input.size + 16)
        val key = encKey
        val written = memScoped {
            val moved = alloc<platform.posix.size_tVar>()
            val status = key.usePinned { k ->
                iv.usePinned { v ->
                    input.usePinned { i ->
                        out.usePinned { o ->
                            CCCrypt(
                                op, kCCAlgorithmAES.convert(), kCCOptionPKCS7Padding.convert(),
                                k.addressOf(0), key.size.convert(), v.addressOf(0),
                                i.addressOf(0), input.size.convert(),
                                o.addressOf(0), out.size.convert(), moved.ptr
                            )
                        }
                    }
                }
            }
            check(status == kCCSuccess) { "CCCrypt $status" }
            moved.value.toInt()
        }
        return out.copyOfRange(0, written)
    }

    private fun hmac(data: ByteArray): ByteArray {
        val out = ByteArray(MAC_BYTES)
        val key = macKey
        key.usePinned { k ->
            data.usePinned { d ->
                out.usePinned { o ->
                    CCHmac(
                        kCCHmacAlgSHA256.convert(), k.addressOf(0), key.size.convert(),
                        d.addressOf(0), data.size.convert(), o.addressOf(0)
                    )
                }
            }
        }
        return out
    }

    actual fun randomBytes(n: Int): ByteArray = random(n)

    private fun random(n: Int): ByteArray {
        val out = ByteArray(n)
        val status =
            out.usePinned { SecRandomCopyBytes(kSecRandomDefault, n.convert(), it.addressOf(0)) }
        check(status == 0) { "SecRandomCopyBytes $status" }
        return out
    }

    private fun baseQuery(): platform.CoreFoundation.CFMutableDictionaryRef? {
        val q = CFDictionaryCreateMutable(
            null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr
        )
        CFDictionaryAddValue(q, kSecClass, kSecClassGenericPassword)
        addBridged(q, kSecAttrService, NSString.create(string = SERVICE))
        addBridged(q, kSecAttrAccount, NSString.create(string = ACCOUNT))
        return q
    }

    private fun addBridged(
        q: platform.CoreFoundation.CFMutableDictionaryRef?,
        key: kotlinx.cinterop.CPointer<*>?,
        value: Any
    ) {
        val ref = CFBridgingRetain(value)
        CFDictionaryAddValue(q, key, ref)
        CFRelease(ref)
    }

    private fun loadKey(): ByteArray? = memScoped {
        val q = baseQuery()
        CFDictionaryAddValue(q, kSecReturnData, kCFBooleanTrue)
        CFDictionaryAddValue(q, kSecMatchLimit, kSecMatchLimitOne)
        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(q, result.ptr)
        CFRelease(q)
        when (status) {
            errSecSuccess -> (CFBridgingRelease(result.value) as NSData).toByteArray()
            errSecItemNotFound -> null
            else -> keychainError("read", status)
        }
    }

    private fun storeKey(key: ByteArray) {
        val q = baseQuery()
        CFDictionaryAddValue(
            q,
            kSecAttrAccessible,
            kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        )
        addBridged(q, kSecValueData, key.toNSData())
        val status = SecItemAdd(q, null)
        CFRelease(q)
        if (status != errSecSuccess) keychainError("write", status)
    }

    private fun NSData.toByteArray(): ByteArray {
        val out = ByteArray(length.toInt())
        if (out.isNotEmpty()) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
        return out
    }

    private fun ByteArray.toNSData(): NSData =
        usePinned { NSData.dataWithBytes(it.addressOf(0), size.convert()) }
}
