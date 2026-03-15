package com.jntx.emberbrowser

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.support.v4.media.session.PlaybackStateCompat

class MediaControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action ?: return
        MainActivity.mediaSession?.let { session ->
            val controls = session.controller.transportControls
            when (action) {
                "ACTION_PLAY_PAUSE" -> {
                    val state = session.controller.playbackState?.state
                    if (state == PlaybackStateCompat.STATE_PLAYING) controls.pause()
                    else controls.play()
                }
                "ACTION_PREVIOUS" -> controls.skipToPrevious()
                "ACTION_NEXT" -> controls.skipToNext()
            }
        }
    }
}
