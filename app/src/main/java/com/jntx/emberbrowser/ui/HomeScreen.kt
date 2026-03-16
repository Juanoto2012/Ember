package com.jntx.emberbrowser.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

@Composable
fun HomeScreen(onSearch: (String) -> Unit, onConfigureHomepage: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf(listOf<String>()) }
    var showConfigDialog by remember { mutableStateOf(false) }
    val client = remember { OkHttpClient() }

    val shortcuts = remember {
        listOf(
            ShortcutItem("Google", "https://www.google.com", Icons.Default.Search),
            ShortcutItem("YouTube", "https://www.youtube.com", Icons.Default.PlayArrow),
            ShortcutItem("Facebook", "https://www.facebook.com", Icons.Default.Public),
            ShortcutItem("X", "https://www.x.com", Icons.Default.Language),
            ShortcutItem("GitHub", "https://www.github.com", Icons.Default.Code),
            ShortcutItem("Wikipedia", "https://www.wikipedia.org", Icons.AutoMirrored.Filled.MenuBook)
        )
    }

    LaunchedEffect(query) {
        if (query.length > 1) {
            delay(300)
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
            title = { Text("Configurar Inicio") },
            text = { 
                OutlinedTextField(
                    value = urlInput, 
                    onValueChange = { urlInput = it }, 
                    label = { Text("URL de la página de inicio") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) 
            },
            confirmButton = { 
                Button(onClick = { onConfigureHomepage(urlInput); showConfigDialog = false }) { 
                    Text("Guardar") 
                } 
            },
            dismissButton = { 
                TextButton(onClick = { showConfigDialog = false }) { 
                    Text("Cancelar") 
                } 
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Botón de configuración en la esquina superior derecha
        IconButton(
            onClick = { showConfigDialog = true },
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        ) {
            Icon(Icons.Outlined.Settings, "Configurar inicio", tint = MaterialTheme.colorScheme.primary)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(0.3f))
            
            // Logo Ember Plano
            Icon(
                imageVector = Icons.Default.Whatshot,
                contentDescription = "Logo",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = "Ember",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Spacer(Modifier.height(40.dp))

            // Barra de búsqueda simple y plana
            Surface(
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = null
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Buscar o escribir URL", fontSize = 15.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { if(query.isNotEmpty()) onSearch(query) }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )
                }
            }

            AnimatedVisibility(visible = suggestions.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    Column {
                        suggestions.take(4).forEach { suggestion ->
                            ListItem(
                                headlineContent = { Text(suggestion, fontSize = 14.sp) },
                                modifier = Modifier.clickable { onSearch(suggestion) }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            // Accesos directos planos
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(shortcuts) { item ->
                    ShortcutItemView(item) { onSearch(item.url) }
                }
            }

            Spacer(Modifier.weight(0.5f))
        }
    }
}

@Composable
fun ShortcutItemView(item: ShortcutItem, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(item.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(item.name, fontSize = 10.sp, maxLines = 1, textAlign = TextAlign.Center)
    }
}

data class ShortcutItem(val name: String, val url: String, val icon: ImageVector)
