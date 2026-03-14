package com.jntx.emberbrowser

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.net.http.SslCertificate
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.speech.tts.TextToSpeech
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.webkit.*
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.app.NotificationCompat
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
import java.io.ByteArrayOutputStream
import java.util.*

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var mediaSession: MediaSessionCompat? = null
    private var currentWebView: WebView? = null
    private var isPlayingMedia = false
    private var currentMediaTitle = "Ember Browser"
    private var currentMediaArtist = "Unknown Artist"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Habilitar cookies globales para persistencia como Chrome
        CookieManager.getInstance().setAcceptCookie(true)
        
        tts = TextToSpeech(this, this)
        setupMediaSession()
        createNotificationChannel()
        
        val database = AppDatabase.getDatabase(this)
        setContent {
            EmberTheme {
                BrowserApp(database, ::speak, ::updateMediaStatus, onWebViewCreated = { currentWebView = it })
            }
        }
    }

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "EmberMediaSession").apply {
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { togglePlayback() }
                override fun onPause() { togglePlayback() }
                override fun onSkipToNext() { skipNext() }
                override fun onSkipToPrevious() { skipPrevious() }
            })
            isActive = true
        }
    }

    private fun updateMediaStatus(title: String, artist: String?, isPlaying: Boolean) {
        val session = mediaSession ?: return
        isPlayingMedia = isPlaying
        currentMediaTitle = title
        currentMediaArtist = artist ?: "Ember Browser"
        
        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        
        session.setPlaybackState(PlaybackStateCompat.Builder()
            .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
            .setActions(
                PlaybackStateCompat.ACTION_PLAY_PAUSE or 
                PlaybackStateCompat.ACTION_PAUSE or 
                PlaybackStateCompat.ACTION_PLAY or 
                PlaybackStateCompat.ACTION_STOP or 
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or 
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            )
            .build())
        
        session.setMetadata(MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentMediaTitle)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentMediaArtist)
            .build())

        showMediaNotification(currentMediaTitle, currentMediaArtist, isPlaying)
    }

    private fun togglePlayback() {
        lifecycleScope.launch(Dispatchers.Main) {
            currentWebView?.evaluateJavascript("""
                (function() {
                    var media = document.querySelector('video') || document.querySelector('audio');
                    if (media) {
                        if (media.paused) media.play();
                        else media.pause();
                    }
                })();
            """.trimIndent(), null)
        }
    }

    private fun skipNext() {
        lifecycleScope.launch(Dispatchers.Main) {
            currentWebView?.evaluateJavascript("""
                (function() {
                    var nextBtn = document.querySelector('.ytp-next-button') || document.querySelector('[aria-label="Siguiente"]') || document.querySelector('[aria-label="Next"]');
                    if (nextBtn) nextBtn.click();
                })();
            """.trimIndent(), null)
        }
    }

    private fun skipPrevious() {
        lifecycleScope.launch(Dispatchers.Main) {
            currentWebView?.evaluateJavascript("window.history.back();", null)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("ember_media", "Media Playback", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun showMediaNotification(title: String, artist: String, isPlaying: Boolean) {
        val session = mediaSession ?: return
        val manager = getSystemService(NotificationManager::class.java) as NotificationManager
        
        // No mostrar si no hay contenido real
        if (!isPlaying && title == "Ember Browser") {
            manager.cancel(1)
            return
        }

        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        
        val notification = NotificationCompat.Builder(this, "ember_media")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(artist)
            .setOngoing(isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(session.sessionToken)
                .setShowActionsInCompactView(0, 1, 2))
            .addAction(NotificationCompat.Action(android.R.drawable.ic_media_previous, "Anterior", null))
            .addAction(NotificationCompat.Action(playPauseIcon, if (isPlaying) "Pausar" else "Reproducir", null))
            .addAction(NotificationCompat.Action(android.R.drawable.ic_media_next, "Siguiente", null))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        manager.notify(1, notification)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.getDefault()
        }
    }

    private fun speak(text: String) {
        if (text.startsWith("__MEDIA_PLAY__")) {
            val data = text.removePrefix("__MEDIA_PLAY__").split("|")
            updateMediaStatus(data.getOrElse(0) { "Ember" }, data.getOrNull(1), true)
        } else if (text.startsWith("__MEDIA_PAUSE__")) {
            val data = text.removePrefix("__MEDIA_PAUSE__").split("|")
            updateMediaStatus(data.getOrElse(0) { "Ember" }, data.getOrNull(1), false)
        } else {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        mediaSession?.release()
        super.onDestroy()
    }
}

class TtsInterface(private val onSpeak: (String) -> Unit) {
    @JavascriptInterface
    fun speak(text: String) {
        onSpeak(text)
    }
}

fun Bitmap.toByteArray(): ByteArray {
    val stream = ByteArrayOutputStream()
    this.compress(Bitmap.CompressFormat.PNG, 100, stream)
    return stream.toByteArray()
}

fun ByteArray.toBitmap(): Bitmap? {
    return BitmapFactory.decodeByteArray(this, 0, this.size)
}

val sp12 = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    var contextMenuInfo by remember { mutableStateOf<Pair<String, String>?>(null) } // Label to URL
    
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

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    LaunchedEffect(Unit) {
        val perms = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(perms.toTypedArray())
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
                    
                    AnimatedVisibility(
                        visible = loadingProgress in 1..99,
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
                                    .shadow(8.dp, RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)),
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
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
                        sharedPrefs.edit().putString("homepage_url", url).apply()
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
                    onProgressChanged = { loadingProgress = it },
                    isRefreshing = isRefreshing,
                    onRefresh = { 
                        isRefreshing = true
                        webView?.reload()
                        scope.launch { delay(1000); isRefreshing = false }
                    },
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
                        WebStorage.getInstance().deleteOrigin(Uri.parse(currentTab.url).host ?: "")
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
                        ContextAction("Abrir en pestaña nueva", Icons.Default.Tab) {
                            tabs = tabs + TabItem(url = url)
                            selectedTabIndex = tabs.size - 1
                            contextMenuInfo = null
                        },
                        ContextAction("Copiar enlace", Icons.Default.ContentCopy) {
                            clipboardManager.setText(AnnotatedString(url))
                            Toast.makeText(context, "Enlace copiado", Toast.LENGTH_SHORT).show()
                            contextMenuInfo = null
                        },
                        ContextAction("Descargar", Icons.Default.Download) {
                            downloadUrl = url
                            showDownloadDialog = true
                            contextMenuInfo = null
                        },
                        ContextAction("Compartir", Icons.Default.Share) {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, url)
                            }
                            context.startActivity(Intent.createChooser(intent, "Compartir enlace"))
                            contextMenuInfo = null
                        }
                    )
                )
            }

            permissionRequest?.let { (request, origin) ->
                AlertDialog(
                    onDismissRequest = { permissionRequest = null },
                    title = { Text("Permiso solicitado") },
                    text = { Text("El sitio $origin solicita acceso a: ${request.resources.joinToString()}") },
                    confirmButton = {
                        Button(onClick = { request.grant(request.resources); permissionRequest = null }) { Text("Permitir") }
                    },
                    dismissButton = {
                        TextButton(onClick = { request.deny(); permissionRequest = null }) { Text("Denegar") }
                    }
                )
            }

            if (showSettingsScreen) {
                SettingsScreen(
                    enhancedProtection = enhancedProtection,
                    onEnhancedProtectionChange = {
                        enhancedProtection = it
                        sharedPrefs.edit().putBoolean("enhanced_protection", it).apply()
                    },
                    searchEngine = searchEngine,
                    onSearchEngineChange = {
                        searchEngine = it
                        sharedPrefs.edit().putString("search_engine", it).apply()
                    },
                    onResetHome = {
                        customHomepageUrl = ""
                        sharedPrefs.edit().remove("homepage_url").apply()
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
                        sharedPrefs.edit().putString("download_path", it).apply()
                    },
                    onReadPage = {
                        webView?.evaluateJavascript("(function() { return document.body.innerText; })();") { text ->
                            onSpeak(text ?: "No se pudo leer el contenido")
                        }
                    },
                    isAdBlockerEnabled = isAdBlockerEnabled,
                    onAdBlockerChange = {
                        isAdBlockerEnabled = it
                        sharedPrefs.edit().putBoolean("ad_blocker_enabled", it).apply()
                    }
                )
            }
        }
    }
}

