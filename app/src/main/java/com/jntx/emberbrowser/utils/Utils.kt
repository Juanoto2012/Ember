package com.jntx.emberbrowser.utils

import android.app.DownloadManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.webkit.JavascriptInterface
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.jntx.emberbrowser.AppDatabase
import com.jntx.emberbrowser.DownloadItem
import com.jntx.emberbrowser.MainActivity
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File

class TtsInterface(
    private val onSpeak: (String) -> Unit,
    private val onGetVoices: () -> String
) {
    @JavascriptInterface
    fun speak(text: String) {
        onSpeak(text)
    }

    @JavascriptInterface
    fun getVoices(): String {
        return onGetVoices()
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

fun startDownload(context: Context, url: String, fileName: String, path: String, database: AppDatabase) {
    try {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(fileName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(File(path, fileName)))
        
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.enqueue(request)
        
        (context as? MainActivity)?.lifecycleScope?.launch {
            database.browserDao().insertDownload(
                DownloadItem(
                    url = url, 
                    fileName = fileName, 
                    filePath = path, 
                    totalSize = 0,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
        Toast.makeText(context, "Descarga iniciada", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) { 
        Toast.makeText(context, "Error en descarga: ${e.message}", Toast.LENGTH_LONG).show() 
    }
}
