package com.michelle.raouf.fetch_jet.feature.http_downloader

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Connection
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.connection
import io.ktor.network.tls.tls
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

internal class FtpException(message: String, val code: Int = 0) : Exception(message)

internal class FtpReply(val code: Int, val text: String) {
    val firstLine: String get() = text.substringBefore('\n')
}

/**
 * One FTP control connection (plain, or TLS from the start / after AUTH TLS) with the few commands a
 * download needs: login, SIZE, MDTM, REST + RETR over a passive data connection (also TLS when the
 * control connection is). No proxy support and no TLS session reuse for the data connection.
 */
internal class FtpSession private constructor(private val target: FtpTarget) {
    private val selector = SelectorManager(Dispatchers.IO)
    private lateinit var control: Connection
    private var utf8 = false

    var supportsRest: Boolean = true
        private set

    suspend fun readReply(timeoutMs: Long = CONTROL_TIMEOUT_MS): FtpReply = withTimeout(timeoutMs) {
        val first = control.input.readUTF8Line(MAX_LINE) ?: throw FtpException("The FTP server closed the connection")
        if (first.length < 3 || !first.take(3).all { it.isDigit() }) {
            throw FtpException("The server did not answer like an FTP server: ${first.take(60)}")
        }
        val code = first.take(3).toInt()
        val lines = arrayListOf(first.drop(4))
        if (first.length > 3 && first[3] == '-') {
            while (true) {
                val line = control.input.readUTF8Line(MAX_LINE) ?: throw FtpException("The FTP server closed the connection")
                if (line.length >= 4 && line.take(3) == first.take(3) && line[3] == ' ') {
                    lines.add(line.drop(4))
                    break
                }
                lines.add(line)
            }
        }
        FtpReply(code, lines.joinToString("\n"))
    }

    suspend fun command(line: String, timeoutMs: Long = CONTROL_TIMEOUT_MS): FtpReply {
        if (line.contains('\r') || line.contains('\n')) throw FtpException("The FTP command has a line break in it")
        control.output.writeStringUtf8(line + "\r\n")
        control.output.flush()
        return readReply(timeoutMs)
    }

    private suspend fun connectControl() {
        val socket = withTimeout(CONNECT_TIMEOUT_MS) {
            aSocket(selector).tcp().connect(InetSocketAddress(target.host, target.port))
        }
        var connection = socket.connection()
        if (target.implicitTls) connection = tlsOver(connection)
        control = connection
        val greeting = readReply()
        if (greeting.code != 220) throw FtpException("The FTP server is not ready: ${greeting.code} ${greeting.firstLine}", greeting.code)
        if (target.tls && !target.implicitTls) {
            var answer = command("AUTH TLS")
            if (answer.code != 234) answer = command("AUTH SSL")
            if (answer.code != 234) throw FtpException("The FTP server does not offer TLS: ${answer.code} ${answer.firstLine}", answer.code)
            control = tlsOver(control)
        }
        if (target.tls) {
            command("PBSZ 0")
            val prot = command("PROT P")
            if (prot.code != 200) throw FtpException("The FTP server would not encrypt the data connection: ${prot.code} ${prot.firstLine}", prot.code)
        }
    }

