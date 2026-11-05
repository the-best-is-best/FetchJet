package com.michelle.raouf.fetch_jet.core.update

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.tbib.kmp_localization.LocalizationManager
import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import java.io.File

actual object PlatformUpdater {
    /** FetchJet downloads the APK itself and opens the system installer for it. */
    actual val canInstallInApp: Boolean = true
    actual val platformKey: String = "android"
    actual val updatesFromStore: Boolean = false

    actual fun latestStoreVersion(): String? = null

    /** The APK built for this device's CPU (the release has one per ABI), else any APK. */
    actual fun pickAsset(assets: List<ReleaseAsset>): ReleaseAsset? {
        val apks = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        val abis = Build.SUPPORTED_ABIS.orEmpty()
        return abis.firstNotNullOfOrNull { abi ->
            apks.firstOrNull {
                it.name.contains(
                    abi,
                    ignoreCase = true
                )
            }
        }
            ?: apks.firstOrNull { it.name.contains("universal", ignoreCase = true) }
            ?: apks.firstOrNull()
    }

    /** Opens Android's installer for the downloaded APK; FetchJet keeps running until it replaces it. */
    actual fun launchInstallerAndExit(installerPath: String): Result<Unit> = runCatching {
        val ctx = PathResolver.appContext ?: error("the app is not ready")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${ctx.packageName}")
                )
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            error(LocalizationManager.Strings.sysAllowUnknownAppsThenInstall)
        }
        val uri =
            FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", File(installerPath))
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
