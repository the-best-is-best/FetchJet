@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

import com.michelle.raouf.fetch_jet.platform.win.WinException
import com.michelle.raouf.fetch_jet.platform.win.WinLineSplitter
import com.michelle.raouf.fetch_jet.platform.win.WinProcess
import com.michelle.raouf.fetch_jet.platform.win.toHexString
import com.michelle.raouf.fetch_jet.platform.win.winCommandLine
import com.michelle.raouf.fetch_jet.platform.win.winCreateDirectories
import com.michelle.raouf.fetch_jet.platform.win.winFullPath
import com.michelle.raouf.fetch_jet.platform.win.winRunProcess
import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import platform.windows.CloseHandle
import platform.windows.OpenProcess
import platform.windows.WaitForSingleObject

private fun alive(pid: UInt): Boolean {
    val handle = OpenProcess(0x00100000u or 0x1000u, 0, pid) ?: return false
    val running = WaitForSingleObject(handle, 0u) == 258u
    CloseHandle(handle)
    return running
}

private fun py(code: String, vararg args: String): List<String> = listOf("python", "-X", "utf8", "-c", code) + args

fun processTests(work: String, options: List<String>) = runBlocking {
    T.guarded("basic") {
        val echo = winRunProcess(listOf("cmd.exe", "/c", "echo hello"))
        T.check("cmd /c echo: output and exit code 0", echo.exitCode == 0 && echo.stdout == "hello\n", echo.stdout.trim())
        T.check("exit code is passed on", winRunProcess(listOf("cmd.exe", "/c", "exit 7")).exitCode == 7)
        var missing = false
        try {
            winRunProcess(listOf("no-such-program-fj.exe"))
        } catch (e: WinException) {
            missing = e.code == 2u
        }
        T.check("a program that does not exist throws WinException(2)", missing)
        val window = winRunProcess(py("import ctypes; print(ctypes.windll.kernel32.GetConsoleWindow())"))
        T.check("the child has no console window", window.stdout.trim() == "0", window.stdout.trim())
    }

    T.guarded("arguments") {
        val arguments = listOf(
            "plain", "with space", "quote\"inside", "back\\slash", "trailing\\", "space trailing\\", "a\\\"b", "",
            "عربي نص", "emoji 😀", "per%cent%", "amp&er|sand<>^", "tab\there", "'single'", "--opt=val ue", "\\\\server\\share\\x y"
        )
        val result = winRunProcess(py("import sys\nfor a in sys.argv[1:]: print(a.encode('utf-8').hex())", *arguments.toTypedArray()))
        val got = result.stdout.trim().lines().map { it.trim() }
        val expected = arguments.map { it.encodeToByteArray().toHexString() }
        T.check("16 awkward arguments arrive unchanged", got == expected, "exit=${result.exitCode} got=${got.size}")
        T.info("command line sample: ${winCommandLine(listOf("a b", "c\"d", "e\\"))}")
    }

    T.guarded("streams") {
        val mark = TimeSource.Monotonic.markNow()
        var outLines = 0
        var errLines = 0
        var lastOut = ""
        val big = winRunProcess(
            py(
                "import sys\nfor i in range(200000):\n    print('out line %d padding padding padding padding' % i)\n" +
                    "    if i % 4 == 0: print('err line %d' % i, file=sys.stderr)"
            ),
            keepOutput = false,
            onStdoutLine = { outLines++; lastOut = it },
            onStderrLine = { errLines++ }
        )
        T.check(
            "200000 stdout lines and 50000 stderr lines at the same time, no deadlock",
            big.exitCode == 0 && outLines == 200000 && errLines == 50000 && lastOut.startsWith("out line 199999"),
            "out=$outLines err=$errLines in ${mark.elapsedNow().inWholeMilliseconds} ms"
        )
        val merged = winRunProcess(
            py("import sys\nprint('one'); sys.stdout.flush()\nprint('two', file=sys.stderr); sys.stderr.flush()\nprint('three')"),
            mergeStderr = true
        )
        T.check("stderr merged into stdout keeps the order", merged.stdout == "one\ntwo\nthree\n" && merged.stderr.isEmpty(), merged.stdout.replace("\n", "|"))
        val split = winRunProcess(py("import sys\nprint('one')\nprint('two', file=sys.stderr)"))
        T.check("stderr captured apart", split.stdout == "one\n" && split.stderr == "two\n")
        val text = "مرحبا بالعالم 😀 hello"
        val dribble = winRunProcess(
            py(
                "import sys,time\ndata=('$text\\n'*40).encode('utf-8')\nfor b in data:\n    sys.stdout.buffer.write(bytes([b])); sys.stdout.buffer.flush()"
            )
        )
        val lines = dribble.stdout.trim().lines()
        T.check("UTF-8 written one byte at a time decodes to whole lines", lines.size == 40 && lines.all { it == text }, "lines=${lines.size}")
        val seen = ArrayList<String>()
        val splitter = WinLineSplitter { seen.add(it) }
        val raw = "a\rb\r\nc\n\nd".encodeToByteArray()
        splitter.feed(raw, raw.size)
        splitter.finish()
        T.check("lines split at CR, CRLF and LF like Java's readLine", seen == listOf("a", "b", "c", "", "d"), seen.toString())
        val input = winRunProcess(py("import sys\ndata=sys.stdin.buffer.read()\nsys.stdout.buffer.write(data.upper())"), stdinBytes = "secret key line\n".encodeToByteArray())
        T.check("stdin is delivered and closed", input.stdout == "SECRET KEY LINE\n", input.stdout.trim())
        val closed = winRunProcess(py("import sys\nprint(len(sys.stdin.read()))"))
        T.check("without stdin data the child reads end of file at once", closed.stdout.trim() == "0")
    }

    T.guarded("environment") {
        val dir = "$work\\مجلد العمل"
        winCreateDirectories(dir)
        val result = winRunProcess(
            py("import os\nprint(os.environ.get('FJ_X')); print(os.environ.get('PATH') is not None); print(os.getcwd()); print(os.environ.get('FJ_GONE'))"),
            environment = mapOf("FJ_X" to "قيمة 1", "FJ_GONE" to null),
            workDir = dir
        )
        val lines = result.stdout.trim().lines()
        T.check(
            "extra environment variable, inherited PATH and Arabic working directory",
            lines.size == 4 && lines[0] == "قيمة 1" && lines[1] == "True" && lines[2] == winFullPath(dir) && lines[3] == "None",
            lines.toString()
        )
    }

    T.guarded("timeout") {
        val mark = TimeSource.Monotonic.markNow()
        var grandchild = 0u
        var child = 0u
        val result = winRunProcess(
            py(
                "import subprocess,sys,time\np=subprocess.Popen([sys.executable,'-c','import time; time.sleep(60)'])\n" +
                    "print(p.pid); sys.stdout.flush()\ntime.sleep(60)"
            ),
            timeoutMs = 2000,
            onStarted = { child = it.pid },
            onStdoutLine = { grandchild = it.trim().toUIntOrNull() ?: 0u }
        )
        val took = mark.elapsedNow().inWholeMilliseconds
        delay(300)
        T.check(
            "timeout kills the child and its grandchild",
            result.timedOut && took < 6000 && grandchild != 0u && !alive(grandchild) && !alive(child),
            "took=$took ms exit=${result.exitCode} child=$child alive=${alive(child)} grandchild=$grandchild alive=${alive(grandchild)}"
        )
    }

    T.guarded("cancel") {
        var pid = 0u
        val mark = TimeSource.Monotonic.markNow()
        var cancelled = false
        val job = launch(Dispatchers.Default) {
            try {
                winRunProcess(py("import time; time.sleep(60)"), onStarted = { pid = it.pid })
            } catch (e: CancellationException) {
                cancelled = true
                throw e
            }
        }
        delay(1200)
        val joined = withTimeoutOrNull(5000) {
            job.cancelAndJoin()
            true
        } ?: false
        delay(200)
        T.check(
            "cancelling the coroutine kills the process at once",
            joined && cancelled && pid != 0u && !alive(pid), "joined=$joined after ${mark.elapsedNow().inWholeMilliseconds} ms pid=$pid"
        )
    }

    T.guarded("parallel") {
        val mark = TimeSource.Monotonic.markNow()
        val results = (0 until 8).map { index ->
            async(Dispatchers.Default) {
                var count = 0
                val r = winRunProcess(
                    py("import sys\nfor i in range(20000): print('p%s line %d' % (sys.argv[1], i))", index.toString()),
                    keepOutput = false, onStdoutLine = { if (it.startsWith("p$index line ")) count++ }
                )
                r.exitCode == 0 && count == 20000
            }
        }.awaitAll()
        T.check("8 processes at once, each 20000 lines, none mixed", results.all { it }, "${mark.elapsedNow().inWholeMilliseconds} ms")
    }

    T.guarded("detached") {
        val marker = "$work\\detached.txt"
        com.michelle.raouf.fetch_jet.platform.win.winDeleteFile(marker)
        val process = WinProcess.start(
            py("import time,sys\ntime.sleep(1.5)\nopen(sys.argv[1],'w').write('done')", marker),
            captureOutput = false, detached = true
        )
        val pid = process.pid
        process.close()
        delay(300)
        val survives = alive(pid)
        delay(2500)
        T.check("a detached process outlives its handle and finishes its work", survives && com.michelle.raouf.fetch_jet.platform.win.winReadText(marker) == "done")
    }
}
