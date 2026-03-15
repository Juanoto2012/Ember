package com.jntx.emberbrowser.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.jntx.emberbrowser.utils.TtsInterface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserWebViewContainer(
    url: String,
    onPageFinished: (String, String?) -> Unit,
    onDownloadRequested: (String) -> Unit,
    onWebViewCreated: (WebView) -> Unit,
    onSpeak: (String) -> Unit,
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
    isPcMode: Boolean
) {
    val pullToRefreshState = rememberPullToRefreshState()
    
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
            onProgressChanged = onProgressChanged,
            onFaviconChanged = onFaviconChanged,
            onPermissionRequested = onPermissionRequested,
            onGeolocationRequested = onGeolocationRequested,
            onImageLongClick = onImageLongClick,
            enhancedProtection = enhancedProtection,
            onMediaStatus = onMediaStatus,
            isAdBlockerEnabled = isAdBlockerEnabled,
            isPcMode = isPcMode
        )
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
    onProgressChanged: (Int) -> Unit,
    onFaviconChanged: (Bitmap?) -> Unit,
    onPermissionRequested: (PermissionRequest, String) -> Unit,
    onGeolocationRequested: (String, GeolocationPermissions.Callback) -> Unit,
    onImageLongClick: (String) -> Unit,
    enhancedProtection: Boolean,
    onMediaStatus: (String, String?, Boolean) -> Unit,
    isAdBlockerEnabled: Boolean,
    isPcMode: Boolean
) {
    val androidUA = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    val desktopUA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    javaScriptCanOpenWindowsAutomatically = true
                    allowFileAccess = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    
                    // PC Mode settings
                    userAgentString = if (isPcMode) desktopUA else androidUA
                    useWideViewPort = isPcMode
                    loadWithOverviewMode = isPcMode
                }
                
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                addJavascriptInterface(TtsInterface(onSpeak), "EmberTTS")
                
                setOnLongClickListener {
                    val result = hitTestResult
                    if (result.type == WebView.HitTestResult.IMAGE_TYPE || result.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                        result.extra?.let { onImageLongClick(it) }
                        true
                    } else false
                }
                
                setDownloadListener { downloadUrl, _, _, _, _ -> onDownloadRequested(downloadUrl) }
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                        if (isAdBlockerEnabled) {
                            val adDomains = listOf("doubleclick.net", "googleadservices.com", "googlesyndication.com")
                            val urlString = request?.url?.toString() ?: ""
                            for (domain in adDomains) {
                                if (urlString.contains(domain)) return WebResourceResponse("text/plain", "utf-8", null)
                            }
                        }
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        CookieManager.getInstance().flush()
                        
                        if (url != null) {
                            onPageFinished(url, view?.title)
                            
                            view?.evaluateJavascript("""
                                (function() {
                                    var media = document.querySelector('video') || document.querySelector('audio');
                                    if (media) {
                                        var updateMedia = function() {
                                            var title = document.title;
                                            var artist = "Ember Browser";
                                            var artwork = "";
                                            
                                            if (navigator.mediaSession && navigator.mediaSession.metadata) {
                                                title = navigator.mediaSession.metadata.title || title;
                                                artist = navigator.mediaSession.metadata.artist || artist;
                                                if (navigator.mediaSession.metadata.artwork && navigator.mediaSession.metadata.artwork.length > 0) {
                                                    artwork = navigator.mediaSession.metadata.artwork[navigator.mediaSession.metadata.artwork.length - 1].src;
                                                }
                                            }
                                            
                                            if (artwork) { EmberTTS.speak("__MEDIA_ARTWORK__" + artwork); }
                                            
                                            if (!media.paused) {
                                                EmberTTS.speak("__MEDIA_PLAY__" + title + "|" + artist);
                                            } else {
                                                EmberTTS.speak("__MEDIA_PAUSE__" + title + "|" + artist);
                                            }
                                        };
                                        
                                        var sendProgress = function() {
                                            if (media && !media.paused) {
                                                EmberTTS.speak("__MEDIA_PROGRESS__" + (media.currentTime * 1000) + "|" + (media.duration * 1000));
                                            }
                                        };
                                        
                                        media.onplay = updateMedia;
                                        media.onpause = updateMedia;
                                        setTimeout(updateMedia, 2000);
                                        setInterval(sendProgress, 1000);
                                        setInterval(function() { if (!media.paused) updateMedia(); }, 10000);
                                    }
                                })();
                            """.trimIndent(), null)
                        }
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
                }
                onWebViewCreated(this)
                loadUrl(url)
            }
        },
        update = { webView -> 
            webView.settings.userAgentString = if (isPcMode) desktopUA else androidUA
            webView.settings.useWideViewPort = isPcMode
            webView.settings.loadWithOverviewMode = isPcMode

            if (webView.url != url && url != "home") {
                webView.loadUrl(url) 
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
