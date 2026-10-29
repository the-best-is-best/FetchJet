package com.michelle.raouf.fetch_jet

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.michelle.raouf.fetch_jet.core.notification.DownloadNotificationManager

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    // "Open .torrent file" in the Add dialog: the system document picker.
    private var torrentPickCallback: ((android.net.Uri?) -> Unit)? = null
    private val torrentPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val cb = torrentPickCallback
        torrentPickCallback = null
        cb?.invoke(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        super.onCreate(savedInstanceState)

        PathResolver.appContext = applicationContext
        DownloadNotificationManager.setContext(applicationContext)
        com.michelle.raouf.fetch_jet.core.log.AppLog.verbose = BuildConfig.VERBOSE_LOGS
        com.michelle.raouf.fetch_jet.core.filesystem.TorrentFilePicker.launcher = { onResult ->
            torrentPickCallback = onResult
            try {
                torrentPicker.launch(
                    arrayOf(
                        "application/x-bittorrent",
                        "application/octet-stream",
                        "*/*"
                    )
                )
            } catch (_: Throwable) {
                torrentPickCallback = null
                onResult(null)
            }
        }
        handleIncomingIntent(intent)
        com.michelle.raouf.fetch_jet.feature.media_extractor.triggerBackgroundYtDlpUpdate(
            applicationContext
        )

        requestPermissionsIfNeeded()

        setContent {
            var isDark by remember { mutableStateOf<Boolean?>(null) }

            DisposableEffect(isDark) {
                val dark = isDark ?: false
                val statusBarStyle = if (dark) {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    )
                }
                val navigationBarStyle = if (dark) {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    )
                }
                enableEdgeToEdge(
                    statusBarStyle = statusBarStyle,
                    navigationBarStyle = navigationBarStyle
                )
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
                onDispose {}
            }

            App(
                onDarkModeChanged = { isDark = it }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    /** A .torrent file or a magnet link opened with FetchJet. */
    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        // Consume it: a rotation must not add the same torrent again.
        intent.data = null
        intent.action = null
        val target = when (uri.scheme?.lowercase()) {
            "magnet" -> uri.toString()
            "content", "file" ->
                com.michelle.raouf.fetch_jet.core.filesystem.TorrentFilePicker.copyTorrentToCache(
                    applicationContext, uri
                )

            "http", "https" -> uri.toString()
            else -> null
        } ?: return
        com.michelle.raouf.fetch_jet.core.network.ExternalDownloadBridge.dispatch(target)
    }

    private fun requestPermissionsIfNeeded() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_MEDIA_VIDEO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_VIDEO)
            }
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_MEDIA_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_MEDIA_IMAGES
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
        requestBatteryOptimizationExemptionIfNeeded()
    }

    private fun requestBatteryOptimizationExemptionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val powerManager = getSystemService(POWER_SERVICE) as? PowerManager
                if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(packageName)) {
                    val intent =
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:$packageName")
                        }
                    startActivity(intent)
                }
            } catch (_: Throwable) {
            }
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}