data class ContextAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CromiteContextMenu(title: String, url: String, onDismiss: () -> Unit, actions: List<ContextAction>) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
    ) {
        Column(Modifier.padding(bottom = 32.dp).navigationBarsPadding()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(if (title == "Imagen") Icons.Default.Image else Icons.Default.Link, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(url, style = MaterialTheme.typography.bodySmall, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(8.dp))
            actions.forEach { action ->
                ListItem(
                    headlineContent = { Text(action.label) },
                    leadingContent = { Icon(action.icon, null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { action.onClick() }
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

@Composable
fun SettingsScreen(
    enhancedProtection: Boolean,
    onEnhancedProtectionChange: (Boolean) -> Unit,
    searchEngine: String,
    onSearchEngineChange: (String) -> Unit,
    onResetHome: () -> Unit,
    onClearData: () -> Unit,
    onBack: () -> Unit,
    downloadPath: String,
    onDownloadPathChange: (String) -> Unit,
    onReadPage: () -> Unit,
    isAdBlockerEnabled: Boolean,
    onAdBlockerChange: (Boolean) -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                Text("Settings", style = MaterialTheme.typography.headlineMedium)
            }
            Spacer(Modifier.height(24.dp))
            
            Text("General", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            ListItem(
                headlineContent = { Text("Search Engine") },
                supportingContent = { Text(searchEngine) },
                leadingContent = { Icon(Icons.Default.Search, null) },
                modifier = Modifier.clickable {
                    val engines = listOf("Google", "Brave", "DuckDuckGo", "Bing")
                    val next = engines[(engines.indexOf(searchEngine) + 1) % engines.size]
                    onSearchEngineChange(next)
                }
            )
            
            ListItem(
                headlineContent = { Text("Ad-Blocker") },
                supportingContent = { Text("Block annoying ads and trackers") },
                trailingContent = { Switch(checked = isAdBlockerEnabled, onCheckedChange = onAdBlockerChange) }
            )

            ListItem(
                headlineContent = { Text("Read Current Page") },
                leadingContent = { Icon(Icons.Default.RecordVoiceOver, null) },
                modifier = Modifier.clickable { onReadPage() }
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Security", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            ListItem(
                headlineContent = { Text("Enhanced Protection") },
                supportingContent = { Text("Block non-HTTPS and invalid certificates") },
                trailingContent = { Switch(checked = enhancedProtection, onCheckedChange = onEnhancedProtectionChange) }
            )
            ListItem(
                headlineContent = { Text("Clear All Browsing Data") },
                leadingContent = { Icon(Icons.Default.DeleteForever, null) },
                modifier = Modifier.clickable { onClearData() }
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Downloads", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(
                value = downloadPath,
                onValueChange = onDownloadPathChange,
                label = { Text("Download Path") },
                modifier = Modifier.fillMaxWidth(),
                textStyle = sp12
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Advanced", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            ListItem(
                headlineContent = { Text("Reset Homepage") },
                modifier = Modifier.clickable { onResetHome() }
            )
        }
    }
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
    onRefresh: () -> Unit,
    onFaviconChanged: (Bitmap?) -> Unit,
    onPermissionRequested: (PermissionRequest, String) -> Unit,
    onImageLongClick: (String) -> Unit,
    enhancedProtection: Boolean,
    onMediaStatus: (String, String?, Boolean) -> Unit,
    isAdBlockerEnabled: Boolean
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
            onImageLongClick = onImageLongClick,
            enhancedProtection = enhancedProtection,
            onMediaStatus = onMediaStatus,
            isAdBlockerEnabled = isAdBlockerEnabled
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
    onImageLongClick: (String) -> Unit,
    enhancedProtection: Boolean,
    onMediaStatus: (String, String?, Boolean) -> Unit,
    isAdBlockerEnabled: Boolean
) {
    var errorInfo by remember { mutableStateOf<String?>(null) }
    var isBlockedBySecurity by remember { mutableStateOf(false) }
    
    LaunchedEffect(url, enhancedProtection) {
        if (enhancedProtection && !url.startsWith("https://") && url != "home" && !url.startsWith("file://") && !url.startsWith("about:")) {
            isBlockedBySecurity = true
        } else {
            isBlockedBySecurity = false
        }
    }

    if (isBlockedBySecurity) {
        SecurityBlockScreen(onBack = { isBlockedBySecurity = false })
    } else if (errorInfo != null) {
        ErrorScreen(errorInfo = errorInfo, onRetry = { errorInfo = null })
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
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                }
                
                // Configurar cookies para esta instancia
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
                            val adDomains = listOf(
                                "doubleclick.net", "googleadservices.com", "googlesyndication.com",
                                "moatads.com", "adservice.google.com", "pagead2.googlesyndication.com",
                                "static.doubleclick.net", "ad.doubleclick.net"
                            )
                            val urlString = request?.url?.toString() ?: ""
                            for (domain in adDomains) {
                                if (urlString.contains(domain)) {
                                    return WebResourceResponse("text/plain", "utf-8", null)
                                }
                            }
                        }
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        // Guardar cookies en disco al terminar
                        CookieManager.getInstance().flush()
                        
                        if (errorInfo == null && !isBlockedBySecurity) {
                            url?.let { onPageFinished(it, view?.title) }
                            
                            // Inject script to detect media and metadata
                            view?.evaluateJavascript("""
                                (function() {
                                    var media = document.querySelector('video') || document.querySelector('audio');
                                    if (media) {
                                        var updateMedia = function() {
                                            var title = document.title;
                                            var artist = "Ember Browser";
                                            
                                            if (navigator.mediaSession && navigator.mediaSession.metadata) {
                                                if (navigator.mediaSession.metadata.title) title = navigator.mediaSession.metadata.title;
                                                if (navigator.mediaSession.metadata.artist) artist = navigator.mediaSession.metadata.artist;
                                            }
                                            
                                            if (!media.paused) {
                                                EmberTTS.speak("__MEDIA_PLAY__" + title + "|" + artist);
                                            } else {
                                                EmberTTS.speak("__MEDIA_PAUSE__" + title + "|" + artist);
                                            }
                                        };
                                        
                                        media.onplay = updateMedia;
                                        media.onpause = updateMedia;
                                        // Trigger check on load
                                        setTimeout(updateMedia, 1500);
                                    }
                                    
                                    if (!window.speechSynthesis) {
                                        window.speechSynthesis = {
                                            speak: function(utterance) { if (utterance && utterance.text) EmberTTS.speak(utterance.text); },
                                            cancel: function() {}, pause: function() {}, resume: function() {}, getVoices: function() { return []; }
                                        };
                                        window.SpeechSynthesisUtterance = function(text) { this.text = text; };
                                    }
                                })();
                            """.trimIndent(), null)
                        }
                    }

                    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: android.net.http.SslError?) {
                        if (enhancedProtection) {
                            isBlockedBySecurity = true
                            handler?.cancel()
                        } else {
                            handler?.proceed()
                        }
                    }

                    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                        if (request?.isForMainFrame == true) errorInfo = error?.description?.toString() ?: "Error de red"
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onPermissionRequest(request: PermissionRequest) {
                        onPermissionRequested(request, url)
                    }
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        onProgressChanged(newProgress)
                    }
                    override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
                        onFaviconChanged(icon)
                    }
                }
                onWebViewCreated(this)
                loadUrl(url)
            }
        },
        update = { webView -> 
            if (errorInfo == null && !isBlockedBySecurity && webView.url != url && url != "home") {
                webView.loadUrl(url) 
            }
        },
        modifier = Modifier.fillMaxSize().then(if (errorInfo != null || isBlockedBySecurity) Modifier.size(0.dp) else Modifier)
    )
}

