package xyz.mdhv.formanalyser.app

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import xyz.mdhv.formanalyser.app.data.AppPrefs
import xyz.mdhv.formanalyser.app.ui.theme.FormAnalyserTheme
import xyz.mdhv.formanalyser.app.ui.theme.ThemeMode
import java.io.ByteArrayInputStream

/**
 * Isolated, offline-only equipment viewer. No model downloads, JS/native bridge, athlete data,
 * file/content access, camera or microphone. The local HTTPS origin maps only to bundled assets.
 * This activity is not exported; main scoring/capture and the competing PR #10 are untouched.
 */
class EquipmentAtelierActivity : ComponentActivity() {
    private var studio: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val previous = savedInstanceState?.getString("atelier.url")
        setContent {
            val prefs = remember { AppPrefs(this@EquipmentAtelierActivity) }
            val stored by prefs.themeMode.collectAsState(initial = ThemeMode.SYSTEM.name)
            FormAnalyserTheme(ThemeMode.fromStorage(stored)) {
                val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
                val theme = if (dark) "dark" else "light"
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = { finish() }) { Text("Back") }
                            Text("Equipment studio", style = MaterialTheme.typography.titleMedium)
                        }
                        AndroidView(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            factory = { context ->
                                WebView(context).apply {
                                    settings.javaScriptEnabled = true
                                    settings.allowFileAccess = false
                                    settings.allowContentAccess = false
                                    settings.domStorageEnabled = false
                                    settings.databaseEnabled = false
                                    settings.blockNetworkLoads = true
                                    settings.cacheMode = WebSettings.LOAD_NO_CACHE
                                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                    settings.mediaPlaybackRequiresUserGesture = true
                                    settings.setSupportMultipleWindows(false)
                                    setBackgroundColor(if (dark) 0xff0d1714.toInt() else 0xffeeede5.toInt())
                                    webViewClient = LocalStudioClient()
                                    studio = this
                                    val restored = previous?.takeIf { isLocal(Uri.parse(it)) }
                                    loadUrl(restored ?: "$ORIGIN/atelier/index.html#theme=$theme")
                                }
                            },
                            update = { view ->
                                view.setBackgroundColor(if (dark) 0xff0d1714.toInt() else 0xffeeede5.toInt())
                                // Only a fixed enum enters JS, never user strings or athlete data.
                                view.evaluateJavascript("window.CrocodylAtelier?.setTheme('$theme')", null)
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        studio?.url?.let { outState.putString("atelier.url", it) }
        super.onSaveInstanceState(outState)
    }

    override fun onPause() {
        studio?.evaluateJavascript("window.CrocodylAtelier?.pause()", null)
        studio?.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        studio?.onResume()
        studio?.evaluateJavascript("window.CrocodylAtelier?.resume()", null)
    }

    override fun onDestroy() {
        studio?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
            view.stopLoading()
            view.destroy()
        }
        studio = null
        super.onDestroy()
    }

    private inner class LocalStudioClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            !isLocal(request.url)

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest,
        ): WebResourceResponse {
            if (!isLocal(request.url) || request.method != "GET") return denied()
            val path = request.url.path?.removePrefix("/") ?: return denied()
            // Decoded path allowlist also rejects traversal, backslashes and encoded separators.
            if (!ASSET_PATH.matches(path)) return denied()
            val mime = when (path.substringAfterLast('.')) {
                "html" -> "text/html"
                "css" -> "text/css"
                "js" -> "application/javascript"
                "json" -> "application/json"
                "glb" -> "model/gltf-binary"
                "jpg" -> "image/jpeg"
                "webp" -> "image/webp"
                else -> return denied()
            }
            return try {
                WebResourceResponse(
                    mime, if (mime.startsWith("text/") || mime.endsWith("javascript") || mime.endsWith("json")) "UTF-8" else null,
                    200, "OK", mapOf("Cache-Control" to "no-store", "X-Content-Type-Options" to "nosniff"),
                    assets.open(path),
                )
            } catch (_: java.io.IOException) {
                WebResourceResponse("text/plain", "UTF-8", 404, "Not found", emptyMap(), ByteArrayInputStream(ByteArray(0)))
            }
        }
    }

    private fun denied() = WebResourceResponse(
        "text/plain", "UTF-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)),
    )

    companion object {
        private const val ORIGIN = "https://crocodyl.local"
        private val ASSET_PATH = Regex("atelier/(?:[A-Za-z0-9_-]+/)*[A-Za-z0-9_-]+\\.(?:html|css|js|json|glb|jpg|webp)")
        private fun isLocal(uri: Uri) = uri.scheme == "https" && uri.host == "crocodyl.local" &&
            (uri.port == -1 || uri.port == 443) && uri.path?.startsWith("/atelier/") == true
    }
}
