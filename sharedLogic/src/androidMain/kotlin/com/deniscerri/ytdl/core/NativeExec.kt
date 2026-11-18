package com.deniscerri.ytdl.core

import android.os.ParcelFileDescriptor
import java.io.File
import java.io.InputStream
import java.io.OutputStream

object NativeExec {
    init {
        System.loadLibrary("fjexec")
    }

    @JvmStatic
    external fun spawn(
        target: String,
        symbol: String,
        preload: String?,
        argv: Array<String>,
        envp: Array<String>?,
        cwd: String?,
        fd0: Int,
        fd1: Int,
        fd2: Int
    ): Int

    @JvmStatic
    external fun waitFor(pid: Int): Int

    @JvmStatic
    external fun pollExit(pid: Int): Int

    @JvmStatic
    external fun killGroup(pid: Int)

    class Target(val lib: String, val symbol: String, val preload: String?)

    fun start(
        target: Target,
        argv: List<String>,
        env: Map<String, String?>,
        cwd: File?,
        mergeErr: Boolean
    ): NativeProcess {
        val inPipe = ParcelFileDescriptor.createPipe()
        val outPipe = ParcelFileDescriptor.createPipe()
        val errPipe = if (mergeErr) null else ParcelFileDescriptor.createPipe()
        val envp = env.filterValues { it != null }.map { "${it.key}=${it.value}" }.toTypedArray()
        val pid = spawn(
            target.lib,
            target.symbol,
            target.preload,
            argv.toTypedArray(),
            envp,
            cwd?.absolutePath,
            inPipe[0].fd,
            outPipe[1].fd,
            if (mergeErr) outPipe[1].fd else errPipe!![1].fd
        )
        inPipe[0].close()
        outPipe[1].close()
        errPipe?.get(1)?.close()
        if (pid <= 0) {
            inPipe[1].close()
            outPipe[0].close()
            errPipe?.get(0)?.close()
            throw java.io.IOException("spawn failed: $pid")
        }
        return NativeProcess(
            pid,
            ParcelFileDescriptor.AutoCloseOutputStream(inPipe[1]),
            ParcelFileDescriptor.AutoCloseInputStream(outPipe[0]),
            errPipe?.let { ParcelFileDescriptor.AutoCloseInputStream(it[0]) }
                ?: java.io.ByteArrayInputStream(ByteArray(0))
        )
    }
}

class NativeProcess(
    val pid: Int,
    private val stdin: OutputStream,
    private val stdout: InputStream,
    private val stderr: InputStream
) : Process() {
    @Volatile
    private var exit: Int? = null
    private val lock = Object()

    override fun getOutputStream(): OutputStream = stdin
    override fun getInputStream(): InputStream = stdout
    override fun getErrorStream(): InputStream = stderr

    override fun waitFor(): Int {
        synchronized(lock) {
            exit?.let { return it }
        }
        val code = NativeExec.waitFor(pid)
        synchronized(lock) {
            if (exit == null) exit = code
            return exit!!
        }
    }

    override fun exitValue(): Int {
        exit?.let { return it }
        val code = NativeExec.pollExit(pid)
        if (code == -1000) throw IllegalThreadStateException("process has not exited")
        synchronized(lock) {
            if (exit == null) exit = code
            return exit!!
        }
    }

    override fun destroy() {
        NativeExec.killGroup(pid)
    }

    override fun isAlive(): Boolean = try {
        exitValue(); false
    } catch (_: IllegalThreadStateException) {
        true
    }
}
