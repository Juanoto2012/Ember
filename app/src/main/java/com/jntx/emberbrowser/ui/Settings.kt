package com.jntx.emberbrowser.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(
    enhancedProtection: Boolean,
    onEnhancedProtectionChange: (Boolean) -> Unit,
    searchEngine: String,
    onSearchEngineChange: (String) -> Unit,
    onResetHome: () -> Unit,
    onClearData: () -> Unit,
    onBack: () -> Unit,
    downloadPath: String,
    onDownloadPathChange: (String) -> Unit,
    onReadPage: () -> Unit,
    isAdBlockerEnabled: Boolean,
    onAdBlockerChange: (Boolean) -> Unit,
    textZoom: Int,
    onTextZoomChange: (Int) -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                Text("Settings", style = MaterialTheme.typography.headlineMedium)
            }
            Spacer(Modifier.height(24.dp))
            
            Text("General", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            ListItem(
                headlineContent = { Text("Search Engine") },
                supportingContent = { Text(searchEngine) },
                leadingContent = { Icon(Icons.Default.Search, null) },
                modifier = Modifier.clickable {
                    val engines = listOf("Google", "Brave", "DuckDuckGo", "Bing")
                    val next = engines[(engines.indexOf(searchEngine) + 1) % engines.size]
                    onSearchEngineChange(next)
                }
            )
            
            ListItem(
                headlineContent = { Text("Ad-Blocker") },
                supportingContent = { Text("Block annoying ads and trackers") },
                trailingContent = { Switch(checked = isAdBlockerEnabled, onCheckedChange = onAdBlockerChange) }
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Accessibility", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Text Zoom: $textZoom%", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = textZoom.toFloat(),
                    onValueChange = { onTextZoomChange(it.toInt()) },
                    valueRange = 50f..200f,
                    steps = 15
                )
            }

            ListItem(
                headlineContent = { Text("Read Current Page") },
                leadingContent = { Icon(Icons.Default.RecordVoiceOver, null) },
                modifier = Modifier.clickable { onReadPage() }
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Security", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            ListItem(
                headlineContent = { Text("Enhanced Protection") },
                supportingContent = { Text("Block non-HTTPS and invalid certificates") },
                trailingContent = { Switch(checked = enhancedProtection, onCheckedChange = onEnhancedProtectionChange) }
            )
            ListItem(
                headlineContent = { Text("Clear All Browsing Data") },
                leadingContent = { Icon(Icons.Default.DeleteForever, null) },
                modifier = Modifier.clickable { onClearData() }
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Downloads", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(
                value = downloadPath,
                onValueChange = onDownloadPathChange,
                label = { Text("Download Path") },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Advanced", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            ListItem(
                headlineContent = { Text("Reset Homepage") },
                modifier = Modifier.clickable { onResetHome() }
            )
        }
    }
}
