package com.michelle.raouf.fetch_jet.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.core.network.ExternalDownloadBridge
import com.michelle.raouf.fetch_jet.core.network.ExternalDownloadRequest
import com.tbib.kmp_localization.LocalizationManager

private const val FINDER = """
(function () {
  var last = "";
  function look() {
    var found = "";
    var list = document.querySelectorAll("video, audio");
    for (var i = 0; i < list.length; i++) {
      var s = list[i].currentSrc || list[i].src || "";
      if (/^https?:/i.test(s) && !/\.m3u8|\.mpd/i.test(s)) { found = s; break; }
    }
    if (found !== last) {
      last = found;
      try { FJMedia.found(found); } catch (e) {}
    }
  }
  setInterval(look, 700);
  look();
})();
"""

private class MediaBridge(private val onFound: (String) -> Unit) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun found(url: String) {
        main.post { onFound(url) }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal actual fun InAppBrowser(startUrl: String, onClose: () -> Unit) {
    val s = LocalizationManager.Strings
    val holder = remember { arrayOfNulls<WebView>(1) }
    var address by remember { mutableStateOf(startUrl) }
    var progress by remember { mutableFloatStateOf(0f) }
    var canBack by remember { mutableStateOf(false) }
    var canForward by remember { mutableStateOf(false) }
    var media by remember { mutableStateOf("") }

    fun send(view: WebView?, url: String, fileName: String = "") {
        val page = view?.url.orEmpty()
        val cookie = CookieManager.getInstance().getCookie(url).orEmpty()
        ExternalDownloadBridge.dispatch(
            ExternalDownloadRequest(
                url = url,
                cookie = cookie,
                referer = page,
                userAgent = view?.settings?.userAgentString.orEmpty(),
                fileName = fileName
            )
        )
        onClose()
    }

    BackHandler {
        val view = holder[0]
        if (view != null && view.canGoBack()) view.goBack() else onClose()
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    TextButton(onClick = { holder[0]?.goBack() }, enabled = canBack) { Text("‹") }
                    TextButton(onClick = { holder[0]?.goForward() }, enabled = canForward) { Text("›") }
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        singleLine = true,
                        placeholder = { Text(s.browserAddressHint) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = {
                            val target = browserAddressToUrl(address)
                            if (target.isNotEmpty()) holder[0]?.loadUrl(target)
                        }),
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { holder[0]?.reload() }) { Text("⟳") }
                    TextButton(onClick = onClose) { Text(s.close) }
                }
                if (progress in 0.01f..0.99f) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                }
                AndroidView(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.javaScriptCanOpenWindowsAutomatically = true
                            settings.setSupportMultipleWindows(true)
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            addJavascriptInterface(MediaBridge { media = it }, "FJMedia")
                            setDownloadListener { url, _, contentDisposition, mimeType, _ ->
                                send(this, url, URLUtil.guessFileName(url, contentDisposition, mimeType))
                            }
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                    val scheme = request.url.scheme?.lowercase()
                                    return scheme != "http" && scheme != "https"
                                }

                                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                                    media = ""
                                    address = url
                                    canBack = view.canGoBack()
                                    canForward = view.canGoForward()
                                }

                                override fun onPageFinished(view: WebView, url: String) {
                                    address = url
                                    canBack = view.canGoBack()
                                    canForward = view.canGoForward()
                                    view.evaluateJavascript(FINDER, null)
                                }
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView, newProgress: Int) {
                                    progress = newProgress / 100f
                                }

                                override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                                    val popup = WebView(view.context)
                                    popup.webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                                            view.loadUrl(request.url.toString())
                                            v.destroy()
                                            return true
                                        }
                                    }
                                    (resultMsg.obj as WebView.WebViewTransport).webView = popup
                                    resultMsg.sendToTarget()
                                    return true
                                }
                            }
                            holder[0] = this
                            loadUrl(browserAddressToUrl(startUrl).ifEmpty { "https://www.google.com" })
                        }
                    },
                    onRelease = { it.destroy(); holder[0] = null }
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { holder[0]?.url?.let { send(holder[0], it) } },
                        modifier = Modifier.weight(1f)
                    ) { Text(s.browserSendPage) }
                    Button(
                        onClick = { send(holder[0], media) },
                        enabled = media.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) { Text(s.browserDownloadMedia) }
                }
            }
        }
    }
}
