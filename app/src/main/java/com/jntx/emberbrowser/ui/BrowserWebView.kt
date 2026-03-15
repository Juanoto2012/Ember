package com.jntx.emberbrowser.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.View
import android.webkit.*
import androidx.activity.compose.BackHandler
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
    isPcMode: Boolean
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
                isPcMode = isPcMode,
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
    onMediaStatus: (String, String?, Boolean) -> Unit,
    isAdBlockerEnabled: Boolean,
    isPcMode: Boolean,
    onShowCustomView: (View, WebChromeClient.CustomViewCallback) -> Unit,
    onHideCustomView: () -> Unit
) {
    val androidUA = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
    val desktopUA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    javaScriptCanOpenWindowsAutomatically = true
                    allowFileAccess = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    
                    // Zoom inteligente global
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                    textZoom = 100
                    
                    setInitialScale(0) 
                    layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
                    
                    userAgentString = if (isPcMode) desktopUA else androidUA
                }
                
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                addJavascriptInterface(TtsInterface(onSpeak, onGetVoices), "EmberTTS")
                
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
                                    var viewport = document.querySelector('meta[name="viewport"]');
                                    var content = 'width=device-width, initial-scale=1.0, maximum-scale=10.0, user-scalable=yes';
                                    if (viewport) {
                                        viewport.content = content;
                                    } else {
                                        var meta = document.createElement('meta');
                                        meta.name = 'viewport';
                                        meta.content = content;
                                        document.getElementsByTagName('head')[0].appendChild(meta);
                                    }

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
                                            if (media && !media.paused && media.duration) {
                                                EmberTTS.speak("__MEDIA_PROGRESS__" + (media.currentTime * 1000) + "|" + (media.duration * 1000));
                                            }
                                        };
                                        
                                        media.onplay = updateMedia;
                                        media.onpause = updateMedia;
                                        media.onended = function() { EmberTTS.speak("__MEDIA_PAUSE__" + document.title + "|Ember Browser"); };
                                        
                                        setTimeout(updateMedia, 2000);
                                        setInterval(sendProgress, 1000);
                                        setInterval(function() { if (!media.paused) updateMedia(); }, 10000);
                                    }

                                    if (window.EmberTTS) {
                                        const ttsBridge = {
                                            speak: function(utterance) {
                                                if (utterance && utterance.text) {
                                                    const payload = {
                                                        text: utterance.text,
                                                        lang: utterance.lang,
                                                        pitch: utterance.pitch || 1.0,
                                                        rate: utterance.rate || 1.0,
                                                        voice: utterance.voice ? utterance.voice.name : ""
                                                    };
                                                    EmberTTS.speak("__TTS_SPEAK_JSON__" + JSON.stringify(payload));
                                                    if (utterance.onstart) utterance.onstart();
                                                    setTimeout(() => { if (utterance.onend) utterance.onend(); }, utterance.text.length * 80);
                                                }
                                            },
                                            cancel: function() { EmberTTS.speak("__TTS_CANCEL__"); },
                                            pause: function() { EmberTTS.speak("__TTS_PAUSE__"); },
                                            resume: function() { EmberTTS.speak("__TTS_RESUME__"); },
                                            getVoices: function() { 
                                                try {
                                                    let v = JSON.parse(EmberTTS.getVoices());
                                                    if (v && v.length > 0) return v;
                                                } catch(e) {}
                                                return [{ name: 'Ember Voice', lang: 'es-ES', default: true }];
                                            },
                                            onvoiceschanged: null,
                                            paused: false,
                                            pending: false,
                                            speaking: false
                                        };

                                        try {
                                            Object.defineProperty(window, 'speechSynthesis', {
                                                value: ttsBridge,
                                                configurable: true,
                                                enumerable: true,
                                                writable: true
                                            });
                                            
                                            if (typeof SpeechSynthesisUtterance === 'undefined') {
                                                window.SpeechSynthesisUtterance = function(text) { 
                                                    this.text = text;
                                                    this.onstart = null;
                                                    this.onend = null;
                                                };
                                            }
                                            
                                            if (window.speechSynthesis.onvoiceschanged) {
                                                window.speechSynthesis.onvoiceschanged();
                                            }
                                            window.dispatchEvent(new Event('voiceschanged'));
                                        } catch(e) { console.log('Error in TTS Bridge injection', e); }
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
                    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                        if (view != null && callback != null) {
                            onShowCustomView(view, callback)
                        }
                    }
                    override fun onHideCustomView() {
                        onHideCustomView()
                    }
                }
                onWebViewCreated(this)
                loadUrl(url)
            }
        },
        update = { webView -> 
            webView.settings.userAgentString = if (isPcMode) desktopUA else androidUA
            if (webView.url != url && url != "home") {
                webView.loadUrl(url) 
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
