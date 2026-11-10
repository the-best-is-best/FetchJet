import com.michelle.raouf.fetch_jet.platform.desktop.DesktopFileSystem
import com.michelle.raouf.fetch_jet.platform.win.winDeleteRecursively
import com.michelle.raouf.fetch_jet.platform.win.winExists
import com.michelle.raouf.fetch_jet.platform.win.winFileInfo
import com.michelle.raouf.fetch_jet.platform.win.winIsDirectory
import com.michelle.raouf.fetch_jet.platform.win.winListEntries
import com.michelle.raouf.fetch_jet.platform.win.winReadFile
import com.michelle.raouf.fetch_jet.platform.win.winReadText
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import okio.buffer
import okio.use

fun pathTests(work: String, rest: List<String>) {
    val which = rest.firstOrNull { it.startsWith("fs=") }?.substringAfter("=") ?: "desktop"
    val fs: FileSystem = if (which == "okio") FileSystem.SYSTEM else DesktopFileSystem
    val root = rest.firstOrNull { it.startsWith("base=") }?.substringAfter("=")?.replace('/', '\\') ?: "E:\\tools\\fjhome"
    val base = "$root\\عربي تجربة\\$which"
    winDeleteRecursively(base)
    T.info("file system: $fs, base $base")
    pathCase(fs, "$which short", "$base\\مجلد التنزيلات")
    var long = "$base\\مجلد طويل"
    while (long.length < 300) long += "\\مجلد فرعي باسم طويل جدا"
    T.info("long folder: ${long.length} characters")
    pathCase(fs, "$which long", long)
    winDeleteRecursively("$root\\عربي تجربة\\$which")
}

private fun pathCase(fs: FileSystem, label: String, dirText: String) {
    val dir = dirText.toPath()
    val name = "ملف عربي.bin"
    T.guarded("$label: createDirectories") {
        fs.createDirectories(dir)
        T.check("$label: createDirectories makes the exact Unicode folder", winIsDirectory(dirText))
    }
    val cacheText = "{\"tasks\":[{\"fileName\":\"$name\",\"note\":\"تجربة\"}]}"
    T.guarded("$label: write text") {
        fs.write(dir / "tasks_cache.json") { writeUtf8(cacheText) }
        T.check("$label: write (tasks cache) lands under the exact name", winReadText("$dirText\\tasks_cache.json") == cacheText)
    }
    T.guarded("$label: exists / metadata / read") {
        val file = dir / "tasks_cache.json"
        T.check("$label: exists", fs.exists(file))
        T.check("$label: metadata size", fs.metadataOrNull(file)?.size == cacheText.encodeToByteArray().size.toLong())
        T.check("$label: read text back", fs.read(file) { readUtf8() } == cacheText)
        T.check("$label: missing file is not there", !fs.exists(dir / "لا يوجد.txt") && fs.metadataOrNull(dir / "لا يوجد.txt") == null)
    }
    val block = ByteArray(100_000) { (it % 251).toByte() }
    T.guarded("$label: part files (appendingSink)") {
        for (index in 0..1) {
            val part = dir / "$name.part$index"
            fs.appendingSink(part).buffer().use { it.write(block) }
            fs.appendingSink(part).buffer().use { it.write(block) }
        }
        T.check("$label: appendingSink appends", winFileInfo("$dirText\\$name.part0")?.size == 200_000L && winFileInfo("$dirText\\$name.part1")?.size == 200_000L)
    }
    T.guarded("$label: manifest") {
        fs.write(dir / "$name.chunks") { writeUtf8("v1\n0 0 199999\n") }
        T.check("$label: manifest read back", fs.read(dir / "$name.chunks") { readUtf8() } == "v1\n0 0 199999\n")
    }
    T.guarded("$label: merge parts (sink + source)") {
        val out = dir / name
        fs.sink(out).buffer().use { sink ->
            for (index in 0..1) fs.source(dir / "$name.part$index").buffer().use { sink.writeAll(it) }
        }
        val bytes = winReadFile("$dirText\\$name")
        T.check("$label: merged file has every byte", bytes != null && bytes.size == 400_000 && bytes.copyOfRange(200_000, 300_000).contentEquals(block))
    }
    T.guarded("$label: list") {
        val names = fs.list(dir).map { it.name }
        T.check("$label: list shows the Arabic names", name in names && "$name.part0" in names && "tasks_cache.json" in names, names.joinToString())
        val native = winListEntries(dirText)?.map { it.name }.orEmpty()
        T.check("$label: names on disk are the exact Unicode names", name in native, native.joinToString())
    }
    T.guarded("$label: atomicMove replaces") {
        fs.write(dir / "مؤقت.tmp") { writeUtf8("new") }
        fs.write(dir / "هدف.txt") { writeUtf8("old") }
        fs.atomicMove(dir / "مؤقت.tmp", dir / "هدف.txt")
        T.check("$label: atomicMove over an existing file", winReadText("$dirText\\هدف.txt") == "new" && !winExists("$dirText\\مؤقت.tmp"))
    }
    T.guarded("$label: copy") {
        fs.copy(dir / name, dir / "نسخة.bin")
        T.check("$label: copy", winFileInfo("$dirText\\نسخة.bin")?.size == 400_000L)
    }
    T.guarded("$label: openReadWrite") {
        fs.openReadWrite(dir / "عشوائي.bin").use { handle ->
            handle.write(10L, block, 0, 100)
            handle.resize(50L)
            T.check("$label: random write + resize", handle.size() == 50L)
        }
        T.check("$label: random file size on disk", winFileInfo("$dirText\\عشوائي.bin")?.size == 50L)
    }
    T.guarded("$label: delete") {
        fs.delete(dir / "نسخة.bin")
        fs.delete(dir / "لا يوجد.txt", mustExist = false)
        T.check("$label: delete a file", !winExists("$dirText\\نسخة.bin"))
        fs.deleteRecursively(dir)
        T.check("$label: deleteRecursively", !winExists(dirText))
    }
}
