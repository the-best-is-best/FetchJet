package com.michelle.raouf.fetch_jet.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.tbib.kmp_localization.LocalizationManager

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal actual fun SiteSignInDialog(site: String, onFinished: (cookie: String?) -> Unit) {
    val s = LocalizationManager.Strings
    val address = if (site.contains("://")) site else "https://$site"
    val webView = remember { arrayOfNulls<WebView>(1) }
    Dialog(
        onDismissRequest = { onFinished(null) },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = s.netLoginBrowserHint,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
                AndroidView(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    factory = { context ->
                        WebView(context).apply {
                            layoutParams = android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            webViewClient = WebViewClient()
                            webView[0] = this
                            loadUrl(address)
                        }
                    },
                    onRelease = { it.destroy(); webView[0] = null }
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { onFinished(null) }) { Text(s.cancel) }
                    Button(onClick = {
                        val current = webView[0]?.url
                        val wanted = com.michelle.raouf.fetch_jet.core.network.SiteLogin.cleanSite(site)
                        val onSite = current != null && com.michelle.raouf.fetch_jet.core.network.SiteLogin(wanted, "", "")
                            .matches(android.net.Uri.parse(current).host.orEmpty())
                        val manager = CookieManager.getInstance()
                        manager.flush()
                        onFinished(manager.getCookie(if (onSite) current else address)?.takeIf { it.isNotBlank() })
                    }) { Text(s.netLoginBrowserDone) }
                }
            }
        }
    }
}
