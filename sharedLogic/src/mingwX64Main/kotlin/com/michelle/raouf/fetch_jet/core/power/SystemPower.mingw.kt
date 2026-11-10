package com.michelle.raouf.fetch_jet.core.power

import com.michelle.raouf.fetch_jet.platform.desktop.DesktopOs
import com.michelle.raouf.fetch_jet.platform.desktop.runProcess
import com.michelle.raouf.fetch_jet.platform.win.winSuspend
import kotlin.concurrent.AtomicInt
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import platform.windows.GetLastError
import platform.windows.Sleep

actual object SystemPower {
    actual val isSupported: Boolean = true

    @OptIn(DelicateCoroutinesApi::class)
    actual fun sleep(): Result<Unit> = runCatching {
        println("WhenDone: SetSuspendState")
        val state = AtomicInt(0)
        val error = AtomicInt(0)
        GlobalScope.launch(Dispatchers.IO) {
            if (winSuspend(hibernate = false)) {
                state.value = 1
            } else {
                error.value = GetLastError().toInt()
                state.value = 2
            }
        }
        var waited = 0
        while (state.value == 0 && waited < 1000) {
            Sleep(50u)
            waited += 50
        }
        if (state.value == 2) error("sleep failed (${error.value})")
    }

    actual fun shutDown(): Result<Unit> = runCatching {
        println("WhenDone: shutdown /s /t 0")
        val result = runBlocking {
            DesktopOs.runProcess(listOf("shutdown.exe", "/s", "/t", "0"), mergeStderr = true, timeoutMs = 10_000)
        }
        if (!result.timedOut && result.exitCode != 0) {
            error(result.stdout.trim().ifBlank { "exit code ${result.exitCode}" })
        }
    }
}
