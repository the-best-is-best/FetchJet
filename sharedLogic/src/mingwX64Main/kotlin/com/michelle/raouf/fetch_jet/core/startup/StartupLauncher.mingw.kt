package com.michelle.raouf.fetch_jet.core.startup

import com.michelle.raouf.fetch_jet.platform.win.WinApp
import com.michelle.raouf.fetch_jet.platform.win.WinRegRoot
import com.michelle.raouf.fetch_jet.platform.win.winExists
import com.michelle.raouf.fetch_jet.platform.win.winRegDeleteValue
import com.michelle.raouf.fetch_jet.platform.win.winRegReadString
import com.michelle.raouf.fetch_jet.platform.win.winRegWriteString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

actual object StartupLauncher {
    private const val RUN_KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val VALUE_NAME = "com.michelle.raouf.fetch_jet"

    actual val isSupported: Boolean
        get() = try {
            !WinApp.isStoreBuild && WinApp.launcherPath != null
        } catch (_: Throwable) {
            false
        }

    actual val managedBySystemUri: String?
        get() = if (WinApp.isStoreBuild) "ms-settings:startupapps" else null

    actual suspend fun isEnabled(): Boolean = withContext(Dispatchers.IO) {
        try {
            val exe = WinApp.launcherPath
            if (!isSupported || exe == null) return@withContext false
            val value = winRegReadString(WinRegRoot.CURRENT_USER, RUN_KEY, VALUE_NAME) ?: return@withContext false
            value.contains(exe, ignoreCase = true) && value.contains(AUTOSTART_ARG)
        } catch (_: Throwable) {
            false
        }
    }

    actual suspend fun setEnabled(enabled: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (enabled) {
                val exe = WinApp.launcherPath ?: error("FetchJet is not installed")
                check(winExists(exe)) { "FetchJet.exe was not found" }
                check(winRegWriteString(WinRegRoot.CURRENT_USER, RUN_KEY, VALUE_NAME, "\"$exe\" $AUTOSTART_ARG")) {
                    "could not write the startup entry"
                }
            } else if (winRegReadString(WinRegRoot.CURRENT_USER, RUN_KEY, VALUE_NAME) != null) {
                check(winRegDeleteValue(WinRegRoot.CURRENT_USER, RUN_KEY, VALUE_NAME)) {
                    "could not remove the startup entry"
                }
            }
        }
    }
}
