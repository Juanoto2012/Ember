package com.jntx.emberbrowser.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jntx.emberbrowser.TabItem

@Composable
fun TabManagerView(
    tabs: List<TabItem>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    onTabClosed: (Int) -> Unit,
    onNewTab: () -> Unit,
    onClose: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp), 
                Arrangement.SpaceBetween, 
                Alignment.CenterVertically
            ) {
                Text("Pestañas", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Row {
                    IconButton(onClick = onNewTab) { Icon(Icons.Default.Add, "Nueva pestaña") }
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Cerrar") }
                }
            }
            
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(tabs) { index, tab ->
                    val isSelected = index == selectedIndex
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.85f)
                            .clickable { onTabSelected(index) }
                            .then(
                                if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                                else Modifier
                            ),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column {
                            // Cabecera de la tarjeta
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier
                                        .size(20.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (tab.favicon != null) {
                                        Image(
                                            bitmap = tab.favicon!!.asImageBitmap(),
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Fit
                                        )
                                    } else {
                                        Icon(Icons.Default.Public, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (tab.url == "home") "Nueva pestaña" else tab.title,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(
                                    onClick = { onTabClosed(index) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                                }
                            }
                            
                            // "Preview" (en Firefox es una captura, aquí usamos un color o el icono grande si no hay captura)
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (tab.favicon != null) {
                                    Image(
                                        bitmap = tab.favicon!!.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        alpha = 0.3f
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Public,
                                        null,
                                        modifier = Modifier.size(48.dp),
                                        tint = Color.Gray.copy(alpha = 0.2f)
                                    )
                                }
                                
                                if (tab.url != "home") {
                                    Text(
                                        text = tab.url,
                                        modifier = Modifier.padding(8.dp).align(Alignment.BottomStart),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // Botón inferior para nueva pestaña (estilo Firefox)
            Surface(
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onNewTab,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Nueva pestaña")
                }
            }
        }
    }
}
