package com.michelle.raouf.fetch_jet.core.filesystem

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings

internal object AndroidStorageAccess {
    fun install(context: Context) {
        val app = context.applicationContext
        AllFilesAccess.needed = { Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager() }
        AllFilesAccess.openSettings = {
            try {
                app.startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + app.packageName))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {
                try {
                    app.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) {
                }
            }
        }
    }
}
