package com.jntx.emberbrowser

import android.app.NotificationChannel
import android.app.NotificationManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
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
import com.jntx.emberbrowser.ui.BrowserApp
import com.jntx.emberbrowser.ui.theme.EmberTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var mediaSession: MediaSessionCompat? = null
    private var currentWebView: WebView? = null
    private var currentArtwork: Bitmap? = null
    private var lastTitle = ""
    private var lastArtist = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Habilitar cookies globales (sin requerir WebView aquí)
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
        lastTitle = title
        lastArtist = artist ?: "Ember Browser"
        
        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        
        session.setPlaybackState(PlaybackStateCompat.Builder()
            .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
            .setActions(
                PlaybackStateCompat.ACTION_PLAY_PAUSE or 
                PlaybackStateCompat.ACTION_PAUSE or 
                PlaybackStateCompat.ACTION_PLAY or 
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or 
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_STOP
            )
            .build())
        
        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, lastTitle)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, lastArtist)
        
        currentArtwork?.let {
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it)
        }
        
        session.setMetadata(metadataBuilder.build())
        showMediaNotification(lastTitle, lastArtist, isPlaying, currentArtwork)
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

    private fun showMediaNotification(title: String, artist: String, isPlaying: Boolean, artwork: Bitmap?) {
        val session = mediaSession ?: return
        val manager = getSystemService(NotificationManager::class.java) as NotificationManager
        
        if (!isPlaying && title == "Ember Browser") {
            manager.cancel(1)
            return
        }

        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        
        val notification = NotificationCompat.Builder(this, "ember_media")
            .setSmallIcon(playPauseIcon)
            .setContentTitle(title)
            .setContentText(artist)
            .setLargeIcon(artwork)
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
        } else if (text.startsWith("__MEDIA_ARTWORK__")) {
            val url = text.removePrefix("__MEDIA_ARTWORK__")
            fetchArtwork(url)
        } else {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    private fun fetchArtwork(url: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val bitmap = BitmapFactory.decodeStream(URL(url).openStream())
                currentArtwork = bitmap
                withContext(Dispatchers.Main) {
                    val state = mediaSession?.controller?.playbackState?.state
                    val isPlaying = state == PlaybackStateCompat.STATE_PLAYING
                    updateMediaStatus(lastTitle, lastArtist, isPlaying)
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
