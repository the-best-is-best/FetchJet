package com.michelle.raouf.fetch_jet.core.log

import java.io.ByteArrayOutputStream
import java.io.OutputStream

/** Writes [out] line by line through [LogRedact] (for the log files). */
class RedactingOutputStream(private val out: OutputStream) : OutputStream() {
    private val line = ByteArrayOutputStream()

    @Synchronized
    override fun write(b: Int) {
        line.write(b)
        if (b == '\n'.code) emit()
    }

    @Synchronized
    override fun write(buf: ByteArray, off: Int, len: Int) {
        for (i in off until off + len) write(buf[i].toInt())
    }

    @Synchronized
    override fun flush() {
        out.flush()
    }

    @Synchronized
    override fun close() {
        if (line.size() > 0) emit()
        out.close()
    }

    private fun emit() {
        out.write(LogRedact.redact(line.toString(Charsets.UTF_8)).toByteArray(Charsets.UTF_8))
        line.reset()
    }
}
