package com.jntx.emberbrowser

import android.graphics.Bitmap
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history")
data class HistoryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val timestamp: Long = System.currentTimeMillis(),
    val favicon: ByteArray? = null
)

@Entity(tableName = "bookmarks")
data class BookmarkItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val isFavorite: Boolean = false,
    val favicon: ByteArray? = null
)

@Entity(tableName = "downloads")
data class DownloadItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val fileName: String,
    val filePath: String,
    val totalSize: Long,
    val progress: Int = 0,
    val status: String = "PENDING", // PENDING, DOWNLOADING, COMPLETED, FAILED
    val timestamp: Long = System.currentTimeMillis()
)

data class TabItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    var url: String = "home",
    var title: String = "New Tab",
    var favicon: Bitmap? = null
)
