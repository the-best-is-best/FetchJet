import com.michelle.raouf.fetch_jet.platform.desktop.DesktopOs
import com.michelle.raouf.fetch_jet.platform.desktop.DesktopZip
import com.michelle.raouf.fetch_jet.platform.desktop.copyFile
import com.michelle.raouf.fetch_jet.platform.desktop.writeBytes
import com.michelle.raouf.fetch_jet.platform.desktop.DesktopZipException
import com.michelle.raouf.fetch_jet.platform.desktop.runProcess
import com.michelle.raouf.fetch_jet.platform.win.toHexString
import com.michelle.raouf.fetch_jet.platform.win.winCreateDirectories
import com.michelle.raouf.fetch_jet.platform.win.winDeleteRecursively
import com.michelle.raouf.fetch_jet.platform.win.winExists
import com.michelle.raouf.fetch_jet.platform.win.winFileInfo
import com.michelle.raouf.fetch_jet.platform.win.winIsDirectory
import com.michelle.raouf.fetch_jet.platform.win.winSha256OfFile
import kotlin.time.TimeSource
import kotlinx.coroutines.runBlocking

fun zipTests(work: String) {
    val dir = "$work\\zip"
    val manifest = loadMap("$dir\\manifest.txt")
    T.check("zip manifest made by make_zip.py is present", manifest.isNotEmpty())

    T.guarded("unzip") {
        val entries = DesktopZip.entries("$dir\\test.zip")
        T.check("directory lists every entry", entries.count { !it.isDirectory } == manifest.size, "${entries.size} entries")
        val out = "$dir\\out-native"
        winDeleteRecursively(out)
        val mark = TimeSource.Monotonic.markNow()
        val files = DesktopZip.unzip("$dir\\test.zip", out)
        val took = mark.elapsedNow().inWholeMilliseconds
        var good = 0
        val bad = ArrayList<String>()
        for ((name, hash) in manifest) {
            val path = out + "\\" + name.replace('/', '\\')
            if (winSha256OfFile(path)?.toHexString() == hash) good++ else bad.add(name)
        }
        T.check("every file is extracted with the right content", files == manifest.size && good == manifest.size, "$good of ${manifest.size} in $took ms $bad")
        T.check("an empty folder entry is created", winIsDirectory("$out\\empty-dir"))
        T.check("an empty file is created", winFileInfo("$out\\empty.txt")?.size == 0L)
        val smallFile = "$dir\\small-entry.txt"
        val smallOk = DesktopZip.unzipEntryTo("$dir\\test.zip", smallFile) { it.name == "docs/عربي/ملف نصي.txt" }
        val small = if (smallOk) DesktopOs.readText(smallFile)?.encodeToByteArray() else null
        T.check("one entry read into memory", small?.decodeToString() == "محتوى عربي\n" + "line\n".repeat(100))
        T.check("a missing entry is not extracted", !DesktopZip.unzipEntryTo("$dir\\test.zip", "$dir\\nope.txt") { it.name == "nope.txt" })
        val copied = DesktopOs.copyFile("$dir\\test.zip", "$dir\\out-native\\copy عربي.zip")
        T.check("copyFile keeps every byte (Arabic target name)", copied && winSha256OfFile("$dir\\out-native\\copy عربي.zip")?.toHexString() == winSha256OfFile("$dir\\test.zip")?.toHexString())
        val written = DesktopOs.writeBytes("$dir\\out-native\\written.bin", ByteArray(70_000) { (it % 251).toByte() })
        T.check("writeBytes writes the whole array", written && winFileInfo("$dir\\out-native\\written.bin")?.size == 70_000L)

        val tarOut = "$dir\\out-tar"
        winDeleteRecursively(tarOut)
        winCreateDirectories(tarOut)
        val tarMark = TimeSource.Monotonic.markNow()
        val tar = runBlocking { DesktopOs.runProcess(listOf("C:\\Windows\\System32\\tar.exe", "-xf", "$dir\\test.zip", "-C", tarOut)) }
        val tarTook = tarMark.elapsedNow().inWholeMilliseconds
        var tarGood = 0
        val tarBad = ArrayList<String>()
        for ((name, hash) in manifest) {
            val path = tarOut + "\\" + name.replace('/', '\\')
            if (winSha256OfFile(path)?.toHexString() == hash) tarGood++ else tarBad.add(name)
        }
        T.info("tar.exe -xf: exit=${tar.exitCode} $tarGood of ${manifest.size} files right in $tarTook ms, wrong or missing: $tarBad ${tar.stderr.take(200)}")
        winDeleteRecursively(out)
        winDeleteRecursively(tarOut)
    }

    T.guarded("bad archives") {
        var refused = false
        try {
            DesktopZip.unzip("$dir\\slip.zip", "$dir\\out-slip")
        } catch (e: DesktopZipException) {
            refused = true
        }
        T.check("an entry that climbs out of the target folder is refused", refused && !winExists("$dir\\evil.txt"))
        refused = false
        try {
            DesktopZip.unzip("$dir\\truncated.zip", "$dir\\out-truncated")
        } catch (e: DesktopZipException) {
            refused = true
        }
        T.check("a cut archive is refused", refused)
        refused = false
        try {
            DesktopZip.unzip("$dir\\corrupt.zip", "$dir\\out-corrupt")
        } catch (e: DesktopZipException) {
            refused = true
        }
        T.check("a damaged entry is refused (checksum)", refused)
        refused = false
        try {
            DesktopZip.entries("$dir\\manifest.txt")
        } catch (e: DesktopZipException) {
            refused = true
        }
        T.check("a file that is not a zip is refused", refused)
        winDeleteRecursively("$dir\\out-slip")
        winDeleteRecursively("$dir\\out-truncated")
        winDeleteRecursively("$dir\\out-corrupt")
    }
}
