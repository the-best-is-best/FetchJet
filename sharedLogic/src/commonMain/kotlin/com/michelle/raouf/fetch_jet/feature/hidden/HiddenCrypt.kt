package com.michelle.raouf.fetch_jet.feature.hidden

import com.michelle.raouf.fetch_jet.core.filesystem.AppFileSystem
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import okio.FileHandle
import okio.Path

internal class HiddenCryptException(message: String) : Exception(message)

internal object HiddenCrypt {
    const val HEADER_SIZE = 20
    const val TAG_SIZE = 32
    const val DEFAULT_CHUNK = 256 * 1024
    private const val VERSION = 1
    private const val MIN_CHUNK = 4096
    private const val MAX_CHUNK = 16 * 1024 * 1024
    private val MAGIC = byteArrayOf(0x46, 0x4A, 0x48, 0x45)

    private class Keys(key: ByteArray) {
        init {
            require(key.size == 32) { "key must be 32 bytes" }
        }

        private val root = key.toByteString()
        val enc: ByteArray = "fjh-enc-v1".encodeUtf8().hmacSha256(root).toByteArray()
        val mac: ByteString = "fjh-mac-v1".encodeUtf8().hmacSha256(root)
    }

    private class Header(val raw: ByteArray, val chunk: Int, val size: Long) {
        val chunks: Long = if (size == 0L) 1L else (size + chunk - 1) / chunk
        val fileLength: Long = HEADER_SIZE + size + chunks * TAG_SIZE

        fun plainLength(index: Long): Int {
            val left = size - index * chunk
            return if (left <= 0L) 0 else if (left < chunk) left.toInt() else chunk
        }

        fun offset(index: Long): Long = HEADER_SIZE + index * (chunk.toLong() + TAG_SIZE)
    }

    private fun headerBytes(chunk: Int, size: Long): ByteArray {
        val h = ByteArray(HEADER_SIZE)
        for (i in 0 until 4) h[i] = MAGIC[i]
        h[4] = VERSION.toByte()
        for (i in 0 until 4) h[8 + i] = (chunk ushr (24 - 8 * i)).toByte()
        for (i in 0 until 8) h[12 + i] = (size ushr (56 - 8 * i)).toByte()
        return h
    }

    private fun parseHeader(h: ByteArray, fileLength: Long): Header {
        for (i in 0 until 4) if (h[i] != MAGIC[i]) throw HiddenCryptException("not an encrypted file")
        if (h[4].toInt() != VERSION) throw HiddenCryptException("unknown version")
        var chunk = 0
        for (i in 0 until 4) chunk = (chunk shl 8) or (h[8 + i].toInt() and 0xff)
        var size = 0L
        for (i in 0 until 8) size = (size shl 8) or (h[12 + i].toLong() and 0xff)
        if (chunk < MIN_CHUNK || chunk > MAX_CHUNK || size < 0L) throw HiddenCryptException("bad header")
        val header = Header(h, chunk, size)
        if (header.fileLength != fileLength) throw HiddenCryptException("file is cut or damaged")
        return header
    }

    private fun nonce(buf: ByteArray, index: Long) {
        for (i in 0 until 8) buf[4 + i] = (index ushr (8 * i)).toByte()
    }

    private fun tagOf(keys: Keys, header: Header, index: Long, data: ByteArray, len: Int): ByteString {
        val b = Buffer()
        b.write(header.raw)
        b.writeLong(index)
        b.write(data, 0, len)
        return b.hmacSha256(keys.mac)
    }

    private fun readFully(h: FileHandle, offset: Long, buf: ByteArray, len: Int) {
        var done = 0
        while (done < len) {
            val n = h.read(offset + done, buf, done, len - done)
            if (n <= 0) throw HiddenCryptException("file ended early")
            done += n
        }
    }

