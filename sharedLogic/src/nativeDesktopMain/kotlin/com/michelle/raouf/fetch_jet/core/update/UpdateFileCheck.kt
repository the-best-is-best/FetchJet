package com.michelle.raouf.fetch_jet.core.update

import com.michelle.raouf.fetch_jet.core.filesystem.PrivateFolder
import com.michelle.raouf.fetch_jet.platform.desktop.DesktopOs

/** Checks the downloaded update again right before it is run; returns its folder. */
internal fun checkUpdateFileBeforeInstall(installerPath: String): String {
    val folder = installerPath.substringBeforeLast('/', "")
    check(folder.isNotEmpty() && PrivateFolder.ensure(folder)) { "the update folder can be changed by another user of this computer" }
    check(PrivateFolder.isOwnFile(installerPath)) { "the update file can be changed by another user of this computer" }
    val signature = DesktopOs.readText("$installerPath.sig")
    check(signature != null && verifyUpdateSignature(installerPath, signature)) { "the update file signature is not valid" }
    return folder
}