    private suspend fun tlsOver(connection: Connection): Connection = try {
        withTimeout(CONNECT_TIMEOUT_MS) { connection.tls(Dispatchers.IO) {
            serverName = target.host
            configureFtpTls(this)
        }.connection() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw FtpException("The secure (TLS) connection to the FTP server failed: ${e.message ?: e::class.simpleName}")
    }

    private suspend fun login(user: String, password: String) {
        var answer = command("USER $user")
        if (answer.code == 331 || answer.code == 332) answer = command("PASS $password")
        if (answer.code !in 200..299) {
            throw FtpException("The FTP server refused the login: ${answer.code} ${answer.firstLine}", answer.code)
        }
        val type = command("TYPE I")
        if (type.code != 200) throw FtpException("The FTP server cannot send binary files: ${type.code} ${type.firstLine}", type.code)
        val features = command("FEAT")
        if (features.code == 211) {
            val text = features.text.uppercase()
            utf8 = text.contains("UTF8")
            supportsRest = text.contains("REST")
            if (utf8) command("OPTS UTF8 ON")
        }
    }

    /** SIZE of a file, or null when the server does not know it. */
    suspend fun size(path: String): Long? {
        val answer = command("SIZE $path")
        return if (answer.code == 213) answer.firstLine.trim().toLongOrNull() else null
    }

    /** The file's modified time as the server writes it (YYYYMMDDHHMMSS), or empty. */
    suspend fun modified(path: String): String {
        val answer = command("MDTM $path")
        return if (answer.code == 213) answer.firstLine.trim() else ""
    }

    /** Does a folder of this name exist? (CWD answers 250.) The working folder is left as it was. */
    suspend fun isFolder(path: String): Boolean {
        val answer = command("CWD $path")
        if (answer.code == 250) {
            command("CDUP")
            return true
        }
        return false
    }

    /**
     * Starts sending [path] from byte [offset] and returns the data connection to read the bytes
     * from. After reading to the end, call [finishTransfer] to read the server's 226.
     */
    suspend fun retrieve(path: String, offset: Long): Connection {
        val data = openPassive()
        try {
            if (offset > 0) {
                val rest = command("REST $offset")
                if (rest.code != 350) {
                    supportsRest = false
                    throw FtpException("The FTP server cannot continue a download (REST ${rest.code})", rest.code)
                }
            }
            val answer = command("RETR $path", RETR_TIMEOUT_MS)
            if (answer.code != 125 && answer.code != 150) {
                throw FtpException("The FTP server would not send the file: ${answer.code} ${answer.firstLine}", answer.code)
            }
            return if (target.tls) tlsOver(data) else data
        } catch (e: Throwable) {
            runCatching { data.socket.close() }
            throw e
        }
    }

    /** Reads the closing reply of a transfer that was read to its end. */
    suspend fun finishTransfer(): FtpReply = readReply()

    private suspend fun openPassive(): Connection {
        var host = target.host
        var port: Int
        val extended = command("EPSV")
        if (extended.code == 229) {
            val digits = extended.text.substringAfter("(|||", "").substringBefore("|)", "")
            port = digits.toIntOrNull() ?: throw FtpException("The FTP server gave an unreadable passive port: ${extended.firstLine}")
        } else {
            val answer = command("PASV")
            if (answer.code != 227) throw FtpException("The FTP server has no passive mode: ${answer.code} ${answer.firstLine}", answer.code)
            val numbers = Regex("""(\d+),(\d+),(\d+),(\d+),(\d+),(\d+)""").find(answer.text)
                ?.groupValues?.drop(1)?.map { it.toInt() }
                ?: throw FtpException("The FTP server gave an unreadable passive address: ${answer.firstLine}")
            port = numbers[4] * 256 + numbers[5]
            val given = "${numbers[0]}.${numbers[1]}.${numbers[2]}.${numbers[3]}"
            if (!isPrivate(given) || isPrivate(target.host)) host = given
        }
        val socket = withTimeout(CONNECT_TIMEOUT_MS) {
            aSocket(selector).tcp().connect(InetSocketAddress(host, port))
        }
        return socket.connection()
    }

    private fun isPrivate(address: String): Boolean {
        val p = address.split('.').mapNotNull { it.toIntOrNull() }
        if (p.size != 4) return address == "localhost" || address == "::1"
        return p[0] == 10 || p[0] == 127 || p[0] == 0 || (p[0] == 172 && p[1] in 16..31) || (p[0] == 192 && p[1] == 168) || (p[0] == 169 && p[1] == 254)
    }

    suspend fun close() = withContext(NonCancellable) {
        try {
            if (this@FtpSession::control.isInitialized) {
                runCatching { command("QUIT", 2_000) }
                runCatching { control.socket.close() }
            }
        } finally {
            runCatching { selector.close() }
        }
    }

    companion object {
        const val CONNECT_TIMEOUT_MS = 20_000L
        const val CONTROL_TIMEOUT_MS = 45_000L
        const val RETR_TIMEOUT_MS = 90_000L
        private const val MAX_LINE = 4096

        suspend fun open(target: FtpTarget, user: String, password: String): FtpSession {
            val session = FtpSession(target)
            try {
                session.connectControl()
                session.login(user, password)
            } catch (e: Throwable) {
                runCatching { session.close() }
                throw e
            }
            return session
        }
    }
}
