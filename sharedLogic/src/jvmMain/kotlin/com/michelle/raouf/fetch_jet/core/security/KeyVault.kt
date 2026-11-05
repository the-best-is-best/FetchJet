package com.michelle.raouf.fetch_jet.core.security

import java.io.File
import java.util.HexFormat
import java.util.concurrent.TimeUnit

/** Where one desktop OS keeps FetchJet's secrets key. */
internal interface KeyVault {
    /** The key, or null when none was saved yet. Throws when the store cannot be read now. */
    fun load(): ByteArray?
    fun store(key: ByteArray)
}

internal object KeyVaultTools {
    const val SERVICE = "FetchJet"
    const val ACCOUNT = "secrets-key"

    val settingsDir: File get() = File(System.getProperty("user.home") ?: ".", ".fetch_jet")

    fun hex(bytes: ByteArray): String = HexFormat.of().formatHex(bytes)
    fun unhex(text: String): ByteArray = HexFormat.of().parseHex(text.trim())

    class Result(val exit: Int, val out: String)

    /** Runs [command] with [input] on stdin (secrets never go on the command line). */
    fun run(vararg command: String, input: String = ""): Result {
        val p = ProcessBuilder(*command).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        p.outputStream.use { it.write(input.toByteArray()) }
        // Wait before reading: readText() blocks until the tool exits, so a keyring that shows
        // an unlock / new-password prompt nobody answers would hang the app (and the bridge).
        if (!p.waitFor(15, TimeUnit.SECONDS)) {
            p.destroyForcibly(); error("${command.first()} timed out")
        }
        val out = p.inputStream.bufferedReader().readText()
        return Result(p.exitValue(), out.trim())
    }

    /** A file only this user can read (POSIX 600). */
    fun writePrivate(file: File, text: String) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.delete()
        tmp.createNewFile()
        tmp.setReadable(false, false); tmp.setReadable(true, true)
        tmp.setWritable(false, false); tmp.setWritable(true, true)
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.delete(); tmp.renameTo(file)
        }
    }
}
