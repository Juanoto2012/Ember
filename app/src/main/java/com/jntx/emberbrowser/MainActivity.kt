package com.jntx.emberbrowser

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import androidx.media.session.MediaButtonReceiver
import com.jntx.emberbrowser.ui.BrowserApp
import com.jntx.emberbrowser.ui.theme.EmberTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var currentWebView: WebView? = null
    private var currentArtwork: Bitmap? = null
    private var lastTitle = ""
    private var lastArtist = ""
    private var lastIsPlaying = false
    private var currentPosition = 0L
    private var currentDuration = 0L

    companion object {
        var mediaSession: MediaSessionCompat? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        CookieManager.getInstance().setAcceptCookie(true)
        
        tts = TextToSpeech(this, this)
        setupMediaSession()
        createNotificationChannel()
        
        val database = AppDatabase.getDatabase(this)
        setContent {
            EmberTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    BrowserApp(
                        database = database,
                        onSpeak = ::speak,
                        onGetVoices = ::getAvailableVoicesJson,
                        onMediaStatus = ::updateMediaStatus,
                        onWebViewCreated = { currentWebView = it }
                    )
                }
            }
        }
    }

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "EmberMediaSession").apply {
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            
            val mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
            mediaButtonIntent.setClass(this@MainActivity, MediaButtonReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(this@MainActivity, 0, mediaButtonIntent, PendingIntent.FLAG_IMMUTABLE)
            setMediaButtonReceiver(pendingIntent)

            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { togglePlayback() }
                override fun onPause() { togglePlayback() }
                override fun onSkipToNext() { skipNext() }
                override fun onSkipToPrevious() { skipPrevious() }
                override fun onSeekTo(pos: Long) { seekTo(pos) }
                override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
                    val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        mediaButtonEvent?.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        mediaButtonEvent?.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                    }
                    if (keyEvent?.action == KeyEvent.ACTION_DOWN) {
                        when (keyEvent.keyCode) {
                            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> togglePlayback()
                            KeyEvent.KEYCODE_MEDIA_PLAY -> togglePlayback()
                            KeyEvent.KEYCODE_MEDIA_PAUSE -> togglePlayback()
                            KeyEvent.KEYCODE_MEDIA_NEXT -> skipNext()
                            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> skipPrevious()
                        }
                    }
                    return true
                }
            })
            isActive = true
        }
    }

    private fun updateMediaStatus(title: String, artist: String?, isPlaying: Boolean) {
        lastTitle = if (title.isBlank()) "Ember Browser" else title
        lastArtist = artist ?: "Ember Browser"
        lastIsPlaying = isPlaying
        updatePlaybackState()
        updateMetadata()
        showMediaNotification(lastTitle, lastArtist, lastIsPlaying, currentArtwork)
    }

    private fun updatePlaybackState() {
        val session = mediaSession ?: return
        val state = if (lastIsPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        
        session.setPlaybackState(PlaybackStateCompat.Builder()
            .setState(state, currentPosition, if (lastIsPlaying) 1.0f else 0.0f)
            .setActions(
                PlaybackStateCompat.ACTION_PLAY_PAUSE or 
                PlaybackStateCompat.ACTION_PAUSE or 
                PlaybackStateCompat.ACTION_PLAY or 
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or 
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_STOP
            )
            .build())
    }

    private fun updateMetadata() {
        val session = mediaSession ?: return
        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, lastTitle)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, lastArtist)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, currentDuration)
        
        currentArtwork?.let {
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it)
        }
        
        session.setMetadata(metadataBuilder.build())
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
                    var nextBtn = document.querySelector('.ytp-next-button') || document.querySelector('[aria-label*="Siguiente"]') || document.querySelector('[aria-label*="Next"]');
                    if (nextBtn) nextBtn.click();
                    else {
                        var media = document.querySelector('video') || document.querySelector('audio');
                        if (media) media.currentTime = media.duration;
                    }
                })();
            """.trimIndent(), null)
        }
    }

    private fun skipPrevious() {
        lifecycleScope.launch(Dispatchers.Main) {
            currentWebView?.evaluateJavascript("""
                (function() {
                    var prevBtn = document.querySelector('.ytp-prev-button') || document.querySelector('[aria-label*="Anterior"]') || document.querySelector('[aria-label*="Previous"]');
                    if (prevBtn) prevBtn.click();
                    else window.history.back();
                })();
            """.trimIndent(), null)
        }
    }

    private fun seekTo(pos: Long) {
        lifecycleScope.launch(Dispatchers.Main) {
            currentWebView?.evaluateJavascript("""
                (function() {
                    var media = document.querySelector('video') || document.querySelector('audio');
                    if (media) media.currentTime = ${pos / 1000.0};
                })();
            """.trimIndent(), null)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("ember_media", "Media Playback", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun showMediaNotification(title: String, artist: String, isPlaying: Boolean, artwork: Bitmap?) {
        val session = mediaSession ?: return
        val manager = getSystemService(NotificationManager::class.java) as NotificationManager
        
        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        
        val prevIntent = PendingIntent.getBroadcast(this, 10, Intent(this, MediaControlReceiver::class.java).apply { action = "ACTION_PREVIOUS" }, PendingIntent.FLAG_IMMUTABLE)
        val playPauseIntent = PendingIntent.getBroadcast(this, 11, Intent(this, MediaControlReceiver::class.java).apply { action = "ACTION_PLAY_PAUSE" }, PendingIntent.FLAG_IMMUTABLE)
        val nextIntent = PendingIntent.getBroadcast(this, 12, Intent(this, MediaControlReceiver::class.java).apply { action = "ACTION_NEXT" }, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, "ember_media")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(artist)
            .setLargeIcon(artwork)
            .setOngoing(isPlaying)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(session.sessionToken)
                .setShowActionsInCompactView(0, 1, 2))
            .addAction(NotificationCompat.Action(android.R.drawable.ic_media_previous, "Anterior", prevIntent))
            .addAction(NotificationCompat.Action(playPauseIcon, if (isPlaying) "Pausa" else "Play", playPauseIntent))
            .addAction(NotificationCompat.Action(android.R.drawable.ic_media_next, "Siguiente", nextIntent))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        
        manager.notify(1, notification)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.getDefault()
            lifecycleScope.launch(Dispatchers.Main) {
                currentWebView?.evaluateJavascript("""
                    (function() {
                        if (window.speechSynthesis && typeof window.speechSynthesis.onvoiceschanged !== 'undefined') {
                            window.speechSynthesis.onvoiceschanged();
                        }
                        window.dispatchEvent(new Event('voiceschanged'));
                    })();
                """.trimIndent(), null)
            }
        }
    }

    private fun getAvailableVoicesJson(): String {
        val voicesArray = JSONArray()
        try {
            tts?.voices?.forEach { voice ->
                val voiceObj = JSONObject().apply {
                    put("name", voice.name)
                    put("lang", voice.locale.toLanguageTag())
                    put("voiceURI", voice.name)
                    put("localService", true)
                    put("default", voice.locale == Locale.getDefault())
                }
                voicesArray.put(voiceObj)
            }
        } catch (e: Exception) { e.printStackTrace() }
        return voicesArray.toString()
    }

    private fun speak(data: String) {
        when {
            data.startsWith("__MEDIA_PLAY__") -> {
                val splitData = data.removePrefix("__MEDIA_PLAY__").split("|")
                updateMediaStatus(splitData.getOrElse(0) { "" }, splitData.getOrNull(1), true)
            }
            data.startsWith("__MEDIA_PAUSE__") -> {
                val splitData = data.removePrefix("__MEDIA_PAUSE__").split("|")
                updateMediaStatus(splitData.getOrElse(0) { "" }, splitData.getOrNull(1), false)
            }
            data.startsWith("__MEDIA_ARTWORK__") -> {
                fetchArtwork(data.removePrefix("__MEDIA_ARTWORK__"))
            }
            data.startsWith("__MEDIA_PROGRESS__") -> {
                val splitData = data.removePrefix("__MEDIA_PROGRESS__").split("|")
                currentPosition = splitData.getOrNull(0)?.toDoubleOrNull()?.toLong() ?: currentPosition
                currentDuration = splitData.getOrNull(1)?.toDoubleOrNull()?.toLong() ?: currentDuration
                updatePlaybackState()
                updateMetadata()
            }
            data.startsWith("__TTS_SPEAK_JSON__") -> {
                try {
                    val json = JSONObject(data.removePrefix("__TTS_SPEAK_JSON__"))
                    val text = json.getString("text")
                    val rate = json.optDouble("rate", 1.0).toFloat()
                    val pitch = json.optDouble("pitch", 1.0).toFloat()
                    val voiceName = json.optString("voice")

                    tts?.apply {
                        setSpeechRate(rate)
                        setPitch(pitch)
                        if (voiceName.isNotEmpty()) {
                            voices?.find { it.name == voiceName }?.let { voice = it }
                        }
                        speak(text, TextToSpeech.QUEUE_FLUSH, null, "ember_tts_id")
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
            data.startsWith("__TTS_CANCEL__") -> tts?.stop()
            data.startsWith("__TTS_PAUSE__") -> tts?.stop()
            else -> tts?.speak(data, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    private fun fetchArtwork(url: String) {
        if (url.isBlank()) return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val bitmap = BitmapFactory.decodeStream(URL(url).openStream())
                currentArtwork = bitmap
                withContext(Dispatchers.Main) {
                    updateMetadata()
                    showMediaNotification(lastTitle, lastArtist, lastIsPlaying, currentArtwork)
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        mediaSession?.release()
        super.onDestroy()
    }
}
