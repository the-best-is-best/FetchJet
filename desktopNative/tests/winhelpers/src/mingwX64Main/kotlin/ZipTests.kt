import com.michelle.raouf.fetch_jet.platform.win.WinZipException
import com.michelle.raouf.fetch_jet.platform.win.toHexString
import com.michelle.raouf.fetch_jet.platform.win.winCreateDirectories
import com.michelle.raouf.fetch_jet.platform.win.winDeleteRecursively
import com.michelle.raouf.fetch_jet.platform.win.winExists
import com.michelle.raouf.fetch_jet.platform.win.winFileInfo
import com.michelle.raouf.fetch_jet.platform.win.winIsDirectory
import com.michelle.raouf.fetch_jet.platform.win.winRunProcess
import com.michelle.raouf.fetch_jet.platform.win.winSha256OfFile
import com.michelle.raouf.fetch_jet.platform.win.winUnzip
import com.michelle.raouf.fetch_jet.platform.win.winZipEntries
import com.michelle.raouf.fetch_jet.platform.win.winZipReadEntry
import kotlin.time.TimeSource
import kotlinx.coroutines.runBlocking

fun zipTests(work: String) {
    val dir = "$work\\zip"
    val manifest = loadMap("$dir\\manifest.txt")
    T.check("zip manifest made by make_zip.py is present", manifest.isNotEmpty())

    T.guarded("unzip") {
        val entries = winZipEntries("$dir\\test.zip")
        T.check("directory lists every entry", entries.count { !it.isDirectory } == manifest.size, "${entries.size} entries")
        val out = "$dir\\out-native"
        winDeleteRecursively(out)
        val mark = TimeSource.Monotonic.markNow()
        val files = winUnzip("$dir\\test.zip", out)
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
        val small = winZipReadEntry("$dir\\test.zip", "docs/عربي/ملف نصي.txt")
        T.check("one entry read into memory", small?.decodeToString() == "محتوى عربي\n" + "line\n".repeat(100))
        T.check("a missing entry is null", winZipReadEntry("$dir\\test.zip", "nope.txt") == null)

        val tarOut = "$dir\\out-tar"
        winDeleteRecursively(tarOut)
        winCreateDirectories(tarOut)
        val tarMark = TimeSource.Monotonic.markNow()
        val tar = runBlocking { winRunProcess(listOf("C:\\Windows\\System32\\tar.exe", "-xf", "$dir\\test.zip", "-C", tarOut)) }
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
            winUnzip("$dir\\slip.zip", "$dir\\out-slip")
        } catch (e: WinZipException) {
            refused = true
        }
        T.check("an entry that climbs out of the target folder is refused", refused && !winExists("$dir\\evil.txt"))
        refused = false
        try {
            winUnzip("$dir\\truncated.zip", "$dir\\out-truncated")
        } catch (e: WinZipException) {
            refused = true
        }
        T.check("a cut archive is refused", refused)
        refused = false
        try {
            winUnzip("$dir\\corrupt.zip", "$dir\\out-corrupt")
        } catch (e: WinZipException) {
            refused = true
        }
        T.check("a damaged entry is refused (checksum)", refused)
        refused = false
        try {
            winZipEntries("$dir\\manifest.txt")
        } catch (e: WinZipException) {
            refused = true
        }
        T.check("a file that is not a zip is refused", refused)
        winDeleteRecursively("$dir\\out-slip")
        winDeleteRecursively("$dir\\out-truncated")
        winDeleteRecursively("$dir\\out-corrupt")
    }
}
