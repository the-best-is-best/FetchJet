package com.michelle.raouf.fetch_jet.core.log

import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.PrintStream

actual object DiagnosticsLog {
    actual val canExport: Boolean get() = true

    private var installed = false
    private fun logFile(): File? {
        val ctx = PathResolver.appContext ?: return null
        return File(ctx.filesDir, "logs/app.log").also { it.parentFile?.mkdirs() }
    }

    /** println / e.printStackTrace go to logcat AND the file (2 MB, then it starts over). */
    actual fun install() {
        if (installed) return
        val f = logFile() ?: return
        installed = true
        try {
            if (f.length() > 2L * 1024 * 1024) f.delete()
            val origOut = System.out
            val origErr = System.err
            val file = FileOutputStream(f, true)

            class Tee(private val a: OutputStream, private val b: OutputStream) : OutputStream() {
                override fun write(b1: Int) {
                    a.write(b1); b.write(b1)
                }

                override fun write(buf: ByteArray, off: Int, len: Int) {
                    a.write(buf, off, len); b.write(buf, off, len)
                }

                override fun flush() {
                    a.flush(); b.flush()
                }
            }
            System.setOut(PrintStream(Tee(origOut, file), true, "UTF-8"))
            System.setErr(PrintStream(Tee(origErr, file), true, "UTF-8"))
            println("---- FetchJet log started ${java.util.Date()} ----")
        } catch (_: Throwable) {
        }
    }

    /** Downloads/FetchJet-log.txt (MediaStore on Android 10+, plain file before). */
    actual fun export(): String? {
        val ctx = PathResolver.appContext ?: return null
        val src = logFile()?.takeIf { it.exists() } ?: return null
        val name = "FetchJet-log-${System.currentTimeMillis() / 1000}.txt"
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri =
                    ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: return null
                ctx.contentResolver.openOutputStream(uri)
                    ?.use { out -> src.inputStream().use { it.copyTo(out) } }
                "Downloads/$name"
            } else {
                val dir =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                dir.mkdirs()
                val dst = File(dir, name)
                src.copyTo(dst, overwrite = true)
                dst.absolutePath
            }
        } catch (e: Throwable) {
            println("DiagnosticsLog: export failed: $e"); null
        }
    }
}
