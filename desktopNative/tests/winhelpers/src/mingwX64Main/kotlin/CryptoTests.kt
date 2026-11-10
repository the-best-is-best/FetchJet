import com.michelle.raouf.fetch_jet.platform.win.WinCryptoException
import com.michelle.raouf.fetch_jet.platform.win.WinEcdhP256
import com.michelle.raouf.fetch_jet.platform.win.WinSha256
import com.michelle.raouf.fetch_jet.platform.win.hexToBytes
import com.michelle.raouf.fetch_jet.platform.win.toHexString
import com.michelle.raouf.fetch_jet.platform.win.winAesGcmOpen
import com.michelle.raouf.fetch_jet.platform.win.winAesGcmSeal
import com.michelle.raouf.fetch_jet.platform.win.winDpapiProtect
import com.michelle.raouf.fetch_jet.platform.win.winDpapiUnprotect
import com.michelle.raouf.fetch_jet.platform.win.winEcdsaDerToRaw
import com.michelle.raouf.fetch_jet.platform.win.winEcdsaP256Verify
import com.michelle.raouf.fetch_jet.platform.win.winP256PointFromSpki
import com.michelle.raouf.fetch_jet.platform.win.winRandomBytes
import com.michelle.raouf.fetch_jet.platform.win.winReadFile
import com.michelle.raouf.fetch_jet.platform.win.winReadText
import com.michelle.raouf.fetch_jet.platform.win.winSha256
import com.michelle.raouf.fetch_jet.platform.win.winSha256OfFile
import com.michelle.raouf.fetch_jet.platform.win.winWriteText
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.TimeSource

private const val REAL_UPDATE_KEY =
    "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE55ElrDkiL7DyOHMrcoJj9A7RWTSiJx2/64nxGKTr+1TBHM48ZgCOdDttzZd3C39qRsAXg6WpAwMe1K4+ki53Lg=="