@Composable
fun SecurityBlockScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.GppBad, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(16.dp))
        Text(text = "Sitio bloqueado por seguridad", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Ember ha bloqueado el acceso a este sitio porque no utiliza una conexión segura (HTTPS) o su certificado no es válido. Navegar aquí podría exponer tus datos.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onBack) {
            Text("Volver a un lugar seguro")
        }
    }
}

@Composable
fun ErrorScreen(errorInfo: String?, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "x _ x", fontSize = 64.sp, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(text = "No se puede acceder a este sitio", fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(text = errorInfo ?: "Error desconocido", fontSize = 14.sp, color = Color.Gray, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry) { Text("Reintentar") }
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
            LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(tabs) { index, tab ->
                    Card(
                        modifier = Modifier.fillMaxWidth().height(72.dp).clickable { onTabSelected(index) },
                        border = if (index == selectedIndex) CardDefaults.outlinedCardBorder() else null,
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                                if (tab.favicon != null) {
                                    Image(bitmap = tab.favicon!!.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                                } else {
                                    Icon(Icons.Default.Public, null, tint = Color.Gray)
                                }
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(tab.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text(tab.url, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                            IconButton(onClick = { onTabClosed(index) }) {
                                Icon(Icons.Default.Close, null, Modifier.size(20.dp))
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

@Composable
fun BookmarkDialog(items: List<BookmarkItem>, onUrlClick: (String) -> Unit, onDismiss: () -> Unit, onAddBookmark: () -> Unit, onDeleteBookmark: (Long) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) { Text("Bookmarks"); IconButton(onClick = onAddBookmark) { Icon(Icons.Default.AddCircleOutline, null) } } },
        text = { 
            if (items.isEmpty()) { Text("No bookmarks yet", modifier = Modifier.fillMaxWidth().padding(16.dp), textAlign = TextAlign.Center) }
            else {
                LazyColumn(Modifier.height(400.dp)) { 
                    items(items) { item -> 
                        ListItem(
                            headlineContent = { Text(item.title) }, 
                            supportingContent = { Text(item.url) }, 
                            modifier = Modifier.clickable { onUrlClick(item.url) },
                            leadingContent = {
                                Box(Modifier.size(32.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                                    if (item.favicon != null) {
                                        Image(bitmap = item.favicon.toBitmap()!!.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                                    } else {
                                        Icon(Icons.Default.Public, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            trailingContent = { IconButton(onClick = { onDeleteBookmark(item.id) }) { Icon(Icons.Default.Delete, null, tint = Color.Gray) } }
                        ) 
                    } 
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDismiss() }) { Text("Close") } }
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
            if (items.isEmpty()) { Text("No history yet", modifier = Modifier.fillMaxWidth().padding(16.dp), textAlign = TextAlign.Center) }
            else { 
                LazyColumn(Modifier.height(400.dp)) { 
                    items(items) { item -> 
                        ListItem(
                            headlineContent = { Text(item.title, maxLines = 1) }, 
                            supportingContent = { Text(item.url, maxLines = 1) }, 
                            modifier = Modifier.clickable { onUrlClick(item.url) },
                            leadingContent = {
                                Box(Modifier.size(32.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                                    if (item.favicon != null) {
                                        Image(bitmap = item.favicon.toBitmap()!!.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                                    } else {
                                        Icon(Icons.Default.Public, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        ) 
                    } 
                } 
            }
        },
        confirmButton = { TextButton(onClick = { onDismiss() }) { Text("Close") } }
    )
}

@Composable
fun DownloadConfirmDialog(url: String, defaultPath: String, onConfirm: (String, String) -> Unit, onDismiss: () -> Unit) {
    var fileName by remember { mutableStateOf(url.substringAfterLast("/")) }
    var path by remember { mutableStateOf(defaultPath) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Descargar archivo") },
        text = { 
            Column {
                Text(url, style = MaterialTheme.typography.bodySmall, color = Color.Gray, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = fileName, 
                    onValueChange = { fileName = it }, 
                    label = { Text("Nombre del archivo") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = path, 
                    onValueChange = { path = it }, 
                    label = { Text("Ruta de descarga") },
                    modifier = Modifier.fillMaxWidth()
                ) 
            } 
        },
        confirmButton = { Button(onClick = { onConfirm(fileName, path) }) { Text("Descargar") } },
        dismissButton = { TextButton(onClick = { onDismiss() }) { Text("Cancelar") } }
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
            .setDestinationUri(Uri.fromFile(java.io.File(path, fileName)))
        
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.enqueue(request)
        
        (context as? MainActivity)?.lifecycleScope?.launch {
            database.browserDao().insertDownload(DownloadItem(url = url, fileName = fileName, filePath = path, totalSize = 0))
        }
        Toast.makeText(context, "Download started", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) { Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_LONG).show() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(onSearch: (String) -> Unit, onConfigureHomepage: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf(listOf<String>()) }
    var showConfigDialog by remember { mutableStateOf(false) }
    val client = remember { OkHttpClient() }

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
                    for (i in 0 until jsonSuggestions.length()) { suggestionList.add(jsonSuggestions.getString(i)) }
                    withContext(Dispatchers.Main) { suggestions = suggestionList }
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
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, null) } }
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
