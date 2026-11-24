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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.sp
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

private enum class BrowserGlyph { BACK, FORWARD, RELOAD, DOWNLOAD }

private fun DrawScope.drawGlyph(kind: BrowserGlyph, color: Color, stroke: Float) {
    val w = size.width
    val h = size.height
    val style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (kind) {
        BrowserGlyph.BACK, BrowserGlyph.FORWARD -> {
            val flip = kind == BrowserGlyph.FORWARD
            fun x(f: Float) = if (flip) w * (1f - f) else w * f
            val p = Path().apply {
                moveTo(x(0.62f), h * 0.2f)
                lineTo(x(0.34f), h * 0.5f)
                lineTo(x(0.62f), h * 0.8f)
            }
            drawPath(p, color, style = style)
        }
        BrowserGlyph.RELOAD -> {
            val inset = w * 0.2f
            val rect = Size(w - inset * 2, h - inset * 2)
            val start = -40.0
            val sweep = 280.0
            drawArc(color, startAngle = start.toFloat(), sweepAngle = sweep.toFloat(), useCenter = false, topLeft = Offset(inset, inset), size = rect, style = style)
            val cx = w / 2
            val cy = h / 2
            val r = rect.width / 2
            val end = Math.toRadians(start + sweep)
            val ex = cx + r * Math.cos(end).toFloat()
            val ey = cy + r * Math.sin(end).toFloat()
            val tx = (-Math.sin(end)).toFloat()
            val ty = Math.cos(end).toFloat()
            val nx = Math.cos(end).toFloat()
            val ny = Math.sin(end).toFloat()
            val len = w * 0.2f
            val half = w * 0.14f
            val head = Path().apply {
                moveTo(ex + tx * len, ey + ty * len)
                lineTo(ex + nx * half, ey + ny * half)
                lineTo(ex - nx * half, ey - ny * half)
                close()
            }
            drawPath(head, color)
        }
        BrowserGlyph.DOWNLOAD -> {
            drawLine(color, Offset(w * 0.5f, h * 0.18f), Offset(w * 0.5f, h * 0.62f), strokeWidth = stroke, cap = StrokeCap.Round)
            val head = Path().apply {
                moveTo(w * 0.3f, h * 0.44f)
                lineTo(w * 0.5f, h * 0.64f)
                lineTo(w * 0.7f, h * 0.44f)
            }
            drawPath(head, color, style = style)
            drawLine(color, Offset(w * 0.24f, h * 0.82f), Offset(w * 0.76f, h * 0.82f), strokeWidth = stroke, cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun BrowserGlyphButton(kind: BrowserGlyph, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val tint = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.3f)
    Box(
        modifier = modifier.height(48.dp).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(28.dp)) { drawGlyph(kind, tint, 2.6.dp.toPx()) }
    }
}

@Composable
private fun BrowserDownloadButton(ready: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val container = MaterialTheme.colorScheme.primary.copy(alpha = if (ready) 1f else 0.6f)
    Box(
        modifier = modifier.size(56.dp).clip(CircleShape).background(container).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(26.dp)) { drawGlyph(BrowserGlyph.DOWNLOAD, Color.White, 2.8.dp.toPx()) }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal actual fun InAppBrowser(startUrl: String, onClose: () -> Unit) {
    val s = LocalizationManager.Strings
    val holder = remember { arrayOfNulls<WebView>(1) }
    var address by remember { mutableStateOf(TextFieldValue(startUrl)) }
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
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        singleLine = true,
                        placeholder = { Text(s.browserAddressHint) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = {
                            val target = browserAddressToUrl(address.text)
                            if (target.isNotEmpty()) holder[0]?.loadUrl(target)
                        }),
                        modifier = Modifier.weight(1f).onFocusChanged {
                            if (it.isFocused) address = address.copy(selection = TextRange(0, address.text.length))
                        }
                    )
                    TextButton(onClick = onClose) { Text(s.close) }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BrowserGlyphButton(BrowserGlyph.BACK, canBack, Modifier.weight(1f)) { holder[0]?.goBack() }
                    BrowserGlyphButton(BrowserGlyph.FORWARD, canForward, Modifier.weight(1f)) { holder[0]?.goForward() }
                    BrowserGlyphButton(BrowserGlyph.RELOAD, true, Modifier.weight(1f)) { holder[0]?.reload() }
                }
                if (progress in 0.01f..0.99f) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                }
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        WebView(context).apply {
                            if (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                                WebView.setWebContentsDebuggingEnabled(true)
                            }
                            layoutParams = android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT
                            )
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
                                    address = TextFieldValue(url)
                                    canBack = view.canGoBack()
                                    canForward = view.canGoForward()
                                }

                                override fun onPageFinished(view: WebView, url: String) {
                                    address = TextFieldValue(url)
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
                BrowserDownloadButton(
                    ready = media.isNotEmpty(),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                ) {
                    if (media.isNotEmpty()) send(holder[0], media)
                    else holder[0]?.url?.let { send(holder[0], it) }
                }
                }
            }
        }
    }
}
