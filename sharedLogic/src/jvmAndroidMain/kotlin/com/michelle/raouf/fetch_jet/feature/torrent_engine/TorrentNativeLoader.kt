package com.michelle.raouf.fetch_jet.feature.torrent_engine

import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * libtorrent4j does NOT extract its native library by itself on desktop: its JNI class just
 * calls `System.loadLibrary("libtorrent4j")`, which only works if the DLL/.so/.dylib is on
 * `java.library.path`. The platform jar only carries it as a resource (`lib/<arch>/...`).
 *
 * This loader makes the engine self-contained:
 *  1. extracts the bundled native from the app's own classpath into `<tools>/`
 *  2. points `libtorrent4j.jni.path` at it before any libtorrent4j class is touched
 *  3. if the resource is missing (unexpected packaging), "Download Tools" in Settings can
 *     fetch the exact same jar from Maven Central (SHA-1 verified) and extract it.
 *
 * On Android the .so files are packaged into the APK and `System.loadLibrary` works, so
 * nothing is done there.
 */
internal object TorrentNativeLoader {
    const val LIBTORRENT4J_VERSION = "2.1.0-39"

    @Volatile
    private var loadResult: Result<String>? = null

    val isAndroid: Boolean =
        System.getProperty("java.vendor")?.contains("Android", ignoreCase = true) == true ||
                System.getProperty("java.vm.vendor")?.contains("Android", ignoreCase = true) == true

    private val isWindows = System.getProperty("os.name")?.lowercase()?.contains("win") == true
    private val isMac = System.getProperty("os.name")?.lowercase()?.contains("mac") == true

    private val nativeFileName: String
        get() = when {
            isWindows -> "libtorrent4j.dll"
            isMac -> "libtorrent4j.dylib"
            else -> "libtorrent4j.so"
        }

    private val mavenArtifact: String
        get() = when {
            isWindows -> "libtorrent4j-windows"
            isMac -> "libtorrent4j-macos"
            else -> "libtorrent4j-linux"
        }

    private val archDirs: List<String>
        get() {
            val arch = System.getProperty("os.arch")?.lowercase() ?: ""
            val primary = when {
                arch.contains("aarch64") || arch.contains("arm64") -> listOf("arm64", "aarch64")
                else -> listOf("x86_64", "amd64", "x64")
            }
            return primary + listOf("x86_64", "arm64", "")
        }

    fun installedNative(toolsDir: File): File? {
        val f = File(toolsDir, "$LIBTORRENT4J_VERSION-$nativeFileName")
        return f.takeIf { it.isFile && it.length() > 100_000 }
    }

    /** Human-readable state for the Settings screen. */
    fun status(toolsDir: File): String {
        if (isAndroid) return "Ready: BitTorrent engine built in"
        loadResult?.let { r ->
            return if (r.isSuccess) "Ready: BitTorrent engine (libtorrent $LIBTORRENT4J_VERSION)"
            else "BitTorrent engine failed to load: ${r.exceptionOrNull()?.message}"
        }
        return if (installedNative(toolsDir) != null || findBundledResource() != null)
            "Ready: BitTorrent engine available (loads on first torrent)"
        else "BitTorrent engine missing. Click 'Download Tools' below."
    }

    fun isAvailable(toolsDir: File): Boolean =
        isAndroid || loadResult?.isSuccess == true || installedNative(toolsDir) != null || findBundledResource() != null

    /**
     * Must be called before the first libtorrent4j class is used. Idempotent; returns the
     * failure instead of throwing so callers can show it.
     */
    @Synchronized
    fun ensureLoaded(toolsDir: File): Result<String> {
        if (isAndroid) return Result.success("android")
        loadResult?.let { return it }
        val result = runCatching {
            val native = installedNative(toolsDir)
                ?: extractBundled(toolsDir)
                ?: throw IllegalStateException(
                    "The libtorrent native library ($nativeFileName) is not bundled in this build. " +
                            "Open Settings and click 'Download Tools'."
                )
            System.setProperty("libtorrent4j.jni.path", native.absolutePath)
            // Touch the JNI class now so a load failure surfaces here with a clear cause.
            val version = org.libtorrent4j.LibTorrent.version()
            "libtorrent $version at ${native.absolutePath}"
        }.recoverCatching { e ->
            val root = generateSequence(e) { it.cause }.last()
            val hint = when {
                root.message?.contains("dependent libraries", true) == true ->
                    " (Install the Microsoft Visual C++ 2015-2022 Redistributable x64.)"

                root.message?.contains("denied", true) == true ->
                    " (Your antivirus blocked the library; allow the FetchJet tools folder.)"

                else -> ""
            }
            throw IllegalStateException("${root::class.simpleName}: ${root.message}$hint", e)
        }
        loadResult = result
        return result
    }

