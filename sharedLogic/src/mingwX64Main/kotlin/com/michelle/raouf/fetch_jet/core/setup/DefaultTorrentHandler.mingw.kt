package com.michelle.raouf.fetch_jet.core.setup

import com.michelle.raouf.fetch_jet.core.setup.WindowsDefaultApps.Root
import com.michelle.raouf.fetch_jet.platform.desktop.DesktopOs
import com.michelle.raouf.fetch_jet.platform.win.WinApp
import com.michelle.raouf.fetch_jet.platform.win.WinRegRoot
import com.michelle.raouf.fetch_jet.platform.win.winRegReadString
import com.michelle.raouf.fetch_jet.platform.win.winRegWriteString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/** Windows: only the user can pick the default, in Settings > Default apps. */
actual object DefaultTorrentHandler {
    actual val supported: Boolean get() = WinApp.launcherPath != null
    actual val opensSystemPage: Boolean = true

    private fun read(root: Root, path: String, name: String?): String? = winRegReadString(
        if (root == Root.CURRENT_USER) WinRegRoot.CURRENT_USER else WinRegRoot.LOCAL_MACHINE, path, name
    )

    actual suspend fun isDefault(): Boolean? = withContext(Dispatchers.IO) {
        runCatching { WindowsDefaultApps.isDefault(::read) }.getOrNull()
    }

    actual suspend fun makeDefault(): Boolean = withContext(Dispatchers.IO) {
        val exe = WinApp.launcherPath ?: return@withContext false
        if (!WinApp.isStoreBuild) {
            for (value in WindowsDefaultApps.registration(exe, WindowsDefaultApps.magnetKeyMissing(::read))) {
                winRegWriteString(WinRegRoot.CURRENT_USER, value.path, value.name, value.data)
            }
        }
        DesktopOs.openExternal(WindowsDefaultApps.settingsUri(exe))
    }
}
