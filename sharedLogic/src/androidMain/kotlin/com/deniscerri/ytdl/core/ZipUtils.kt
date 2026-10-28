/*
 * This file is derived from YTDLnis (https://github.com/deniscerri/ytdlnis),
 * Copyright (C) Denis Çerri and contributors, licensed under the GNU General Public License v3.0.
 * Modified for FetchJet (Copyright (C) 2026 Michelle Raouf). FetchJet as a whole is distributed
 * under the GNU General Public License v3.0 or later; see the LICENSE file in the repository root.
 */
package com.deniscerri.ytdl.core

import android.system.Os
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.io.IOUtils
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets

object ZipUtils {
    fun unzip(sourceFile: File?, targetDirectory: File) {
        ZipFile(sourceFile).use { zipFile ->
            val entries = zipFile.entries
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val entryDestination = File(targetDirectory, entry.name)
                // prevent zipSlip
                if (!entryDestination.canonicalPath.startsWith(targetDirectory.canonicalPath + File.separator)) {
                    throw IllegalAccessException("Entry is outside of the target dir: " + entry.name)
                }
                if (entry.isDirectory) {
                    entryDestination.mkdirs()
                } else if (entry.isUnixSymlink) {
                    zipFile.getInputStream(entry).use { `in` ->
                        val symlink = IOUtils.toString(`in`, StandardCharsets.UTF_8)
                        Os.symlink(symlink, entryDestination.absolutePath)
                    }
                } else {
                    entryDestination.parentFile?.mkdirs()
                    zipFile.getInputStream(entry).use { `in` ->
                        FileOutputStream(entryDestination).use { out ->
                            IOUtils.copy(
                                `in`,
                                out
                            )
                        }
                    }
                }
            }
        }
    }

    fun unzip(inputStream: InputStream?, targetDirectory: File) {
        ZipArchiveInputStream(BufferedInputStream(inputStream)).use { zis ->
            var entry: ZipArchiveEntry? = null
            while (zis.nextZipEntry.also { entry = it } != null) {
                val entryDestination = File(targetDirectory, entry!!.name)
                // prevent zipSlip
                if (!entryDestination.canonicalPath.startsWith(targetDirectory.canonicalPath + File.separator)) {
                    throw IllegalAccessException("Entry is outside of the target dir: " + entry.name)
                }
                if (entry.isDirectory) {
                    entryDestination.mkdirs()
                } else {
                    entryDestination.parentFile.mkdirs()
                    FileOutputStream(entryDestination).use { out -> IOUtils.copy(zis, out) }
                }
            }
        }
    }
}