package com.jntx.emberbrowser.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.print.PrintManager
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
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
    onGetVoices: () -> String,
    onMediaStatus: (String, String?, Boolean) -> Unit,
    onWebViewCreated: (WebView) -> Unit
) {
    var tabs by remember { mutableStateOf(listOf(TabItem())) }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var showTabManager by remember { mutableStateOf(false) }
    
    var currentScreen by remember { mutableStateOf<BrowserScreen>(BrowserScreen.Browser) }
    var showMenu by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("ember_prefs", Context.MODE_PRIVATE) }
    val clipboardManager = LocalClipboardManager.current
    
    var customHomepageUrl by remember { mutableStateOf(sharedPrefs.getString("homepage_url", "") ?: "") }
    var downloadPath by remember { mutableStateOf(sharedPrefs.getString("download_path", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath) ?: "") }
    var enhancedProtection by remember { mutableStateOf(sharedPrefs.getBoolean("enhanced_protection", true)) }
    var searchEngine by remember { mutableStateOf(sharedPrefs.getString("search_engine", "Google") ?: "Google") }
    var isAdBlockerEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("ad_blocker_enabled", true)) }
    var isPcMode by remember { mutableStateOf(false) }
    
    var downloadUrl by remember { mutableStateOf("") }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var permissionRequest by remember { mutableStateOf<Pair<PermissionRequest, String>?>(null) }
    var geolocationRequest by remember { mutableStateOf<Pair<String, GeolocationPermissions.Callback>?>(null) }
    var contextMenuInfo by remember { mutableStateOf<Pair<String, String>?>(null) } 
    var showSecurityInfo by remember { mutableStateOf(false) }
    
    val currentTab = tabs.getOrNull(selectedTabIndex) ?: TabItem()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var webView: WebView? by remember { mutableStateOf(null) }
    
    var addressBarText by remember { mutableStateOf("") }
    var addressSuggestions by remember { mutableStateOf(listOf<SuggestionItem>()) }
    var showAddressSuggestions by remember { mutableStateOf(false) }
    val client = remember { OkHttpClient() }
    var suggestionJob: Job? by remember { mutableStateOf(null) }

    var loadingProgress by remember { mutableIntStateOf(0) }

    fun getSearchUrl(query: String): String {
        return when (searchEngine) {
            "Brave" -> "https://search.brave.com/search?q=$query"
            "DuckDuckGo" -> "https://duckduckgo.com/?q=$query"
            "Bing" -> "https://www.bing.com/search?q=$query"
            else -> "https://www.google.com/search?q=$query"
        }
    }

    fun fetchSuggestions(query: String) {
        suggestionJob?.cancel()
        if (query.length < 2) {
            addressSuggestions = emptyList()
            showAddressSuggestions = false
            return
        }
        
        suggestionJob = scope.launch(Dispatchers.IO) {
            delay(200)
            val suggestions = mutableListOf<SuggestionItem>()
            
            val historyMatches = database.browserDao().searchHistory("%$query%")
            historyMatches.take(3).forEach {
                suggestions.add(SuggestionItem(it.title, it.url, SuggestionType.HISTORY))
            }
            
            try {
                val url = "https://search.brave.com/api/suggest?q=$query"
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: ""
                val jsonArray = JSONArray(body)
                val jsonSuggestions = jsonArray.getJSONArray(1)
                for (i in 0 until jsonSuggestions.length()) {
                    val s = jsonSuggestions.getString(i)
                    if (suggestions.none { it.title == s || it.url == s }) {
                        suggestions.add(SuggestionItem(s, getSearchUrl(s), SuggestionType.SEARCH))
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
            
            withContext(Dispatchers.Main) {
                addressSuggestions = suggestions
                showAddressSuggestions = suggestions.isNotEmpty()
            }
        }
    }
    
    LaunchedEffect(currentTab.url) {
        addressBarText = if (currentTab.url != "home") currentTab.url else ""
        showAddressSuggestions = false
    }

    BackHandler(enabled = currentScreen != BrowserScreen.Browser || showTabManager || permissionRequest != null || geolocationRequest != null || contextMenuInfo != null || showSecurityInfo || showMenu || (webView?.canGoBack() == true)) {
        when {
            showMenu -> showMenu = false
            showTabManager -> showTabManager = false
            showSecurityInfo -> showSecurityInfo = false
            permissionRequest != null -> { permissionRequest?.first?.deny(); permissionRequest = null }
            geolocationRequest != null -> { geolocationRequest?.second?.invoke(geolocationRequest?.first, false, false); geolocationRequest = null }
            contextMenuInfo != null -> contextMenuInfo = null
            currentScreen != BrowserScreen.Browser -> currentScreen = BrowserScreen.Browser
            webView?.canGoBack() == true -> webView?.goBack()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (currentScreen) {
            BrowserScreen.Browser -> {
                Scaffold(
                    topBar = {
                        if (!showTabManager && currentTab.url != "home") {
                            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
                                Column {
                                    TopAppBar(
                                        title = {
                                            TextField(
                                                value = addressBarText,
                                                onValueChange = { addressBarText = it; fetchSuggestions(it) },
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(52.dp),
                                                shape = RoundedCornerShape(26.dp),
                                                singleLine = true,
                                                textStyle = MaterialTheme.typography.bodyMedium,
                                                placeholder = { Text("Busca o escribe una URL", fontSize = 14.sp) },
                                                leadingIcon = {
                                                    val isHttps = currentTab.url.startsWith("https")
                                                    Icon(
                                                        imageVector = if (isHttps) Icons.Default.Shield else Icons.Default.GppBad,
                                                        contentDescription = "Security",
                                                        tint = if (isHttps) MaterialTheme.colorScheme.primary else Color.Red,
                                                        modifier = Modifier.size(20.dp).clickable { showSecurityInfo = true }
                                                    )
                                                },
                                                trailingIcon = { if (addressBarText.isNotEmpty()) IconButton(onClick = { addressBarText = "" }) { Icon(Icons.Default.Close, "Clear", modifier = Modifier.size(20.dp)) } },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                                                keyboardActions = KeyboardActions(onGo = {
                                                    val finalUrl = if (addressBarText.startsWith("http") || addressBarText.startsWith("file://")) addressBarText 
                                                                  else if (addressBarText.contains(".") && !addressBarText.contains(" ")) "https://$addressBarText"
                                                                  else getSearchUrl(addressBarText)
                                                    tabs = tabs.toMutableList().apply { this[selectedTabIndex] = this[selectedTabIndex].copy(url = finalUrl) }
                                                    focusManager.clearFocus()
                                                    showAddressSuggestions = false
                                                }),
                                                colors = TextFieldDefaults.colors(
                                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                    focusedIndicatorColor = Color.Transparent,
                                                    unfocusedIndicatorColor = Color.Transparent
                                                )
                                            )
                                        },
                                        actions = { IconButton(onClick = { webView?.reload() }) { Icon(Icons.Default.Refresh, "Reload") } }
                                    )
                                    
                                    AnimatedVisibility(
                                        visible = loadingProgress > 0 && loadingProgress < 100,
                                        enter = fadeIn() + expandVertically(),
                                        exit = fadeOut() + shrinkVertically()
                                    ) {
                                        LinearProgressIndicator(
                                            progress = { loadingProgress / 100f },
                                            modifier = Modifier.fillMaxWidth().height(2.dp),
                                            color = MaterialTheme.colorScheme.primary,
                                            trackColor = Color.Transparent,
                                        )
                                    }

                                    if (showAddressSuggestions) {
                                        Popup(onDismissRequest = { showAddressSuggestions = false }, properties = PopupProperties(focusable = false)) {
                                            Surface(
                                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).shadow(8.dp),
                                                color = MaterialTheme.colorScheme.surface,
                                                shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
                                            ) {
                                                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                                                    items(addressSuggestions) { suggestion ->
                                                        ListItem(
                                                            headlineContent = { Text(suggestion.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                                            supportingContent = { if (suggestion.type == SuggestionType.HISTORY) Text(suggestion.url, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = Color.Gray) },
                                                            modifier = Modifier.clickable {
                                                                val finalUrl = if (suggestion.type == SuggestionType.HISTORY) suggestion.url else getSearchUrl(suggestion.title)
                                                                tabs = tabs.toMutableList().apply { this[selectedTabIndex] = this[selectedTabIndex].copy(url = finalUrl) }
                                                                focusManager.clearFocus()
                                                                showAddressSuggestions = false
                                                            },
                                                            leadingContent = { 
                                                                Icon(
                                                                    imageVector = if (suggestion.type == SuggestionType.HISTORY) Icons.Default.History else Icons.Default.Search, 
                                                                    null, 
                                                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), 
                                                                    modifier = Modifier.size(20.dp)
                                                                ) 
                                                            }
                                                        )
                                                    }
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
                            BottomAppBar(containerColor = MaterialTheme.colorScheme.surface, contentPadding = PaddingValues(horizontal = 4.dp), modifier = Modifier.height(56.dp).shadow(8.dp) ) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { if (webView?.canGoBack() == true) webView?.goBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                                    IconButton(onClick = { if (webView?.canGoForward() == true) webView?.goForward() }) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Forward") }
                                    
                                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.Menu, "Menu") }
                                    
                                    IconButton(onClick = { showTabManager = true }) {
                                        Box(modifier = Modifier.size(22.dp).border(1.8.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                                            Text(text = tabs.size.toString(), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    IconButton(onClick = { tabs = tabs.toMutableList().apply { this[selectedTabIndex] = this[selectedTabIndex].copy(url = "home") } }) { Icon(Icons.Default.Home, "Home") }
                                }
                            }
                        }
                    }
                ) { paddingValues ->
                    Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
                        if (currentTab.url == "home") {
                            HomeScreen(onSearch = { query ->
                                val finalUrl = if (query.startsWith("http") || query.startsWith("file://")) query 
                                              else if (query.contains(".") && !query.contains(" ")) "https://$query"
                                              else getSearchUrl(query)
                                tabs = tabs.toMutableList().apply { this[selectedTabIndex] = this[selectedTabIndex].copy(url = finalUrl) }
                            }, onConfigureHomepage = { url -> customHomepageUrl = url; sharedPrefs.edit { putString("homepage_url", url) } })
                        } else {
                            BrowserWebViewContainer(
                                url = currentTab.url,
                                onPageFinished = { url, title ->
                                    tabs = tabs.toMutableList().apply { this[selectedTabIndex] = this[selectedTabIndex].copy(url = url, title = title ?: url) }
                                    scope.launch { database.browserDao().insertHistory(HistoryItem(url = url, title = title ?: url)) }
                                },
                                onDownloadRequested = { url -> downloadUrl = url; showDownloadDialog = true },
                                onWebViewCreated = { webView = it; onWebViewCreated(it) },
                                onSpeak = onSpeak,
                                onGetVoices = onGetVoices,
                                onProgressChanged = { loadingProgress = it },
                                isRefreshing = false,
                                onRefresh = { webView?.reload() },
                                onFaviconChanged = { bitmap -> tabs = tabs.toMutableList().apply { this[selectedTabIndex] = this[selectedTabIndex].copy(favicon = bitmap) } },
                                onPermissionRequested = { request, url -> permissionRequest = request to url },
                                onGeolocationRequested = { origin, callback -> geolocationRequest = origin to callback },
                                onImageLongClick = { url -> contextMenuInfo = "Imagen" to url },
                                enhancedProtection = enhancedProtection,
                                onMediaStatus = onMediaStatus,
                                isAdBlockerEnabled = isAdBlockerEnabled,
                                isPcMode = isPcMode
                            )
                        }
                    }
                }
            }
            BrowserScreen.History -> {
                val historyItems by database.browserDao().getAllHistory().collectAsState(initial = emptyList())
                HistoryScreen(items = historyItems, onUrlClick = { url: String -> tabs = tabs.toMutableList().apply { this[selectedTabIndex] = this[selectedTabIndex].copy(url = url) }; currentScreen = BrowserScreen.Browser }, onBack = { currentScreen = BrowserScreen.Browser }, onClearHistory = { scope.launch { database.browserDao().clearHistory() } })
            }
            BrowserScreen.Bookmarks -> {
                val bookmarkItems by database.browserDao().getAllBookmarks().collectAsState(initial = emptyList())
                BookmarkScreen(items = bookmarkItems, onUrlClick = { url: String -> tabs = tabs.toMutableList().apply { this[selectedTabIndex] = this[selectedTabIndex].copy(url = url) }; currentScreen = BrowserScreen.Browser }, onBack = { currentScreen = BrowserScreen.Browser }, onAddBookmark = { scope.launch { database.browserDao().insertBookmark(BookmarkItem(url = currentTab.url, title = currentTab.title, favicon = currentTab.favicon?.toByteArray())) } }, onDeleteBookmark = { id: Long -> scope.launch { database.browserDao().deleteBookmark(id) } })
            }
            BrowserScreen.Downloads -> {
                val downloadItems by database.browserDao().getAllDownloads().collectAsState(initial = emptyList())
                DownloadScreen(items = downloadItems, onBack = { currentScreen = BrowserScreen.Browser }, onDelete = { id -> scope.launch { database.browserDao().deleteDownload(id) } })
            }
            BrowserScreen.Settings -> {
                SettingsScreen(
                    enhancedProtection = enhancedProtection,
                    onEnhancedProtectionChange = { enhancedProtection = it; sharedPrefs.edit { putBoolean("enhanced_protection", it) } },
                    searchEngine = searchEngine,
                    onSearchEngineChange = { searchEngine = it; sharedPrefs.edit { putString("search_engine", it) } },
                    onResetHome = { customHomepageUrl = ""; sharedPrefs.edit { remove("homepage_url") } },
                    onClearData = { webView?.clearCache(true); webView?.clearHistory(); webView?.clearFormData(); CookieManager.getInstance().removeAllCookies(null); Toast.makeText(context, "Todos los datos borrados", Toast.LENGTH_SHORT).show() },
                    onBack = { currentScreen = BrowserScreen.Browser },
                    downloadPath = downloadPath,
                    onDownloadPathChange = { downloadPath = it; sharedPrefs.edit { putString("download_path", it) } },
                    onReadPage = { webView?.evaluateJavascript("(function() { return document.body.innerText; })();") { text -> onSpeak(text ?: "No se pudo leer el contenido") } },
                    isAdBlockerEnabled = isAdBlockerEnabled,
                    onAdBlockerChange = { isAdBlockerEnabled = it; sharedPrefs.edit { putBoolean("ad_blocker_enabled", it) } }
                )
            }
        }

        if (showTabManager) {
            TabManagerView(tabs = tabs, selectedIndex = selectedTabIndex, onTabSelected = { index -> selectedTabIndex = index; showTabManager = false }, onTabClosed = { index -> val newList = tabs.toMutableList().apply { removeAt(index) }; if (newList.isEmpty()) { tabs = listOf(TabItem()); selectedTabIndex = 0 } else { tabs = newList; if (selectedTabIndex >= tabs.size) selectedTabIndex = tabs.size - 1 } }, onNewTab = { tabs = tabs + TabItem(); selectedTabIndex = tabs.size - 1; showTabManager = false }, onClose = { showTabManager = false })
        }

        if (showMenu) {
            SettingsMenu(
                onDismiss = { showMenu = false },
                onHistory = { currentScreen = BrowserScreen.History; showMenu = false },
                onDownloads = { currentScreen = BrowserScreen.Downloads; showMenu = false },
                onBookmarks = { currentScreen = BrowserScreen.Bookmarks; showMenu = false },
                onOpenSettings = { currentScreen = BrowserScreen.Settings; showMenu = false },
                isPcMode = isPcMode,
                onPcModeChange = { isPcMode = it; webView?.reload(); showMenu = false },
                currentUrl = currentTab.url,
                onFindInPage = { /* Próximamente */ },
                onPrint = { 
                    webView?.let { view ->
                        (context as? Activity)?.let { activity ->
                            val printManager = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
                            val printAdapter = view.createPrintDocumentAdapter("Ember Document")
                            printManager.print("Ember Print", printAdapter, null)
                        }
                    }
                }
            )
        }

        if (showSecurityInfo) {
            SecurityDialog(url = currentTab.url, certificate = webView?.certificate, onDismiss = { showSecurityInfo = false }, onClearSiteData = {
                WebStorage.getInstance().deleteOrigin(currentTab.url.toUri().host ?: "")
                webView?.clearCache(true)
                showSecurityInfo = false
                Toast.makeText(context, "Datos del sitio borrados", Toast.LENGTH_SHORT).show()
            })
        }

        if (showDownloadDialog) {
            DownloadConfirmDialog(url = downloadUrl, defaultPath = downloadPath, onConfirm = { fileName, path -> startDownload(context, downloadUrl, fileName, path, database); showDownloadDialog = false }, onDismiss = { showDownloadDialog = false })
        }

        permissionRequest?.let { (request, origin) ->
            AlertDialog(
                onDismissRequest = { request.deny(); permissionRequest = null },
                title = { Text("Permiso solicitado") },
                text = { Text("El sitio $origin solicita acceso a: ${request.resources.joinToString(", ")}") },
                confirmButton = { Button(onClick = { request.grant(request.resources); permissionRequest = null }) { Text("Permitir") } },
                dismissButton = { TextButton(onClick = { request.deny(); permissionRequest = null }) { Text("Denegar") } }
            )
        }

        geolocationRequest?.let { (origin, callback) ->
            AlertDialog(
                onDismissRequest = { callback.invoke(origin, false, false); geolocationRequest = null },
                title = { Text("Ubicación") },
                text = { Text("¿Permitir que $origin acceda a tu ubicación?") },
                confirmButton = { Button(onClick = { callback.invoke(origin, true, true); geolocationRequest = null }) { Text("Permitir") } },
                dismissButton = { TextButton(onClick = { callback.invoke(origin, false, false); geolocationRequest = null }) { Text("Denegar") } }
            )
        }

        contextMenuInfo?.let { (label, url) ->
            CromiteContextMenu(title = label, url = url, onDismiss = { contextMenuInfo = null }, actions = listOf( { ContextAction("Abrir en pestaña nueva", Icons.Default.Tab) { tabs = tabs + TabItem(url = url); selectedTabIndex = tabs.size - 1; contextMenuInfo = null } }, { ContextAction("Copiar enlace", Icons.Default.ContentCopy) { clipboardManager.setText(AnnotatedString(url)); Toast.makeText(context, "Enlace copiado", Toast.LENGTH_SHORT).show(); contextMenuInfo = null } }, { ContextAction("Descargar", Icons.Default.Download) { downloadUrl = url; showDownloadDialog = true; contextMenuInfo = null } }, { ContextAction("Compartir", Icons.Default.Share) { val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, url) }; context.startActivity(Intent.createChooser(intent, "Compartir enlace")); contextMenuInfo = null } } ))
        }
    }
}

data class SuggestionItem(val title: String, val url: String, val type: SuggestionType)
enum class SuggestionType { SEARCH, HISTORY }
enum class BrowserScreen { Browser, History, Bookmarks, Downloads, Settings }
