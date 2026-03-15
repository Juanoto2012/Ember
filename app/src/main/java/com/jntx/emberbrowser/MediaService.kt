package com.jntx.emberbrowser

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.media.session.MediaButtonReceiver

class MediaService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MainActivity.mediaSession?.let {
            MediaButtonReceiver.handleIntent(it, intent)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
