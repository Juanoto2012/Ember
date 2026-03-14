package com.jntx.emberbrowser.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jntx.emberbrowser.DownloadItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(
    items: List<DownloadItem>,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Descargas") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (items.isEmpty()) {
            Box(modifier = Modifier.padding(paddingValues).fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Download, null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                    Spacer(Modifier.height(16.dp))
                    Text("No tienes descargas aún", color = Color.Gray)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
                items(items) { item ->
                    ListItem(
                        headlineContent = { Text(item.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text("${item.status} • ${item.filePath}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = {
                            Icon(Icons.Default.FileDownload, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    )
                }
            }
        }
    }
}
