package com.michelle.raouf.fetch_jet.feature.media_extractor

import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

/**
 * A process's command line is visible to every user of the computer (`ps`, Task Manager), so
 * the proxy login (`--proxy http://user:pass@...`) and the Cookie / Authorization headers go
 * to yt-dlp in a config file only this user can read (`--config-locations`). The file is
 * deleted when the process ends ([deleteWhenDone]).
 */
internal object YtDlpSecretArgs {
    private val SECRET_HEADERS = setOf("cookie", "authorization", "proxy-authorization")

    class Split(val command: List<String>, private val file: File?) {
        fun deleteWhenDone(process: Process) {
            val f = file ?: return
            process.onExit().thenRun { f.delete() }
        }

        fun deleteNow() {
            file?.delete()
        }
    }

    /** [command] = binary + arguments; everything after `--` (the URLs) is left alone. */
    fun split(command: List<String>): Split {
        val keep = mutableListOf<String>()
        val secret = mutableListOf<String>()
        var i = 0
        while (i < command.size) {
            val arg = command[i]
            if (i > 0 && arg == "--") {
                keep += command.subList(i, command.size); break
            }
            val value = command.getOrNull(i + 1)
            if (i > 0 && value != null && isSecret(arg, value)) {
                secret += arg; secret += value; i += 2; continue
            }
            keep += arg; i++
        }
        if (secret.isEmpty()) return Split(command, null)
        val file = try {
            privateTempFile().also { f ->
                f.writeText(
                    secret.chunked(2).joinToString("\n") { (k, v) -> "$k ${quote(v)}" } + "\n")
            }
        } catch (_: Exception) {
            return Split(command, null)
        }
        return Split(
            listOf(keep.first(), "--config-locations", file.absolutePath) + keep.drop(1),
            file
        )
    }

    private fun isSecret(arg: String, value: String): Boolean = when (arg) {
        "--proxy" -> value.substringBefore('/', "").isNotEmpty() && '@' in value
        "--add-headers", "--add-header" ->
            value.substringBefore(':').trim().lowercase() in SECRET_HEADERS

        else -> false
    }

    /** yt-dlp reads config files with shlex (POSIX rules): single quotes, ' as '"'"'. */
    private fun quote(value: String) = "'" + value.replace("'", "'\"'\"'") + "'"

    private fun privateTempFile(): File {
        val dir = File(PathResolver().getTempDirectory()).toPath()
        return try {
            Files.createTempFile(
                dir, "fetchjet-ytdlp-", ".conf",
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))
            ).toFile()
        } catch (_: UnsupportedOperationException) {
            // Windows: the user's own temp folder is already private to that user.
            Files.createTempFile(dir, "fetchjet-ytdlp-", ".conf").toFile()
        }
    }
}
