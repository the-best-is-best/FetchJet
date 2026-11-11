package com.michelle.raouf.fetch_jet.core.startup

import com.michelle.raouf.fetch_jet.platform.desktop.DesktopApp
import com.michelle.raouf.fetch_jet.platform.desktop.DesktopOs
import com.michelle.raouf.fetch_jet.platform.desktop.exists
import com.michelle.raouf.fetch_jet.platform.desktop.join
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

actual object StartupLauncher {
    private const val LABEL = "com.michelle.raouf.fetch_jet"

    private fun agentsDir(): String = DesktopOs.join(DesktopOs.getEnv("HOME").orEmpty(), "Library/LaunchAgents")

    private fun agentFile(): String = DesktopOs.join(agentsDir(), "$LABEL.plist")

    private fun xml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    actual val isSupported: Boolean
        get() = DesktopApp.launcherPath != null && !DesktopOs.getEnv("HOME").isNullOrBlank()

    actual val managedBySystemUri: String? = null

    actual suspend fun isEnabled(): Boolean = withContext(Dispatchers.IO) {
        val exe = DesktopApp.launcherPath ?: return@withContext false
        val text = DesktopOs.readText(agentFile()) ?: return@withContext false
        text.contains(xml(exe)) && text.contains(AUTOSTART_ARG)
    }

    actual suspend fun setEnabled(enabled: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (enabled) {
                val exe = DesktopApp.launcherPath ?: error("FetchJet is not installed")
                check(DesktopOs.exists(exe)) { "FetchJet was not found" }
                check(DesktopOs.createDirectories(agentsDir())) { "could not create ${agentsDir()}" }
                val text = buildString {
                    appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                    appendLine("<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">")
                    appendLine("<plist version=\"1.0\">")
                    appendLine("<dict>")
                    appendLine("  <key>Label</key>")
                    appendLine("  <string>$LABEL</string>")
                    appendLine("  <key>ProgramArguments</key>")
                    appendLine("  <array>")
                    appendLine("    <string>${xml(exe)}</string>")
                    appendLine("    <string>$AUTOSTART_ARG</string>")
                    appendLine("  </array>")
                    appendLine("  <key>RunAtLoad</key>")
                    appendLine("  <true/>")
                    appendLine("  <key>ProcessType</key>")
                    appendLine("  <string>Interactive</string>")
                    appendLine("</dict>")
                    appendLine("</plist>")
                }
                check(DesktopOs.writeText(agentFile(), text)) { "could not write the start-at-login entry" }
            } else if (DesktopOs.exists(agentFile())) {
                check(DesktopOs.deleteFile(agentFile())) { "could not remove the start-at-login entry" }
            }
        }
    }
}
