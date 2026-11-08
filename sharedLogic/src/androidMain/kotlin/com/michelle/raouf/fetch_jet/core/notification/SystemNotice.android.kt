package com.michelle.raouf.fetch_jet.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.tbib.kmp_localization.LocalizationManager

actual object SystemNotice {
    private const val CHANNEL = "fetchjet_devices"

    actual fun show(id: String, title: String, text: String) {
        val ctx = DownloadNotificationManager.appContext ?: PathResolver.appContext ?: return
        try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL,
                        LocalizationManager.Strings.sysDeviceSendTitle,
                        NotificationManager.IMPORTANCE_DEFAULT
                    )
                )
            }
            val open = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
            val pi = open?.let {
                PendingIntent.getActivity(
                    ctx, id.hashCode(), it,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }
            val builder = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
            if (pi != null) builder.setContentIntent(pi)
            nm.notify(id.hashCode(), builder.build())
        } catch (_: Throwable) {
        }
    }
}