@OptIn(ExperimentalEncodingApi::class)
fun cryptoTests(work: String) {
    val v = loadMap("$work\\vectors.txt")
    T.check("vectors file is present", v.isNotEmpty())
    val made = StringBuilder()

    T.guarded("sha256") {
        T.check(
            "SHA-256 one shot equals Java",
            winSha256(v.getValue("sha.input").hexToBytes()).toHexString() == v.getValue("sha.expected")
        )
        T.check(
            "SHA-256 of nothing",
            winSha256(ByteArray(0)).toHexString() == "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        )
        val mark = TimeSource.Monotonic.markNow()
        val big = winSha256OfFile("$work\\big.bin")
        val took = mark.elapsedNow().inWholeMilliseconds
        T.check("SHA-256 of a 20 MB file read in pieces equals Java", big?.toHexString() == v.getValue("big.sha256"), "$took ms")
        val bytes = winReadFile("$work\\big.bin")!!
        val hash = WinSha256()
        var at = 0
        var step = 1
        while (at < bytes.size) {
            val take = minOf(step, bytes.size - at)
            hash.update(bytes, at, take)
            at += take
            step = step * 3 + 1
        }
        T.check("SHA-256 incremental with odd piece sizes", hash.digest().toHexString() == v.getValue("big.sha256"))
        T.check("missing file gives null", winSha256OfFile("$work\\no-such-file.bin") == null)
    }

    T.guarded("aes-gcm") {
        val key = v.getValue("aes.key").hexToBytes()
        val plain = v.getValue("aes.plain").hexToBytes()
        T.check("AES-256-GCM opens what Java sealed", winAesGcmOpen(key, v.getValue("aes.sealed").hexToBytes()).contentEquals(plain))
        T.check("AES-256-GCM opens a Java sealed empty message", winAesGcmOpen(key, v.getValue("aes.sealed.empty").hexToBytes()).isEmpty())
        val large = winAesGcmOpen(key, winReadFile("$work\\aes-large.sealed")!!)
        T.check("AES-256-GCM opens a 300 KB Java message", winSha256(large).toHexString() == v.getValue("aes.large.sha256"))
        val sealed = winAesGcmSeal(key, plain)
        T.check("sealed layout is nonce 12 + data + tag 16", sealed.size == plain.size + 28)
        T.check("native seal then open", winAesGcmOpen(key, sealed).contentEquals(plain))
        T.check("two seals differ (random nonce)", !winAesGcmSeal(key, plain).contentEquals(sealed))
        made.append("aes.sealed=").append(sealed.toHexString()).append('\n')
        made.append("aes.sealed.empty=").append(winAesGcmSeal(key, ByteArray(0)).toHexString()).append('\n')
        val tampered = sealed.copyOf()
        tampered[14] = (tampered[14].toInt() xor 1).toByte()
        var refused = false
        try {
            winAesGcmOpen(key, tampered)
        } catch (e: WinCryptoException) {
            refused = true
        }
        T.check("a tampered message is refused", refused)
        val wrongKey = key.copyOf()
        wrongKey[0] = (wrongKey[0].toInt() xor 1).toByte()
        refused = false
        try {
            winAesGcmOpen(wrongKey, sealed)
        } catch (e: WinCryptoException) {
            refused = true
        }
        T.check("a wrong key is refused", refused)
        val mark = TimeSource.Monotonic.markNow()
        repeat(2000) { winAesGcmOpen(key, sealed) }
        T.info("AES-GCM open of ${sealed.size} bytes: ${mark.elapsedNow().inWholeMicroseconds / 2000} us each")
    }

    T.guarded("ecdsa") {
        val publicKey = Base64.decode(v.getValue("ecdsa.pub"))
        val otherKey = Base64.decode(v.getValue("ecdsa.other.pub"))
        val signature = Base64.decode(v.getValue("ecdsa.sig.big"))
        val hash = winSha256OfFile("$work\\big.bin")!!
        T.check("ECDSA P-256: a Java signature of the 20 MB file verifies", winEcdsaP256Verify(publicKey, hash, signature))
        T.check("ECDSA: another public key refuses it", !winEcdsaP256Verify(otherKey, hash, signature))
        val otherHash = hash.copyOf()
        otherHash[0] = (otherHash[0].toInt() xor 1).toByte()
        T.check("ECDSA: a changed file refuses it", !winEcdsaP256Verify(publicKey, otherHash, signature))
        val badSignature = signature.copyOf()
        badSignature[badSignature.size - 1] = (badSignature[badSignature.size - 1].toInt() xor 1).toByte()
        T.check("ECDSA: a changed signature refuses it", !winEcdsaP256Verify(publicKey, hash, badSignature))
        T.check("ECDSA: garbage signature refuses it", !winEcdsaP256Verify(publicKey, hash, byteArrayOf(1, 2, 3)))
        T.check("ECDSA: garbage key refuses it", !winEcdsaP256Verify(byteArrayOf(1, 2, 3), hash, signature))
        var many = 0
        var lengths = HashSet<Int>()
        for (i in 0 until 40) {
            val (message, sig) = v.getValue("ecdsa.many.$i").split(':')
            val der = sig.hexToBytes()
            lengths.add(der.size)
            if (winEcdsaP256Verify(publicKey, winSha256(message.hexToBytes()), der)) many++
        }
        T.check("ECDSA: 40 Java signatures of different DER lengths verify", many == 40, "ok=$many lengths=$lengths")
        T.check("DER to raw gives 64 bytes", winEcdsaDerToRaw(signature)?.size == 64)
        T.check("the app's real update key parses as P-256", winP256PointFromSpki(Base64.decode(REAL_UPDATE_KEY))?.size == 64)
    }

    T.guarded("ecdh") {
        val javaPublic = v.getValue("ecdh.java.pub").hexToBytes()
        val mine = WinEcdhP256()
        val derived = mine.deriveSha256(javaPublic)
        T.check("ECDH public key is a 91 byte SPKI", mine.publicSpki.size == 91 && winP256PointFromSpki(mine.publicSpki) != null)
        made.append("ecdh.native.pub=").append(mine.publicSpki.toHexString()).append('\n')
        made.append("ecdh.native.derived=").append(derived.toHexString()).append('\n')
        val other = WinEcdhP256()
        val a = mine.deriveSha256(other.publicSpki)
        val b = other.deriveSha256(mine.publicSpki)
        T.check("two native key pairs derive the same key", a.contentEquals(b) && a.size == 32)
        var refused = false
        try {
            val broken = javaPublic.copyOf()
            broken[40] = (broken[40].toInt() xor 0x55).toByte()
            mine.deriveSha256(broken)
        } catch (e: Throwable) {
            refused = true
        }
        T.check("a point that is not on the curve is refused", refused)
        mine.close()
        other.close()
    }

    T.guarded("random") {
        val a = winRandomBytes(32)
        val b = winRandomBytes(32)
        T.check("secure random gives different bytes", a.size == 32 && !a.contentEquals(b) && a.any { it != 0.toByte() })
        T.check("zero random bytes", winRandomBytes(0).isEmpty())
    }

    T.guarded("secrets") {
        val key = v.getValue("secret.key").hexToBytes()
        made.append("secret.stored=fjenc1:")
            .append(Base64.encode(winAesGcmSeal(key, "native secret سر 🎵".encodeToByteArray()))).append('\n')
    }
    winWriteText("$work\\native.txt", made.toString())
}

@OptIn(ExperimentalEncodingApi::class)
fun secretsTests(work: String) {
    val v = loadMap("$work\\vectors.txt")
    T.guarded("dpapi") {
        val key = v.getValue("secret.key").hexToBytes()
        val keyFile = winReadText("$work\\secrets.key")?.trim().orEmpty()
        T.check("secrets.key written by the PowerShell DPAPI call is present", keyFile.isNotEmpty())
        val loaded = winDpapiUnprotect(Base64.decode(keyFile))
        T.check("native DPAPI opens the key the JVM app's way stored", loaded.contentEquals(key))
        val stored = v.getValue("secret.stored")
        val plain = winAesGcmOpen(loaded, Base64.decode(stored.removePrefix("fjenc1:")))
        T.check(
            "a fjenc1: value written by Java opens with that key",
            plain.contentEquals(v.getValue("secret.plain").hexToBytes()), plain.decodeToString()
        )
        val protectedByNative = winDpapiProtect(key)
        winWriteText("$work\\native-protected.b64", Base64.encode(protectedByNative))
        T.check("native DPAPI round trip", winDpapiUnprotect(protectedByNative).contentEquals(key))
        var refused = false
        try {
            val broken = protectedByNative.copyOf()
            broken[broken.size - 5] = (broken[broken.size - 5].toInt() xor 1).toByte()
            winDpapiUnprotect(broken)
        } catch (e: Throwable) {
            refused = true
        }
        T.check("a damaged DPAPI blob is refused", refused)
    }
}