    fun encrypt(src: Path, dst: Path, key: ByteArray, chunk: Int = DEFAULT_CHUNK, onProgress: (Long) -> Unit = {}): Long {
        require(chunk in MIN_CHUNK..MAX_CHUNK)
        val keys = Keys(key)
        val size = AppFileSystem.metadata(src).size ?: throw HiddenCryptException("size unknown")
        val raw = headerBytes(chunk, size)
        val header = Header(raw, chunk, size)
        val input = AppFileSystem.openReadOnly(src)
        try {
            val output = AppFileSystem.openReadWrite(dst, mustCreate = true)
            try {
                output.write(0L, raw, 0, HEADER_SIZE)
                val buf = ByteArray(chunk + TAG_SIZE)
                val nonce = ByteArray(ChaCha20.NONCE_SIZE)
                var written = HEADER_SIZE.toLong()
                var done = 0L
                for (index in 0 until header.chunks) {
                    val len = header.plainLength(index)
                    readFully(input, done, buf, len)
                    nonce(nonce, index)
                    ChaCha20.xor(keys.enc, nonce, 0, buf, 0, buf, 0, len)
                    tagOf(keys, header, index, buf, len).copyInto(0, buf, len, TAG_SIZE)
                    output.write(written, buf, 0, len + TAG_SIZE)
                    written += len + TAG_SIZE
                    done += len
                    onProgress(done)
                }
                output.flush()
            } finally {
                output.close()
            }
        } finally {
            input.close()
        }
        if (AppFileSystem.metadata(src).size != size) throw HiddenCryptException("the file changed while it was being encrypted")
        return size
    }

    class Reader(private val path: Path, key: ByteArray) {
        private val keys = Keys(key)
        private val handle: FileHandle = AppFileSystem.openReadOnly(path)
        private val header: Header
        private val scratch: ByteArray
        private val nonce = ByteArray(ChaCha20.NONCE_SIZE)

        init {
            try {
                val total = handle.size()
                if (total < HEADER_SIZE + TAG_SIZE) throw HiddenCryptException("file is cut or damaged")
                val raw = ByteArray(HEADER_SIZE)
                readFully(handle, 0L, raw, HEADER_SIZE)
                header = parseHeader(raw, total)
                scratch = ByteArray(header.chunk + TAG_SIZE)
            } catch (t: Throwable) {
                handle.close()
                throw t
            }
        }

        val size: Long get() = header.size
        val chunks: Long get() = header.chunks
        val chunkSize: Int get() = header.chunk

        fun readChunk(index: Long, out: ByteArray): Int {
            if (index < 0 || index >= header.chunks) throw HiddenCryptException("no such chunk")
            val len = header.plainLength(index)
            readFully(handle, header.offset(index), scratch, len + TAG_SIZE)
            val expected = tagOf(keys, header, index, scratch, len)
            var diff = 0
            for (i in 0 until TAG_SIZE) diff = diff or (expected[i].toInt() xor scratch[len + i].toInt())
            if (diff != 0) throw HiddenCryptException("the file failed its check")
            nonce(nonce, index)
            ChaCha20.xor(keys.enc, nonce, 0, scratch, 0, out, 0, len)
            return len
        }

        fun close() {
            handle.close()
        }
    }

    fun decrypt(src: Path, dst: Path, key: ByteArray, onProgress: (Long, Long) -> Unit = { _, _ -> }): Long {
        val reader = Reader(src, key)
        try {
            val output = AppFileSystem.openReadWrite(dst, mustCreate = true)
            try {
                val buf = ByteArray(reader.chunkSize)
                var written = 0L
                for (index in 0 until reader.chunks) {
                    val len = reader.readChunk(index, buf)
                    output.write(written, buf, 0, len)
                    written += len
                    onProgress(written, reader.size)
                }
                output.flush()
            } finally {
                output.close()
            }
            return reader.size
        } finally {
            reader.close()
        }
    }

    fun verifyTail(src: Path, key: ByteArray, expectedSize: Long): Boolean = try {
        val reader = Reader(src, key)
        try {
            reader.size == expectedSize && reader.readChunk(reader.chunks - 1, ByteArray(reader.chunkSize)) >= 0
        } finally {
            reader.close()
        }
    } catch (_: HiddenCryptException) {
        false
    }
}
