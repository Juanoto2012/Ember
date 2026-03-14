package com.jntx.emberbrowser.ui

import android.net.http.SslCertificate
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jntx.emberbrowser.BookmarkItem
import com.jntx.emberbrowser.DownloadItem
import com.jntx.emberbrowser.HistoryItem
import com.jntx.emberbrowser.utils.toBitmap

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
                                    val bitmap = item.favicon?.toBitmap()
                                    if (bitmap != null) {
                                        Image(bitmap = bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
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
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
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
                                    val bitmap = item.favicon?.toBitmap()
                                    if (bitmap != null) {
                                        Image(bitmap = bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
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
    var fileName by remember { mutableStateOf(url.substringAfterLast("/", "file")) }
    var path by remember { mutableStateOf(defaultPath) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Download, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Descargar archivo") },
        text = { 
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = url,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                OutlinedTextField(
                    value = fileName, 
                    onValueChange = { fileName = it }, 
                    label = { Text("Nombre del archivo") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                OutlinedTextField(
                    value = path, 
                    onValueChange = { path = it }, 
                    label = { Text("Ruta de descarga") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                ) 
            } 
        },
        confirmButton = { 
            Button(
                onClick = { onConfirm(fileName, path) },
                shape = RoundedCornerShape(8.dp)
            ) { 
                Text("Descargar") 
            } 
        },
        dismissButton = { 
            TextButton(onClick = onDismiss) { 
                Text("Cancelar") 
            } 
        }
    )
}

@Composable
fun DownloadManagerDialog(items: List<DownloadItem>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Downloads") },
        text = { 
            LazyColumn(modifier = Modifier.height(400.dp)) {
                items(items) { item -> 
                    ListItem(headlineContent = { Text(item.fileName) }, supportingContent = { Text("${item.status} - ${item.filePath}") }) 
                }
                if (items.isEmpty()) { 
                    item { Text("No downloads yet", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) } 
                } 
            } 
        },
        confirmButton = { TextButton(onClick = { onDismiss() }) { Text("Close") } }
    )
}
