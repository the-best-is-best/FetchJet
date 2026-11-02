/*
 * This file is derived from YTDLnis (https://github.com/deniscerri/ytdlnis),
 * Copyright (C) Denis Çerri and contributors, licensed under the GNU General Public License v3.0.
 * Modified for FetchJet (Copyright (C) 2026 Michelle Raouf). FetchJet as a whole is distributed
 * under the GNU General Public License v3.0 or later; see the LICENSE file in the repository root.
 */
package com.deniscerri.ytdl.core.packages

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.core.RuntimeManager
import com.deniscerri.ytdl.core.ZipUtils
import org.apache.commons.io.FileUtils
import java.io.File

abstract class PackageBase {
    protected abstract val executableName: String      // e.g., "ffmpeg"
    protected abstract val packageFolderName: String      // e.g., "ffmpeg"
    protected abstract val bundledZipName: String   // e.g., "libffmpeg.zip.so"
    protected abstract val canUninstall: Boolean   // set false if library is essential
    protected abstract val githubRepo: String  // e.g deniscerri/ytdlnis-packages
    protected abstract val githubPackageName: String  // e.g ffmpeg
    abstract val apkPackage: String // e.g. com.deniscerri.ytdl.ffmpeg
    fun getInstance(): PackageBase = this

    abstract val bundledVersion: String?
    var downloadedVersion: String? = null

    data class PackageLocation(
        val binDir: File,
        val ldDir: File,
        val executable: File,
        val isDownloaded: Boolean,
        val isBundled: Boolean,
        val isAvailable: Boolean,
        val canUninstall: Boolean
    )

    // Preferences Keys
    private val downloadedVersionKey = "${executableName}_downloaded_ver"
    private val bundledVerKey = "${executableName}_bundled_ver"

    private val packagesRoot = "packages"
    private val downloadedPackagesRoot = "downloaded_packages"

    lateinit var location: PackageLocation

    companion object

    fun init(context: Context) {
        val baseDir = File(context.noBackupFilesDir, RuntimeManager.BASENAME)
        val packageDir = File(baseDir, "$packagesRoot/$packageFolderName")
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        //try init bundled
        val bundledZip = File(context.applicationInfo.nativeLibraryDir, bundledZipName)
        initBundled(context, bundledZip, packageDir)
        if (apkPackage.isNotBlank()) {
            //try init from package apk
            try {
                context.packageManager.getApplicationInfo(apkPackage, 0)
            } catch (e: Exception) {
                null
            }?.apply {
                val apkBundledZip = File(this.nativeLibraryDir, bundledZipName)
                initBundled(
                    context,
                    apkBundledZip,
                    getDownloadedDir(context),
                    packageApkVersion = getDownloadedPackageAppVersion(context)
                )
            }
        }

        location = getLocation(context, baseDir)
        downloadedVersion = if (location.isDownloaded) {
            prefs.getString(downloadedVersionKey, null)
        } else {
            ""
        }
    }

    private fun initBundled(
        context: Context,
        bundledZip: File,
        targetDir: File,
        packageApkVersion: String? = null
    ) {
        if (!bundledZip.exists()) return

        val currentSize = bundledZip.length().toString()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        val bundleVerKey = prefs.getString(bundledVerKey, "")
        val downloadedVerKey = prefs.getString(downloadedVersionKey, "")

        val sizeMismatch =
            if (packageApkVersion != null) downloadedVerKey != packageApkVersion else bundleVerKey != currentSize

        if (!targetDir.exists() || sizeMismatch) {
            FileUtils.deleteQuietly(targetDir)
            targetDir.mkdirs()
            try {
                ZipUtils.unzip(bundledZip, targetDir)
                prefs.edit {
                    if (packageApkVersion != null) {
                        putString(downloadedVersionKey, packageApkVersion)
                    } else {
                        putString(bundledVerKey, currentSize)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun getDownloadedDir(context: Context): File {
        val baseDir = File(context.noBackupFilesDir, RuntimeManager.BASENAME)
        return File(baseDir, "$downloadedPackagesRoot/$packageFolderName")
    }

    fun getLocation(context: Context, baseDir: File): PackageLocation {
        //downloaded apk location
        var packageBinDir: String? = null
        if (apkPackage.isNotBlank()) {
            try {
                packageBinDir =
                    context.packageManager.getApplicationInfo(apkPackage, 0).nativeLibraryDir
            } catch (e: Exception) {
            }
        }
        val downloadedBinDir = File(packageBinDir ?: "")
        val downloadedLDDir = getDownloadedDir(context)
        val downloadedExe = File(packageBinDir, "lib$executableName.so")

        //bundled location
        val bundledBinDir = File(context.applicationInfo.nativeLibraryDir)
        val bundledLDDir = File(File(baseDir, packagesRoot), packageFolderName)
        val bundledExe = File(bundledBinDir, "lib$executableName.so")

        val isPackageActive = downloadedExe.exists()
        val isBundleActive = bundledExe.exists()

        val finalExe: File
        val binDir: File
        val ldDir: File

        if (isPackageActive) {
            finalExe = downloadedExe
            binDir = downloadedBinDir
            ldDir = downloadedLDDir
        } else {
            finalExe = bundledExe
            binDir = bundledBinDir
            ldDir = bundledLDDir
        }

        return PackageLocation(
            binDir = binDir,
            ldDir = ldDir,
            executable = finalExe,
            isDownloaded = isPackageActive,
            isBundled = isBundleActive,
            isPackageActive || isBundleActive,
            canUninstall
        )
    }

    fun getDownloadedPackageAppVersion(context: Context): String? {
        if (apkPackage.isBlank()) return null
        return try {
            val packageManager = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val info =
                    packageManager.getPackageInfo(apkPackage, PackageManager.PackageInfoFlags.of(0))
                "v${info.versionName}"
            } else {
                @Suppress("DEPRECATION")
                val info = packageManager.getPackageInfo(apkPackage, 0)
                "v${info.versionName}"
            }
        } catch (e: PackageManager.NameNotFoundException) {
            null // Package not installed
        }
    }

}