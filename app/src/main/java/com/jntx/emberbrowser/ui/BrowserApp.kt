package com.jntx.emberbrowser.ui

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.edit
import androidx.core.net.toUri
import com.jntx.emberbrowser.*
import com.jntx.emberbrowser.utils.startDownload
import com.jntx.emberbrowser.utils.toByteArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserApp(
    database: AppDatabase, 
    onSpeak: (String) -> Unit, 
    onMediaStatus: (String, String?, Boolean) -> Unit,
    onWebViewCreated: (WebView) -> Unit
) {
    var tabs by remember { mutableStateOf(listOf(TabItem())) }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var showTabManager by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showDownloads by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showSecurityInfo by remember { mutableStateOf(false) }
    var showSettingsScreen by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("ember_prefs", Context.MODE_PRIVATE) }
    val clipboardManager = LocalClipboardManager.current
    
    var customHomepageUrl by remember { mutableStateOf(sharedPrefs.getString("homepage_url", "") ?: "") }
    var downloadPath by remember { mutableStateOf(sharedPrefs.getString("download_path", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath) ?: "") }
    var enhancedProtection by remember { mutableStateOf(sharedPrefs.getBoolean("enhanced_protection", true)) }
    var searchEngine by remember { mutableStateOf(sharedPrefs.getString("search_engine", "Google") ?: "Google") }
    var isAdBlockerEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("ad_blocker_enabled", true)) }
    
    var downloadUrl by remember { mutableStateOf("") }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var permissionRequest by remember { mutableStateOf<Pair<PermissionRequest, String>?>(null) }
    var contextMenuInfo by remember { mutableStateOf<Pair<String, String>?>(null) } 
    
    val currentTab = tabs.getOrNull(selectedTabIndex) ?: TabItem()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var webView: WebView? by remember { mutableStateOf(null) }
    
    var addressBarText by remember { mutableStateOf("") }
    var addressSuggestions by remember { mutableStateOf(listOf<String>()) }
    var showAddressSuggestions by remember { mutableStateOf(false) }
    val client = remember { OkHttpClient() }
    var suggestionJob: Job? by remember { mutableStateOf(null) }

    fun getSearchUrl(query: String): String {
        return when (searchEngine) {
            "Brave" -> "https://search.brave.com/search?q=$query"
            "DuckDuckGo" -> "https://duckduckgo.com/?q=$query"
            "Bing" -> "https://www.bing.com/search?q=$query"
            else -> "https://www.google.com/search?q=$query"
        }
    }

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
        addressBarText = if (currentTab.url != "home") currentTab.url else ""
        showAddressSuggestions = false
    }

    BackHandler(enabled = !showTabManager && !showHistory && !showBookmarks && !showDownloads && !showSettings && !showSecurityInfo && !showSettingsScreen && permissionRequest == null && contextMenuInfo == null) {
        if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else if (currentTab.url != "home") {
            tabs = tabs.toMutableList().apply {
                this[selectedTabIndex] = this[selectedTabIndex].copy(url = "home")
            }
        }
    }

    Scaffold(
        topBar = {
            if (!showTabManager && !showSettingsScreen && currentTab.url != "home") {
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
                                            val finalUrl = if (query.startsWith("http") || query.startsWith("file://")) query 
                                                          else if (query.contains(".") && !query.contains(" ")) "https://$query"
                                                          else getSearchUrl(query)
                                            
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
                    
                    if (showAddressSuggestions) {
                        Popup(
                            onDismissRequest = { showAddressSuggestions = false },
                            properties = PopupProperties(focusable = false)
                        ) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
                                shadowElevation = 8.dp
                            ) {
                                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                                    items(addressSuggestions) { suggestion ->
                                        ListItem(
                                            headlineContent = { Text(suggestion) },
                                            modifier = Modifier.clickable {
                                                addressBarText = suggestion
                                                val finalUrl = getSearchUrl(suggestion)
                                                tabs = tabs.toMutableList().apply {
                                                    this[selectedTabIndex] = this[selectedTabIndex].copy(url = finalUrl)
                                                }
                                                focusManager.clearFocus()
                                                showAddressSuggestions = false
                                            },
                                            leadingContent = { Icon(Icons.Default.Search, null, tint = Color.Gray, modifier = Modifier.size(18.dp)) }
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
            if (!showTabManager && !showSettingsScreen) {
                BottomAppBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    modifier = Modifier.height(56.dp).shadow(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { if (webView?.canGoBack() == true) webView?.goBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", modifier = Modifier.size(24.dp))
                        }
                        IconButton(onClick = { if (webView?.canGoForward() == true) webView?.goForward() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, "Forward", modifier = Modifier.size(24.dp))
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Menu, "Menu", modifier = Modifier.size(24.dp))
                        }
                        IconButton(onClick = { showTabManager = true }) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .border(1.8.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(4.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tabs.size.toString(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        IconButton(onClick = { 
                            tabs = tabs.toMutableList().apply {
                                this[selectedTabIndex] = this[selectedTabIndex].copy(url = "home")
                            }
                        }) {
                            Icon(Icons.Default.Home, "Home", modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            if (currentTab.url == "home") {
                HomeScreen(
                    onSearch = { query ->
                        val finalUrl = if (query.startsWith("http") || query.startsWith("file://")) query 
                                      else if (query.contains(".") && !query.contains(" ")) "https://$query"
                                      else getSearchUrl(query)
                        tabs = tabs.toMutableList().apply {
                            this[selectedTabIndex] = this[selectedTabIndex].copy(url = finalUrl)
                        }
                    },
                    onConfigureHomepage = { url ->
                        customHomepageUrl = url
                        sharedPrefs.edit { putString("homepage_url", url) }
                    }
                )
            } else {
                BrowserWebViewContainer(
                    url = currentTab.url,
                    onPageFinished = { url, title ->
                        tabs = tabs.toMutableList().apply {
                            this[selectedTabIndex] = this[selectedTabIndex].copy(url = url, title = title ?: url)
                        }
                        scope.launch {
                            database.browserDao().insertHistory(HistoryItem(url = url, title = title ?: url))
                        }
                    },
                    onDownloadRequested = { url ->
                        downloadUrl = url
                        showDownloadDialog = true
                    },
                    onWebViewCreated = { 
                        webView = it
                        onWebViewCreated(it)
                    },
                    onSpeak = onSpeak,
                    onProgressChanged = { /* handle progress */ },
                    isRefreshing = false,
                    onRefresh = { webView?.reload() },
                    onFaviconChanged = { bitmap ->
                        tabs = tabs.toMutableList().apply {
                            this[selectedTabIndex] = this[selectedTabIndex].copy(favicon = bitmap)
                        }
                    },
                    onPermissionRequested = { request, url ->
                        permissionRequest = request to url
                    },
                    onImageLongClick = { url ->
                        contextMenuInfo = "Imagen" to url
                    },
                    enhancedProtection = enhancedProtection,
                    onMediaStatus = onMediaStatus,
                    isAdBlockerEnabled = isAdBlockerEnabled
                )
            }

            if (showTabManager) {
                TabManagerView(
                    tabs = tabs,
                    selectedIndex = selectedTabIndex,
                    onTabSelected = { index -> selectedTabIndex = index; showTabManager = false },
                    onTabClosed = { index ->
                        val newList = tabs.toMutableList().apply { removeAt(index) }
                        if (newList.isEmpty()) {
                            tabs = listOf(TabItem())
                            selectedTabIndex = 0
                        } else {
                            tabs = newList
                            if (selectedTabIndex >= tabs.size) selectedTabIndex = tabs.size - 1
                        }
                    },
                    onNewTab = {
                        tabs = tabs + TabItem()
                        selectedTabIndex = tabs.size - 1
                        showTabManager = false
                    },
                    onClose = { showTabManager = false }
                )
            }

            if (showHistory) {
                val historyItems by database.browserDao().getAllHistory().collectAsState(initial = emptyList())
                HistoryDialog(
                    items = historyItems,
                    onUrlClick = { url ->
                        tabs = tabs.toMutableList().apply {
                            this[selectedTabIndex] = this[selectedTabIndex].copy(url = url)
                        }
                        showHistory = false
                        showSettings = false
                    },
                    onDismiss = { showHistory = false },
                    onClearHistory = { scope.launch { database.browserDao().clearHistory() } }
                )
            }

            if (showBookmarks) {
                val bookmarkItems by database.browserDao().getAllBookmarks().collectAsState(initial = emptyList())
                BookmarkDialog(
                    items = bookmarkItems,
                    onUrlClick = { url ->
                        tabs = tabs.toMutableList().apply {
                            this[selectedTabIndex] = this[selectedTabIndex].copy(url = url)
                        }
                        showBookmarks = false
                        showSettings = false
                    },
                    onDismiss = { showBookmarks = false },
                    onAddBookmark = {
                        scope.launch {
                            database.browserDao().insertBookmark(BookmarkItem(url = currentTab.url, title = currentTab.title, favicon = currentTab.favicon?.toByteArray()))
                        }
                    },
                    onDeleteBookmark = { id -> scope.launch { database.browserDao().deleteBookmark(id) } }
                )
            }

            if (showDownloads) {
                val downloadItems by database.browserDao().getAllDownloads().collectAsState(initial = emptyList())
                DownloadManagerDialog(items = downloadItems, onDismiss = { showDownloads = false })
            }

            if (showSettings) {
                SettingsMenu(
                    onDismiss = { showSettings = false },
                    onHistory = { showHistory = true },
                    onDownloads = { showDownloads = true },
                    onBookmarks = { showBookmarks = true },
                    onOpenSettings = { showSettingsScreen = true; showSettings = false }
                )
            }

            if (showSecurityInfo) {
                SecurityDialog(
                    url = currentTab.url,
                    certificate = webView?.certificate,
                    onDismiss = { showSecurityInfo = false },
                    onClearSiteData = {
                        WebStorage.getInstance().deleteOrigin(currentTab.url.toUri().host ?: "")
                        webView?.clearCache(true)
                        showSecurityInfo = false
                        Toast.makeText(context, "Datos del sitio borrados", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            if (showDownloadDialog) {
                DownloadConfirmDialog(
                    url = downloadUrl,
                    defaultPath = downloadPath,
                    onConfirm = { fileName, path ->
                        startDownload(context, downloadUrl, fileName, path, database)
                        showDownloadDialog = false
                    },
                    onDismiss = { showDownloadDialog = false }
                )
            }

            contextMenuInfo?.let { (label, url) ->
                CromiteContextMenu(
                    title = label,
                    url = url,
                    onDismiss = { contextMenuInfo = null },
                    actions = listOf(
                        { ContextAction("Abrir en pestaña nueva", Icons.Default.Tab) {
                            tabs = tabs + TabItem(url = url)
                            selectedTabIndex = tabs.size - 1
                            contextMenuInfo = null
                        } },
                        { ContextAction("Copiar enlace", Icons.Default.ContentCopy) {
                            clipboardManager.setText(AnnotatedString(url))
                            Toast.makeText(context, "Enlace copiado", Toast.LENGTH_SHORT).show()
                            contextMenuInfo = null
                        } },
                        { ContextAction("Descargar", Icons.Default.Download) {
                            downloadUrl = url
                            showDownloadDialog = true
                            contextMenuInfo = null
                        } },
                        { ContextAction("Compartir", Icons.Default.Share) {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, url)
                            }
                            context.startActivity(Intent.createChooser(intent, "Compartir enlace"))
                            contextMenuInfo = null
                        } }
                    )
                )
            }

            if (showSettingsScreen) {
                SettingsScreen(
                    enhancedProtection = enhancedProtection,
                    onEnhancedProtectionChange = {
                        enhancedProtection = it
                        sharedPrefs.edit { putBoolean("enhanced_protection", it) }
                    },
                    searchEngine = searchEngine,
                    onSearchEngineChange = {
                        searchEngine = it
                        sharedPrefs.edit { putString("search_engine", it) }
                    },
                    onResetHome = {
                        customHomepageUrl = ""
                        sharedPrefs.edit { remove("homepage_url") }
                    },
                    onClearData = {
                        webView?.clearCache(true)
                        webView?.clearHistory()
                        webView?.clearFormData()
                        CookieManager.getInstance().removeAllCookies(null)
                        Toast.makeText(context, "Todos los datos borrados", Toast.LENGTH_SHORT).show()
                    },
                    onBack = { showSettingsScreen = false },
                    downloadPath = downloadPath,
                    onDownloadPathChange = {
                        downloadPath = it
                        sharedPrefs.edit { putString("download_path", it) }
                    },
                    onReadPage = {
                        webView?.evaluateJavascript("(function() { return document.body.innerText; })();") { text ->
                            onSpeak(text ?: "No se pudo leer el contenido")
                        }
                    },
                    isAdBlockerEnabled = isAdBlockerEnabled,
                    onAdBlockerChange = {
                        isAdBlockerEnabled = it
                        sharedPrefs.edit { putBoolean("ad_blocker_enabled", it) }
                    }
                )
            }
        }
    }
}

@Composable
fun SettingsMenu(onDismiss: () -> Unit, onHistory: () -> Unit, onDownloads: () -> Unit, onBookmarks: () -> Unit, onOpenSettings: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.4f)).clickable { onDismiss() }) {
        Surface(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(), shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(24.dp).navigationBarsPadding()) {
                Text(text = "Ember Menu", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    MenuActionItem(Icons.Outlined.History, "History") { onHistory() }
                    MenuActionItem(Icons.Outlined.Download, "Downloads") { onDownloads() }
                    MenuActionItem(Icons.Outlined.StarOutline, "Bookmarks") { onBookmarks() }
                    MenuActionItem(Icons.Outlined.Settings, "Settings") { onOpenSettings() }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
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
