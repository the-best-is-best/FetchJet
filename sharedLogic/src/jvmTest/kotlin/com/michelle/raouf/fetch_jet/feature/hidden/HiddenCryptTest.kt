package com.michelle.raouf.fetch_jet.feature.hidden

import okio.Path.Companion.toPath
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.ChaCha20ParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HiddenCryptTest {
    private lateinit var dir: File
    private val random = SecureRandom()

    @BeforeTest
    fun setUp() {
        dir = File.createTempFile("fjcrypt", "").also { it.delete(); it.mkdirs() }
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun hex(s: String): ByteArray = s.replace(" ", "").replace("\n", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private val rfcKey = ByteArray(32) { it.toByte() }

    @Test
    fun rfc8439BlockFunction() {
        val nonce = hex("000000090000004a00000000")
        val block = ByteArray(64).also { ChaCha20.xor(rfcKey, nonce, 1, ByteArray(64), 0, it, 0, 64) }
        val expected = hex(
            "10f1e7e4d13b5915500fdd1fa32071c4c7d1f4c733c068030422aa9ac3d46c4e" +
                    "d2826446079faa0914c2d705d98b02a2b5129cd1de164eb9cbd083e8a2503c4e"
        )
        assertContentEquals(expected, block)
    }

    @Test
    fun rfc8439Encryption() {
        val nonce = hex("000000000000004a00000000")
        val plain = "Ladies and Gentlemen of the class of '99: If I could offer you only one tip for the future, sunscreen would be it."
            .encodeToByteArray()
        val out = ByteArray(plain.size)
        ChaCha20.xor(rfcKey, nonce, 1, plain, 0, out, 0, plain.size)
        val expected = hex(
            "6e2e359a2568f98041ba0728dd0d6981e97e7aec1d4360c20a27afccfd9fae0b" +
                    "f91b65c5524733ab8f593dabcd62b3571639d624e65152ab8f530c359f0861d8" +
                    "07ca0dbf500d6a6156a38e088a22b65e52bc514d16ccf806818ce91ab7793736" +
                    "5af90bbf74a35be6b40b8eedf2785e42874d"
        )
        assertContentEquals(expected, out)
    }

    @Test
    fun matchesTheJdkImplementationOnOddLengthsAndCounters() {
        for (len in listOf(0, 1, 63, 64, 65, 127, 128, 1000, 4096, 65537)) {
            val key = ByteArray(32).also { random.nextBytes(it) }
            val nonce = ByteArray(12).also { random.nextBytes(it) }
            val counter = random.nextInt(1000)
            val data = ByteArray(len).also { random.nextBytes(it) }
            val mine = ByteArray(len)
            ChaCha20.xor(key, nonce, counter, data, 0, mine, 0, len)
            val jdk = Cipher.getInstance("ChaCha20")
            jdk.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "ChaCha20"), ChaCha20ParameterSpec(nonce, counter))
            assertContentEquals(jdk.doFinal(data), mine, "length $len")
        }
    }

    @Test
    fun xorInPlaceWithOffsets() {
        val key = ByteArray(32) { (it * 7).toByte() }
        val nonce = ByteArray(12) { it.toByte() }
        val data = ByteArray(300) { it.toByte() }
        val whole = ByteArray(300)
        ChaCha20.xor(key, nonce, 0, data, 0, whole, 0, 300)
        val buf = data.copyOf()
        ChaCha20.xor(key, nonce, 0, buf, 0, buf, 0, 300)
        assertContentEquals(whole, buf)
        val shifted = ByteArray(310)
        data.copyInto(shifted, 10)
        ChaCha20.xor(key, nonce, 0, shifted, 10, shifted, 5, 300)
        assertContentEquals(whole, shifted.copyOfRange(5, 305))
    }

    private fun write(name: String, size: Int): File {
        val f = File(dir, name)
        val data = ByteArray(size).also { random.nextBytes(it) }
        f.writeBytes(data)
        return f
    }

    private fun roundTrip(size: Int, chunk: Int) {
        val key = ByteArray(32).also { random.nextBytes(it) }
        val plain = write("p$size-$chunk", size)
        val enc = File(dir, "e$size-$chunk")
        val back = File(dir, "b$size-$chunk")
        val n = HiddenCrypt.encrypt(plain.absolutePath.toPath(), enc.absolutePath.toPath(), key, chunk)
        assertEquals(size.toLong(), n)
        val chunks = if (size == 0) 1L else (size.toLong() + chunk - 1) / chunk
        assertEquals(HiddenCrypt.HEADER_SIZE + size + chunks * HiddenCrypt.TAG_SIZE, enc.length())
        assertTrue(HiddenCrypt.verifyTail(enc.absolutePath.toPath(), key, size.toLong()))
        if (size >= 64) assertFalse(enc.readBytes().copyOfRange(20, 52).contentEquals(plain.readBytes().copyOf(32)))
        HiddenCrypt.decrypt(enc.absolutePath.toPath(), back.absolutePath.toPath(), key)
        assertContentEquals(plain.readBytes(), back.readBytes(), "size $size chunk $chunk")
    }

    @Test
    fun roundTripsOfEveryShape() {
        val chunk = 4096
        for (size in listOf(0, 1, 63, 64, 65, chunk - 1, chunk, chunk + 1, 2 * chunk, 3 * chunk + 17, 10 * chunk)) roundTrip(size, chunk)
        roundTrip(700_000, HiddenCrypt.DEFAULT_CHUNK)
        roundTrip(2 * HiddenCrypt.DEFAULT_CHUNK, HiddenCrypt.DEFAULT_CHUNK)
    }

    @Test
    fun aWrongKeyFails() {
        val key = ByteArray(32).also { random.nextBytes(it) }
        val plain = write("p", 10_000)
        val enc = File(dir, "e")
        HiddenCrypt.encrypt(plain.absolutePath.toPath(), enc.absolutePath.toPath(), key, 4096)
        val other = ByteArray(32).also { random.nextBytes(it) }
        assertFailsWith<HiddenCryptException> {
            HiddenCrypt.decrypt(enc.absolutePath.toPath(), File(dir, "out").absolutePath.toPath(), other)
        }
    }

    @Test
    fun anyFlippedBitIsCaught() {
        val key = ByteArray(32).also { random.nextBytes(it) }
        val plain = write("p", 9_000)
        val enc = File(dir, "e")
        HiddenCrypt.encrypt(plain.absolutePath.toPath(), enc.absolutePath.toPath(), key, 4096)
        val good = enc.readBytes()
        val spots = listOf(0, 5, 9, 14, 20, 100, 4096 + 20, 4096 + 20 + 32 + 5, good.size - 1, good.size - 40)
        for (at in spots) {
            val bad = good.copyOf()
            bad[at] = (bad[at].toInt() xor 1).toByte()
            val f = File(dir, "bad$at")
            f.writeBytes(bad)
            assertFailsWith<HiddenCryptException> {
                HiddenCrypt.decrypt(f.absolutePath.toPath(), File(dir, "out$at").absolutePath.toPath(), key)
            }
        }
    }

    @Test
    fun aCutOrExtendedFileIsCaught() {
        val key = ByteArray(32).also { random.nextBytes(it) }
        val plain = write("p", 9_000)
        val enc = File(dir, "e")
        HiddenCrypt.encrypt(plain.absolutePath.toPath(), enc.absolutePath.toPath(), key, 4096)
        val good = enc.readBytes()
        val cut = File(dir, "cut").also { it.writeBytes(good.copyOf(good.size - 40)) }
        val more = File(dir, "more").also { it.writeBytes(good + byteArrayOf(1, 2, 3)) }
        assertFalse(HiddenCrypt.verifyTail(cut.absolutePath.toPath(), key, 9_000))
        assertFalse(HiddenCrypt.verifyTail(more.absolutePath.toPath(), key, 9_000))
        assertFalse(HiddenCrypt.verifyTail(File(dir, "e").absolutePath.toPath(), key, 9_001))
        assertTrue(HiddenCrypt.verifyTail(File(dir, "e").absolutePath.toPath(), key, 9_000))
    }

    @Test
    fun chunksOpenOnTheirOwnInAnyOrder() {
        val key = ByteArray(32).also { random.nextBytes(it) }
        val plain = write("p", 5 * 4096 + 100)
        val enc = File(dir, "e")
        HiddenCrypt.encrypt(plain.absolutePath.toPath(), enc.absolutePath.toPath(), key, 4096)
        val data = plain.readBytes()
        val reader = HiddenCrypt.Reader(enc.absolutePath.toPath(), key)
        try {
            val out = ByteArray(reader.chunkSize)
            for (index in listOf(5L, 0L, 3L, 5L, 1L)) {
                val n = reader.readChunk(index, out)
                val from = (index * 4096).toInt()
                assertContentEquals(data.copyOfRange(from, from + n), out.copyOf(n))
            }
            assertEquals(6L, reader.chunks)
        } finally {
            reader.close()
        }
    }

    @Test
    fun throughput() {
        val size = 96 * 1024 * 1024
        val plain = File(dir, "big")
        val block = ByteArray(1 shl 20).also { random.nextBytes(it) }
        plain.outputStream().use { out -> repeat(size / block.size) { out.write(block) } }
        val key = ByteArray(32).also { random.nextBytes(it) }
        val enc = File(dir, "big.enc")
        val back = File(dir, "big.back")
        HiddenCrypt.encrypt(plain.absolutePath.toPath(), File(dir, "warm").absolutePath.toPath(), key)
        File(dir, "warm").delete()
        val t0 = System.nanoTime()
        HiddenCrypt.encrypt(plain.absolutePath.toPath(), enc.absolutePath.toPath(), key)
        val t1 = System.nanoTime()
        HiddenCrypt.decrypt(enc.absolutePath.toPath(), back.absolutePath.toPath(), key)
        val t2 = System.nanoTime()
        val mb = size / 1048576.0
        println("HIDDEN-CRYPT encrypt %.0f MB/s, decrypt %.0f MB/s".format(mb / ((t1 - t0) / 1e9), mb / ((t2 - t1) / 1e9)))
        assertEquals(plain.length(), back.length())
        assertTrue(mb / ((t1 - t0) / 1e9) > 20.0, "encrypt is too slow")
    }
}
