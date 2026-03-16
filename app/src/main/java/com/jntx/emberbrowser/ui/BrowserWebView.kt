package com.jntx.emberbrowser.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Message
import android.view.View
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import com.jntx.emberbrowser.UserAgentMode
import com.jntx.emberbrowser.utils.TtsInterface

private val AD_BLOCK_DOMAINS = setOf(
    "doubleclick.net", "googleadservices.com", "googlesyndication.com", "adnxs.com",
    "ads.twitter.com", "amazon-adsystem.com", "moatads.com", "outbrain.com",
    "taboola.com", "criteo.com", "pubmatic.com", "rubiconproject.com"
)
private val BLOCKED_RESPONSE = WebResourceResponse("text/plain", "utf-8", null)

private const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
private const val IOS_UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1"
private const val TABLET_UA = "Mozilla/5.0 (iPad; CPU OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1"
private const val ANDROID_14_UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Build/UD1A.230805.019) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserWebViewContainer(
    url: String,
    onPageFinished: (String, String?) -> Unit,
    onDownloadRequested: (String) -> Unit,
    onWebViewCreated: (WebView) -> Unit,
    onSpeak: (String) -> Unit,
    onGetVoices: () -> String,
    onProgressChanged: (Int) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onFaviconChanged: (Bitmap?) -> Unit,
    onPermissionRequested: (PermissionRequest, String) -> Unit,
    onGeolocationRequested: (String, GeolocationPermissions.Callback) -> Unit,
    onImageLongClick: (String) -> Unit,
    enhancedProtection: Boolean,
    onMediaStatus: (String, String?, Boolean) -> Unit,
    isAdBlockerEnabled: Boolean,
    userAgentMode: UserAgentMode,
    textZoom: Int = 100
) {
    val pullToRefreshState = rememberPullToRefreshState()
    var customView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    if (customView != null) {
        BackHandler {
            customViewCallback?.onCustomViewHidden()
            customView = null
            customViewCallback = null
        }
        AndroidView(
            factory = { customView!! },
            modifier = Modifier.fillMaxSize()
        )
    } else {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            state = pullToRefreshState,
            modifier = Modifier.fillMaxSize()
        ) {
            BrowserWebView(
                url = url,
                onPageFinished = onPageFinished,
                onDownloadRequested = onDownloadRequested,
                onWebViewCreated = onWebViewCreated,
                onSpeak = onSpeak,
                onGetVoices = onGetVoices,
                onProgressChanged = onProgressChanged,
                onFaviconChanged = onFaviconChanged,
                onPermissionRequested = onPermissionRequested,
                onGeolocationRequested = onGeolocationRequested,
                onImageLongClick = onImageLongClick,
                enhancedProtection = enhancedProtection,
                onMediaStatus = onMediaStatus,
                isAdBlockerEnabled = isAdBlockerEnabled,
                userAgentMode = userAgentMode,
                textZoom = textZoom,
                onShowCustomView = { view, callback ->
                    customView = view
                    customViewCallback = callback
                },
                onHideCustomView = {
                    customView = null
                    customViewCallback = null
                }
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserWebView(
    url: String,
    onPageFinished: (String, String?) -> Unit,
    onDownloadRequested: (String) -> Unit,
    onWebViewCreated: (WebView) -> Unit,
    onSpeak: (String) -> Unit,
    onGetVoices: () -> String,
    onProgressChanged: (Int) -> Unit,
    onFaviconChanged: (Bitmap?) -> Unit,
    onPermissionRequested: (PermissionRequest, String) -> Unit,
    onGeolocationRequested: (String, GeolocationPermissions.Callback) -> Unit,
    onImageLongClick: (String) -> Unit,
    enhancedProtection: Boolean,
    @Suppress("UNUSED_PARAMETER") onMediaStatus: (String, String?, Boolean) -> Unit,
    isAdBlockerEnabled: Boolean,
    userAgentMode: UserAgentMode,
    textZoom: Int,
    onShowCustomView: (View, WebChromeClient.CustomViewCallback) -> Unit,
    onHideCustomView: () -> Unit
) {
    val context = LocalContext.current
    val androidUA = remember { WebSettings.getDefaultUserAgent(context) }

    fun resolveUA(mode: UserAgentMode): String {
        return when (mode) {
            UserAgentMode.ANDROID -> androidUA
            UserAgentMode.PC -> DESKTOP_UA
            UserAgentMode.IOS -> IOS_UA
            UserAgentMode.TABLET -> TABLET_UA
            UserAgentMode.ANDROID_14 -> ANDROID_14_UA
        }
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                isVerticalScrollBarEnabled = true
                isHorizontalScrollBarEnabled = true
                isFocusable = true
                isFocusableInTouchMode = true

                settings.apply {
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
                    defaultFontSize = 16
                    minimumFontSize = 8
                    minimumLogicalFontSize = 8
                    defaultTextEncodingName = "UTF-8"

                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                    this.textZoom = textZoom

                    javaScriptEnabled = true
                    domStorageEnabled = true
                    javaScriptCanOpenWindowsAutomatically = true
                    setSupportMultipleWindows(true)

                    allowFileAccess = true
                    allowContentAccess = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    cacheMode = WebSettings.LOAD_DEFAULT
                    mediaPlaybackRequiresUserGesture = false

                    safeBrowsingEnabled = true
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        @Suppress("DEPRECATION")
                        forceDark = WebSettings.FORCE_DARK_AUTO
                    }

                    userAgentString = resolveUA(userAgentMode)
                }

                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                addJavascriptInterface(TtsInterface(onSpeak, onGetVoices), "EmberTTS")

                setOnLongClickListener {
                    val result = hitTestResult
                    if (result.type == WebView.HitTestResult.IMAGE_TYPE ||
                        result.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
                    ) {
                        result.extra?.let { onImageLongClick(it) }
                        true
                    } else false
                }

                setDownloadListener { downloadUrl, _, _, _, _ -> onDownloadRequested(downloadUrl) }

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val urlString = request?.url?.toString() ?: return false
                        if (!urlString.startsWith("http") && !urlString.startsWith("file://")) {
                            return try {
                                val intent = Intent(Intent.ACTION_VIEW, urlString.toUri())
                                context.startActivity(intent)
                                true
                            } catch (e: Exception) { true }
                        }
                        return false
                    }

                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                        if (isAdBlockerEnabled) {
                            val host = request?.url?.host ?: ""
                            if (AD_BLOCK_DOMAINS.any { domain -> host.endsWith(domain) }) {
                                return BLOCKED_RESPONSE
                            }
                        }
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        if (url != null) {
                            onPageFinished(url, view?.title)
                        }
                        CookieManager.getInstance().flush()
                    }

                    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: android.net.http.SslError?) {
                        if (enhancedProtection) handler?.cancel() else handler?.proceed()
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onPermissionRequest(request: PermissionRequest) { onPermissionRequested(request, url) }
                    override fun onProgressChanged(view: WebView?, newProgress: Int) { onProgressChanged(newProgress) }
                    override fun onReceivedIcon(view: WebView?, icon: Bitmap?) { onFaviconChanged(icon) }
                    override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                        onGeolocationRequested(origin, callback)
                    }

                    override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
                        if (!isUserGesture) return false
                        val transport = resultMsg?.obj as? WebView.WebViewTransport
                        transport?.webView = this@apply
                        resultMsg?.sendToTarget()
                        return true
                    }

                    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                        if (view != null && callback != null) onShowCustomView(view, callback)
                    }

                    override fun onHideCustomView() { onHideCustomView() }
                }

                onWebViewCreated(this)
                loadUrl(url)
            }
        },
        update = { webView ->
            webView.settings.userAgentString = resolveUA(userAgentMode)
            webView.settings.textZoom = textZoom
            if (webView.url != url && url != "home") {
                webView.loadUrl(url)
            }
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.clearHistory()
            webView.removeAllViews()
            webView.destroy()
        },
        modifier = Modifier.fillMaxSize()
    )
}