    private fun findBundledResource(): String? {
        val cl = TorrentNativeLoader::class.java.classLoader ?: return null
        for (dir in archDirs) {
            val path = if (dir.isEmpty()) "lib/$nativeFileName" else "lib/$dir/$nativeFileName"
            if (cl.getResource(path) != null) return path
        }
        return null
    }

    private fun extractBundled(toolsDir: File): File? {
        val path = findBundledResource() ?: return null
        val cl = TorrentNativeLoader::class.java.classLoader
        val target = File(toolsDir, "$LIBTORRENT4J_VERSION-$nativeFileName")
        toolsDir.mkdirs()
        val tmp = File(toolsDir, target.name + ".tmp")
        cl.getResourceAsStream(path)!!.use { input -> tmp.outputStream().use { input.copyTo(it) } }
        Files.move(
            tmp.toPath(),
            target.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE
        )
        target.setExecutable(true)
        return target
    }

    /**
     * "Download Tools": fetch the platform jar from Maven Central, verify its SHA-1 against
     * the checksum Maven publishes next to it, and extract the native from it.
     */
    fun downloadFromMavenCentral(toolsDir: File): Result<File> = runCatching {
        if (isAndroid) throw IllegalStateException("Not needed on Android")
        installedNative(toolsDir)?.let { return@runCatching it }
        extractBundled(toolsDir)?.let { return@runCatching it }

        val base =
            "https://repo1.maven.org/maven2/org/libtorrent4j/$mavenArtifact/$LIBTORRENT4J_VERSION/" +
                    "$mavenArtifact-$LIBTORRENT4J_VERSION.jar"
        toolsDir.mkdirs()
        val jar = File(toolsDir, "$mavenArtifact-$LIBTORRENT4J_VERSION.jar.tmp")
        val expectedSha1 = httpGetText("$base.sha1").trim().substringBefore(' ').lowercase()
        val actualSha1 = httpDownload(base, jar, maxBytes = 60L * 1024 * 1024)
        if (expectedSha1.length == 40 && expectedSha1 != actualSha1) {
            jar.delete()
            throw IllegalStateException("Checksum mismatch for $mavenArtifact (expected $expectedSha1, got $actualSha1)")
        }
        val target = File(toolsDir, "$LIBTORRENT4J_VERSION-$nativeFileName")
        var found = false
        ZipInputStream(jar.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name.substringAfterLast('/')
                if (!entry.isDirectory && name == nativeFileName && entry.name.startsWith("lib/")) {
                    val tmp = File(toolsDir, target.name + ".tmp")
                    tmp.outputStream().use { zis.copyTo(it) }
                    Files.move(
                        tmp.toPath(),
                        target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                    )
                    found = true
                    break
                }
                entry = zis.nextEntry
            }
        }
        jar.delete()
        if (!found) throw IllegalStateException("$nativeFileName not found inside $mavenArtifact jar")
        target.setExecutable(true)
        loadResult = null   // allow ensureLoaded() to try again with the new file
        target
    }

    private fun open(url: String): HttpURLConnection {
        val c = URI(url).toURL().openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 60_000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "FetchJet")
        if (c.responseCode !in 200..299) throw IllegalStateException("HTTP ${c.responseCode} for $url")
        return c
    }

    private fun httpGetText(url: String): String =
        open(url).inputStream.bufferedReader().use { it.readText() }

    private fun httpDownload(url: String, dest: File, maxBytes: Long): String {
        val digest = MessageDigest.getInstance("SHA-1")
        var total = 0L
        open(url).inputStream.use { input ->
            dest.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > maxBytes) throw IllegalStateException("Download too large")
                    digest.update(buf, 0, n)
                    out.write(buf, 0, n)
                }
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
