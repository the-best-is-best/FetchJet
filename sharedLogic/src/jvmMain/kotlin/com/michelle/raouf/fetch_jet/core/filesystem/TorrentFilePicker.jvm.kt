package com.michelle.raouf.fetch_jet.core.filesystem

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.SwingUtilities

actual object TorrentFilePicker {
    actual val isSupported: Boolean
        get() = try {
            !java.awt.GraphicsEnvironment.isHeadless()
        } catch (_: Throwable) {
            false
        }

    actual fun pick(onPicked: (String?) -> Unit) {
        // The native dialog (Explorer / Finder / GTK) rather than Swing's own look.
        SwingUtilities.invokeLater {
            val path = try {
                val dialog = FileDialog(null as Frame?, "Open .torrent file", FileDialog.LOAD)
                dialog.setFilenameFilter { _, name -> name.lowercase().endsWith(".torrent") }
                dialog.file = "*.torrent"
                dialog.isVisible = true
                val dir = dialog.directory
                val file = dialog.file
                if (dir != null && file != null) File(dir, file).absolutePath else null
            } catch (_: Throwable) {
                null
            }
            onPicked(path)
        }
    }
}
