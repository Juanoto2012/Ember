package com.jntx.emberbrowser

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.net.http.SslCertificate
import android.os.Bundle
import android.os.Environment
import android.speech.tts.TextToSpeech
import android.webkit.*
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.lifecycleScope
import com.jntx.emberbrowser.ui.theme.EmberTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.*

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        val database = AppDatabase.getDatabase(this)
        setContent {
            EmberTheme {
                BrowserApp(database, ::speak)
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.getDefault()
        }
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

class TtsInterface(private val onSpeak: (String) -> Unit) {
    @JavascriptInterface
    fun speak(text: String) {
        onSpeak(text)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BrowserApp(database: AppDatabase, onSpeak: (String) -> Unit) {
    var tabs by remember { mutableStateOf(listOf(TabItem())) }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var showTabManager by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showDownloads by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showSecurityInfo by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("ember_prefs", Context.MODE_PRIVATE) }
    
    var customHomepageUrl by remember { 
        mutableStateOf(sharedPrefs.getString("homepage_url", "") ?: "") 
    }
    var downloadUrl by remember { mutableStateOf("") }
    var showDownloadDialog by remember { mutableStateOf(false) }
    
    val currentTab = tabs.getOrNull(selectedTabIndex) ?: TabItem()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var webView: WebView? by remember { mutableStateOf(null) }
    
    var addressBarText by remember { mutableStateOf("") }
    var addressSuggestions by remember { mutableStateOf(listOf<String>()) }
    var showAddressSuggestions by remember { mutableStateOf(false) }
    val client = remember { OkHttpClient() }

    var loadingProgress by remember { mutableIntStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }

    var suggestionJob: Job? = null

    fun fetchAddressSuggestions(query: String) {
        suggestionJob?.cancel()
        if (query.length < 2) {
            addressSuggestions = emptyList()
            showAddressSuggestions = false
            return
        }
        
        suggestionJob = scope.launch(Dispatchers.IO) {
            delay(300)
            try {
                val url = "https://search.brave.com/api/suggest?q=$query"
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: ""
                val jsonArray = JSONArray(body)
                val jsonSuggestions = jsonArray.getJSONArray(1)
                val suggestionList = mutableListOf<String>()
                for (i in 0 until jsonSuggestions.length()) {
                    suggestionList.add(jsonSuggestions.getString(i))
                }
                withContext(Dispatchers.Main) {
                    addressSuggestions = suggestionList
                    showAddressSuggestions = suggestionList.isNotEmpty()
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
    
    LaunchedEffect(currentTab.url) {
        if (currentTab.url != "home") {
            addressBarText = currentTab.url
        } else {
            addressBarText = ""
        }
        showAddressSuggestions = false
    }

    BackHandler(enabled = !showTabManager && !showHistory && !showBookmarks && !showDownloads && !showSettings && !showSecurityInfo) {
        if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else if (currentTab.url != "home") {
            tabs = tabs.toMutableList().apply {
                this[selectedTabIndex] = this[selectedTabIndex].copy(url = "home")
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION
        ))
    }

    Scaffold(
        topBar = {
            if (!showTabManager && currentTab.url != "home") {
                Column {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = { showSecurityInfo = true }) {
                                val isHttps = currentTab.url.startsWith("https")
                                Icon(
                                    imageVector = if (isHttps) Icons.Default.Shield else Icons.Default.GppBad,
                                    contentDescription = "Security",
                                    tint = if (isHttps) MaterialTheme.colorScheme.primary else Color.Red
                                )
                            }
                        },
                        title = {
                            Box {
                                OutlinedTextField(
                                    value = addressBarText,
                                    onValueChange = { 
                                        addressBarText = it
                                        fetchAddressSuggestions(it)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    shape = RoundedCornerShape(24.dp),
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium,
                                    placeholder = { Text("Search or type URL", fontSize = 14.sp) },
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Uri,
                                        imeAction = ImeAction.Go
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onGo = {
                                            val query = addressBarText
                                            val finalUrl = if (query.startsWith("http")) query 
                                                          else if (query.contains(".") && !query.contains(" ")) "https://$query"
                                                          else "https://www.google.com/search?q=$query"
                                            
                                            tabs = tabs.toMutableList().apply {
                                                this[selectedTabIndex] = this[selectedTabIndex].copy(url = finalUrl)
                                            }
                                            focusManager.clearFocus()
                                            showAddressSuggestions = false
                                        }
                                    ),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent
                                    )
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = { webView?.reload() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Reload")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.shadow(2.dp)
                    )
                    
                    AnimatedVisibility(
                        visible = loadingProgress > 0 && loadingProgress < 100,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        LinearProgressIndicator(
                            progress = { loadingProgress / 100f },
                            modifier = Modifier.fillMaxWidth().height(3.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.Transparent
                        )
                    }
                    
                    if (showAddressSuggestions) {
                        Popup(
                            onDismissRequest = { showAddressSuggestions = false },
                            properties = PopupProperties(focusable = false)
                        ) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .shadow(8.dp, RoundedCornerShape(12.dp)),
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                                    items(addressSuggestions) { suggestion ->
                                        ListItem(
                                            headlineContent = { Text(suggestion) },
                                            modifier = Modifier.clickable {
                                                addressBarText = suggestion
                                                val finalUrl = if (suggestion.startsWith("http")) suggestion 
                                                              else "https://www.google.com/search?q=$suggestion"
                                                tabs = tabs.toMutableList().apply {
                                                    this[selectedTabIndex] = this[selectedTabIndex].copy(url = finalUrl)
                                                }
                                                focusManager.clearFocus()
                                                showAddressSuggestions = false
                                            },
                                            leadingContent = { Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp), tint = Color.Gray) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (!showTabManager) {
                Surface(
                    tonalElevation = 2.dp,
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .height(56.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { webView?.goBack() }) { 
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(24.dp)) 
                        }
                        IconButton(onClick = { webView?.goForward() }) { 
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(24.dp)) 
                        }
                        IconButton(onClick = { 
                            tabs = tabs.toMutableList().apply {
                                this[selectedTabIndex] = this[selectedTabIndex].copy(url = "home")
                            }
                        }) { Icon(Icons.Outlined.Home, null) }
                        
                        IconButton(onClick = { showTabManager = true }) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.Layers, null)
                                Text(
                                    text = tabs.size.toString(),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                        IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Menu, null) }
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            if (currentTab.url == "home") {
                if (customHomepageUrl.isEmpty()) {
                    HomeScreen(
                        onSearch = { query: String ->
                            val finalUrl = if (query.startsWith("http")) query 
                                          else if (query.contains(".") && !query.contains(" ")) "https://$query"
                                          else "https://www.google.com/search?q=$query"
                            tabs = tabs.toMutableList().apply {
                                this[selectedTabIndex] = this[selectedTabIndex].copy(url = finalUrl)
                            }
                        },
                        onConfigureHomepage = { 
                            customHomepageUrl = it
                            sharedPrefs.edit().putString("homepage_url", it).apply()
                        }
                    )
                } else {
                    BrowserWebViewContainer(
                        url = customHomepageUrl,
                        onPageFinished = { newUrl, title ->
                             tabs = tabs.toMutableList().apply {
                                this[selectedTabIndex] = this[selectedTabIndex].copy(url = newUrl, title = title ?: newUrl)
                            }
                            isRefreshing = false
                        },
                        onDownloadRequested = { url ->
                            downloadUrl = url
                            showDownloadDialog = true
                        },
                        onWebViewCreated = { webView = it },
                        onSpeak = onSpeak,
                        onProgressChanged = { loadingProgress = it },
                        isRefreshing = isRefreshing,
                        onRefresh = { 
                            isRefreshing = true
                            webView?.reload() 
                        }
                    )
                }
            } else {
                BrowserWebViewContainer(
                    url = currentTab.url,
                    onPageFinished = { newUrl, title ->
                        tabs = tabs.toMutableList().apply {
                            this[selectedTabIndex] = this[selectedTabIndex].copy(url = newUrl, title = title ?: newUrl)
                        }
                        scope.launch {
                            database.browserDao().insertHistory(HistoryItem(url = newUrl, title = title ?: newUrl))
                        }
                        isRefreshing = false
                    },
                    onDownloadRequested = { url ->
                        downloadUrl = url
                        showDownloadDialog = true
                    },
                    onWebViewCreated = { webView = it },
                    onSpeak = onSpeak,
                    onProgressChanged = { loadingProgress = it },
                    isRefreshing = isRefreshing,
                    onRefresh = { 
                        isRefreshing = true
                        webView?.reload() 
                    }
                )
            }

            if (showSecurityInfo) {
                SecurityDialog(
                    url = currentTab.url,
                    certificate = webView?.certificate,
                    onDismiss = { showSecurityInfo = false },
                    onClearSiteData = {
                        val uri = Uri.parse(currentTab.url)
                        val origin = "${uri.scheme}://${uri.host}"
                        WebStorage.getInstance().deleteOrigin(origin)
                        CookieManager.getInstance().setCookie(origin, "")
                        webView?.reload()
                        showSecurityInfo = false
                        Toast.makeText(context, "Site data cleared", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            if (showDownloadDialog) {
                DownloadConfirmDialog(
                    url = downloadUrl,
                    onConfirm = { fileName, path ->
                        startDownload(context, downloadUrl, fileName, path, database)
                        showDownloadDialog = false
                    },
                    onDismiss = { showDownloadDialog = false }
                )
            }

            if (showTabManager) {
                TabManagerView(
                    tabs = tabs,
                    selectedIndex = selectedTabIndex,
                    onTabSelected = { index: Int -> selectedTabIndex = index; showTabManager = false },
                    onTabClosed = { index: Int ->
                        if (tabs.size > 1) {
                            tabs = tabs.toMutableList().apply { removeAt(index) }
                            if (selectedTabIndex >= tabs.size) selectedTabIndex = tabs.size - 1
                        }
                    },
                    onNewTab = {
                        tabs = tabs + TabItem(url = "home")
                        selectedTabIndex = tabs.size - 1
                        showTabManager = false
                    },
                    onClose = { showTabManager = false }
                )
            }

            if (showHistory) {
                var historyItems by remember { mutableStateOf(listOf<HistoryItem>()) }
                LaunchedEffect(Unit) { historyItems = database.browserDao().getAllHistory() }
                HistoryDialog(
                    items = historyItems, 
                    onUrlClick = { url: String -> 
                        tabs = tabs.toMutableList().apply { this[selectedTabIndex] = this[selectedTabIndex].copy(url = url) }
                        showHistory = false 
                    }, 
                    onDismiss = { showHistory = false },
                    onClearHistory = {
                        scope.launch {
                            database.browserDao().clearHistory()
                            historyItems = emptyList()
                        }
                    }
                )
            }
            
            if (showBookmarks) {
                var bookmarkItems by remember { mutableStateOf(listOf<BookmarkItem>()) }
                LaunchedEffect(Unit) { bookmarkItems = database.browserDao().getAllBookmarks() }
                BookmarkDialog(items = bookmarkItems, onUrlClick = { url: String ->
                    tabs = tabs.toMutableList().apply { this[selectedTabIndex] = this[selectedTabIndex].copy(url = url) }
                    showBookmarks = false
                }, onDismiss = { showBookmarks = false }, onAddBookmark = {
                    scope.launch {
                        database.browserDao().insertBookmark(BookmarkItem(url = currentTab.url, title = currentTab.title))
                        bookmarkItems = database.browserDao().getAllBookmarks()
                    }
                }, onDeleteBookmark = { id ->
                    scope.launch {
                        database.browserDao().deleteBookmark(id)
                        bookmarkItems = database.browserDao().getAllBookmarks()
                    }
                })
            }

            if (showDownloads) {
                var downloadItems by remember { mutableStateOf(listOf<DownloadItem>()) }
                LaunchedEffect(Unit) { downloadItems = database.browserDao().getAllDownloads() }
                DownloadManagerDialog(items = downloadItems, onDismiss = { showDownloads = false })
            }

            if (showSettings) {
                SettingsMenu(
                    onDismiss = { showSettings = false },
                    onHistory = { showHistory = true; showSettings = false },
                    onDownloads = { showDownloads = true; showSettings = false },
                    onBookmarks = { showBookmarks = true; showSettings = false },
                    onResetHomepage = { 
                        customHomepageUrl = ""
                        sharedPrefs.edit().remove("homepage_url").apply()
                    },
                    onClearData = {
                        WebStorage.getInstance().deleteAllData()
                        CookieManager.getInstance().removeAllCookies(null)
                        CookieManager.getInstance().flush()
                        webView?.clearCache(true)
                        webView?.clearFormData()
                        webView?.clearHistory()
                        Toast.makeText(context, "Cookies and Site Data cleared", Toast.LENGTH_SHORT).show()
                    },
                    onReadPage = {
                        webView?.evaluateJavascript(
                            "(function() { return document.body.innerText; })();"
                        ) { text ->
                            val cleanText = text?.trim()?.removeSurrounding("\"")?.replace("\\n", "\n") ?: ""
                            if (cleanText.isNotEmpty()) {
                                onSpeak(cleanText)
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun SecurityDialog(url: String, certificate: SslCertificate?, onDismiss: () -> Unit, onClearSiteData: () -> Unit) {
    val isHttps = url.startsWith("https")
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(if (isHttps) Icons.Default.Shield else Icons.Default.GppBad, null, tint = if (isHttps) MaterialTheme.colorScheme.primary else Color.Red) },
        title = { Text(if (isHttps) "Sitio Seguro" else "Sitio no seguro") },
        text = {
            Column {
                Text("URL: $url", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                if (isHttps && certificate != null) {
                    Text("Certificado válido", fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                    Text("Emitido para: ${certificate.issuedTo.cName}", style = MaterialTheme.typography.bodySmall)
                    Text("Emitido por: ${certificate.issuedBy.oName}", style = MaterialTheme.typography.bodySmall)
                } else if (isHttps) {
                    Text("Conexión segura establecida.", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("La conexión con este sitio no es privada.", color = Color.Red)
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onClearSiteData,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Borrar datos de este sitio")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

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
    onRefresh: () -> Unit
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
            onProgressChanged = onProgressChanged
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
    onProgressChanged: (Int) -> Unit
) {
    var errorInfo by remember { mutableStateOf<String?>(null) }
    
    if (errorInfo != null) {
        Column(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "x _ x", fontSize = 64.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text(
                text = "No se puede acceder a este sitio",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = errorInfo ?: "Error desconocido",
                fontSize = 14.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = { errorInfo = null }) {
                Text("Reintentar")
            }
        }
    }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.apply {
                    javaScriptEnabled = true; domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    javaScriptCanOpenWindowsAutomatically = true; allowFileAccess = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                    databaseEnabled = true
                }
                
                addJavascriptInterface(TtsInterface(onSpeak), "EmberTTS")
                
                setDownloadListener { downloadUrl, _, _, _, _ -> onDownloadRequested(downloadUrl) }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        if (errorInfo == null) {
                            url?.let { onPageFinished(it, view?.title) }
                            
                            view?.evaluateJavascript("""
                                (function() {
                                    if (!window.speechSynthesis) {
                                        window.speechSynthesis = {
                                            speak: function(utterance) {
                                                if (utterance && utterance.text) {
                                                    EmberTTS.speak(utterance.text);
                                                }
                                            },
                                            cancel: function() {},
                                            pause: function() {},
                                            resume: function() {},
                                            getVoices: function() { return []; }
                                        };
                                        window.SpeechSynthesisUtterance = function(text) {
                                            this.text = text;
                                        };
                                    } else {
                                        var oldSpeak = window.speechSynthesis.speak;
                                        window.speechSynthesis.speak = function(utterance) {
                                            if (utterance && utterance.text) {
                                                EmberTTS.speak(utterance.text);
                                            }
                                            oldSpeak.call(window.speechSynthesis, utterance);
                                        };
                                    }
                                })();
                            """.trimIndent(), null)
                        }
                    }

                    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                        if (request?.isForMainFrame == true) {
                            errorInfo = error?.description?.toString() ?: "Error de red"
                        }
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onPermissionRequest(request: PermissionRequest) { request.grant(request.resources) }
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        onProgressChanged(newProgress)
                    }
                }
                onWebViewCreated(this)
                loadUrl(url)
            }
        },
        update = { webView -> 
            if (errorInfo == null && webView.url != url && url != "home") {
                webView.loadUrl(url) 
            }
        },
        modifier = Modifier.fillMaxSize().then(if (errorInfo != null) Modifier.size(0.dp) else Modifier)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(onSearch: (String) -> Unit, onConfigureHomepage: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf(listOf<String>()) }
    var showConfigDialog by remember { mutableStateOf(false) }
    val client = remember { OkHttpClient() }
    val language = Locale.getDefault().language

    LaunchedEffect(query) {
        if (query.length > 1) {
            withContext(Dispatchers.IO) {
                try {
                    val url = "https://search.brave.com/api/suggest?q=$query"
                    val request = Request.Builder().url(url).build()
                    val response = client.newCall(request).execute()
                    val body = response.body?.string() ?: ""
                    val jsonArray = JSONArray(body)
                    val jsonSuggestions = jsonArray.getJSONArray(1)
                    val suggestionList = mutableListOf<String>()
                    for (i in 0 until jsonSuggestions.length()) {
                        suggestionList.add(jsonSuggestions.getString(i))
                    }
                    suggestions = suggestionList
                } catch (e: Exception) { e.printStackTrace() }
            }
        } else { suggestions = emptyList() }
    }

    if (showConfigDialog) {
        var urlInput by remember { mutableStateOf("https://") }
        AlertDialog(
            onDismissRequest = { showConfigDialog = false },
            title = { Text("Set Web Homepage") },
            text = { OutlinedTextField(value = urlInput, onValueChange = { urlInput = it }, label = { Text("URL") }) },
            confirmButton = { Button(onClick = { onConfigureHomepage(urlInput); showConfigDialog = false }) { Text("Set") } },
            dismissButton = { TextButton(onClick = { showConfigDialog = false }) { Text("Cancel") } }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        @OptIn(ExperimentalMaterial3Api::class)
        Text(text = "Ember", fontSize = 48.sp, fontWeight = FontWeight.Light, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(40.dp))
        Box(contentAlignment = Alignment.TopCenter) {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().shadow(4.dp, CircleShape),
                    placeholder = { Text("Search or type URL", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                    shape = CircleShape,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, null) }
                    }
                )
                if (suggestions.isNotEmpty()) {
                    Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(8.dp)) {
                        LazyColumn(modifier = Modifier.heightIn(max = 250.dp)) {
                            items(suggestions) { suggestion ->
                                ListItem(
                                    headlineContent = { Text(suggestion) },
                                    modifier = Modifier.clickable { query = suggestion; onSearch(suggestion) },
                                    leadingContent = { Icon(Icons.Default.Search, null, tint = Color.Gray, modifier = Modifier.size(18.dp)) }
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(48.dp))

        FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, maxItemsInEachRow = 4) {
            SpeedDialItem(Icons.Default.Language, "Google") { onSearch("https://www.google.com") }
            SpeedDialItem(Icons.Default.VideoLibrary, "YouTube") { onSearch("https://www.youtube.com") }
            SpeedDialItem(Icons.Default.Public, "GitHub") { onSearch("https://www.github.com") }
            SpeedDialItem(Icons.Outlined.Edit, "Set Home") { showConfigDialog = true }
        }
    }
}

@Composable
fun TabManagerView(tabs: List<TabItem>, selectedIndex: Int, onTabSelected: (Int) -> Unit, onTabClosed: (Int) -> Unit, onNewTab: () -> Unit, onClose: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column {
            Row(Modifier.fillMaxWidth().padding(16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("Tabs", style = MaterialTheme.typography.headlineMedium)
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, null) }
            }
            LazyVerticalGrid(columns = GridCells.Fixed(2), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.weight(1f)) {
                itemsIndexed(tabs) { index, tab ->
                    Card(modifier = Modifier.height(180.dp).clickable { onTabSelected(index) }, border = if (index == selectedIndex) CardDefaults.outlinedCardBorder() else null, shape = RoundedCornerShape(12.dp)) {
                        Box(Modifier.padding(12.dp)) {
                            Column {
                                Text(tab.title, maxLines = 2, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(tab.url, fontSize = 10.sp, maxLines = 1, color = Color.Gray)
                            }
                            IconButton(onClick = { onTabClosed(index) }, modifier = Modifier.align(Alignment.TopEnd).size(24.dp).background(Color.Black.copy(0.1f), CircleShape)) {
                                Icon(Icons.Default.Close, null, Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
            Button(onClick = onNewTab, modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("New Tab")
            }
        }
    }
}

@Composable
fun SettingsMenu(onDismiss: () -> Unit, onHistory: () -> Unit, onDownloads: () -> Unit, onBookmarks: () -> Unit, onResetHomepage: () -> Unit, onClearData: () -> Unit, onReadPage: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.4f)).clickable { onDismiss() }) {
        Surface(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(), shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(24.dp).navigationBarsPadding()) {
                Text(text = "Ember Menu", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    MenuActionItem(Icons.Outlined.History, "History") { onHistory() }
                    MenuActionItem(Icons.Outlined.Download, "Downloads") { onDownloads() }
                    MenuActionItem(Icons.Outlined.StarOutline, "Bookmarks") { onBookmarks() }
                    MenuActionItem(Icons.Outlined.RecordVoiceOver, "Read Page") { onReadPage(); onDismiss() }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                ListItem(
                    headlineContent = { Text("Clear Browsing Data") }, 
                    leadingContent = { Icon(Icons.Default.DeleteOutline, null) }, 
                    modifier = Modifier.clickable { onClearData(); onDismiss() },
                    supportingContent = { Text("Cookies, cache and site data") }
                )
                ListItem(
                    headlineContent = { Text("Reset Home") },
                    leadingContent = { Icon(Icons.Outlined.Refresh, null) },
                    modifier = Modifier.clickable { onResetHomepage(); onDismiss() }
                )
                ListItem(headlineContent = { Text("Exit") }, leadingContent = { Icon(Icons.AutoMirrored.Filled.ExitToApp, null) }, modifier = Modifier.clickable { /* Exit */ })
            }
        }
    }
}

@Composable
fun MenuActionItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
        Icon(icon, null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
        Text(label, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun BookmarkDialog(items: List<BookmarkItem>, onUrlClick: (String) -> Unit, onDismiss: () -> Unit, onAddBookmark: () -> Unit, onDeleteBookmark: (Long) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) { Text("Bookmarks"); IconButton(onClick = onAddBookmark) { Icon(Icons.Default.AddCircleOutline, null) } } },
        text = { 
            if (items.isEmpty()) {
                Text("No bookmarks yet", modifier = Modifier.fillMaxWidth().padding(16.dp), textAlign = TextAlign.Center)
            } else {
                LazyColumn(Modifier.height(400.dp)) { 
                    items(items) { item -> 
                        ListItem(
                            headlineContent = { Text(item.title) }, 
                            supportingContent = { Text(item.url) }, 
                            modifier = Modifier.clickable { onUrlClick(item.url) },
                            trailingContent = {
                                IconButton(onClick = { onDeleteBookmark(item.id) }) {
                                    Icon(Icons.Default.Delete, null, tint = Color.Gray)
                                }
                            }
                        ) 
                    } 
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
fun HistoryDialog(items: List<HistoryItem>, onUrlClick: (String) -> Unit, onDismiss: () -> Unit, onClearHistory: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("History")
                TextButton(onClick = onClearHistory) { Text("Clear All", color = MaterialTheme.colorScheme.error) }
            }
        },
        text = { 
            if (items.isEmpty()) {
                Text("No history yet", modifier = Modifier.fillMaxWidth().padding(16.dp), textAlign = TextAlign.Center)
            } else {
                LazyColumn(Modifier.height(400.dp)) { items(items) { item -> ListItem(headlineContent = { Text(item.title, maxLines = 1) }, supportingContent = { Text(item.url, maxLines = 1) }, modifier = Modifier.clickable { onUrlClick(item.url) }) } } 
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
fun DownloadConfirmDialog(url: String, onConfirm: (String, String) -> Unit, onDismiss: () -> Unit) {
    var fileName by remember { mutableStateOf(url.substringAfterLast("/")) }
    var path by remember { mutableStateOf(Environment.DIRECTORY_DOWNLOADS) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download File") },
        text = { Column { OutlinedTextField(value = fileName, onValueChange = { fileName = it }, label = { Text("File Name") }); Spacer(Modifier.height(8.dp)); OutlinedTextField(value = path, onValueChange = { path = it }, label = { Text("Download Path") }) } },
        confirmButton = { Button(onClick = { onConfirm(fileName, path) }) { Text("Download") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun DownloadManagerDialog(items: List<DownloadItem>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Downloads") },
        text = { LazyColumn(Modifier.height(400.dp)) { items(items) { item -> ListItem(headlineContent = { Text(item.fileName) }, supportingContent = { Text("${item.status} - ${item.filePath}") }) }; if (items.isEmpty()) { item { Text("No downloads yet", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) } } } },
        confirmButton = { TextButton(onClick = { onDismiss() }) { Text("Close") } }
    )
}

@Composable
fun SpeedDialItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(12.dp)) {
        Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).clickable { onClick() }, contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        }
        Text(text = label, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.onBackground)
    }
}

fun startDownload(context: Context, url: String, fileName: String, path: String, database: AppDatabase) {
    try {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(fileName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.enqueue(request)
        
        (context as? MainActivity)?.lifecycleScope?.launch {
            database.browserDao().insertDownload(DownloadItem(url = url, fileName = fileName, filePath = path, totalSize = 0))
        }
        
        Toast.makeText(context, "Download started", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
