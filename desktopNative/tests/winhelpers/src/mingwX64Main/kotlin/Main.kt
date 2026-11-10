import com.michelle.raouf.fetch_jet.platform.win.winReadText
import kotlin.system.exitProcess

object T {
    var failures = 0
    var passes = 0

    fun check(name: String, ok: Boolean, detail: String = "") {
        if (ok) passes++ else failures++
        println((if (ok) "PASS  " else "FAIL  ") + name + (if (detail.isEmpty()) "" else "  [$detail]"))
    }

    fun info(text: String) = println("INFO  $text")

    inline fun guarded(name: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            check(name, false, e.stackTraceToString().take(1500))
        }
    }
}

fun loadMap(path: String): Map<String, String> {
    val text = winReadText(path) ?: return emptyMap()
    val map = LinkedHashMap<String, String>()
    for (line in text.lines()) {
        val cut = line.indexOf('=')
        if (cut > 0) map[line.substring(0, cut)] = line.substring(cut + 1).trim()
    }
    return map
}

fun main(args: Array<String>) {
    if (args.size < 2) {
        println("winhelpers <work-dir> crypto|secrets|system|files|process|zip|net|proxy [options]")
        exitProcess(2)
    }
    val work = args[0].replace('/', '\\')
    val rest = args.drop(2)
    when (args[1]) {
        "crypto" -> cryptoTests(work)
        "secrets" -> secretsTests(work)
        "system" -> systemTests(work, rest)
        "files" -> filesTests(work)
        "process" -> processTests(work, rest)
        "zip" -> zipTests(work)
        "paths" -> pathTests(work, rest)
        "net" -> netTests(work, rest)
        "rawhttp" -> rawHttpTests(work, rest)
        "torrent" -> torrentTests(work, rest)
        "proxy" -> proxyTests(work, rest)
        "sync" -> deviceSyncTests(work, rest)
        "syncjvm" -> deviceSyncJvmTests(work, rest)
        "syncpeer" -> deviceSyncPeer(work, rest)
        else -> {
            println("unknown mode ${args[1]}")
            exitProcess(2)
        }
    }
    println("DONE  ${args[1]}: ${T.passes} passed, ${T.failures} failed")
    exitProcess(if (T.failures == 0) 0 else 1)
}
