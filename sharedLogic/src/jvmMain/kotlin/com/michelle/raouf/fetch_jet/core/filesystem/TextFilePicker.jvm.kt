package com.michelle.raouf.fetch_jet.core.filesystem

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.SwingUtilities

actual object TextFilePicker {
    actual val isSupported: Boolean
        get() = try {
            !java.awt.GraphicsEnvironment.isHeadless()
        } catch (_: Throwable) {
            false
        }

    actual fun pick(onPicked: (String?) -> Unit) {
        SwingUtilities.invokeLater {
            val text = try {
                val dialog = FileDialog(null as Frame?, "Import links", FileDialog.LOAD)
                dialog.setFilenameFilter { _, name -> name.lowercase().endsWith(".txt") }
                dialog.file = "*.txt"
                dialog.isVisible = true
                val dir = dialog.directory
                val file = dialog.file
                if (dir != null && file != null) File(dir, file).readText() else null
            } catch (_: Throwable) {
                null
            }
            onPicked(text)
        }
    }
}
