package com.jntx.emberbrowser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

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
