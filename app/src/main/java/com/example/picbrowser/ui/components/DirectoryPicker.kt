package com.example.picbrowser.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun DirectoryPicker(
    onDirectorySelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val initialPath = remember {
        // Try common storage paths
        val paths = listOfNotNull(
            "/storage/emulated/0",
            "/sdcard",
            "/"
        )
        paths.firstOrNull { path ->
            try {
                File(path).exists() && File(path).isDirectory && File(path).canRead()
            } catch (_: Exception) {
                false
            }
        } ?: "/"
    }

    var currentPath by remember { mutableStateOf(initialPath) }
    var directories by remember { mutableStateOf<List<File>>(emptyList()) }

    LaunchedEffect(currentPath) {
        directories = withContext(Dispatchers.IO) {
            val file = File(currentPath)
            if (file.exists() && file.isDirectory) {
                file.listFiles()
                    ?.filter { it.isDirectory && it.canRead() }
                    ?.sortedWith(compareBy({ it.name.startsWith(".") }, { it.name.lowercase() }))
                    ?: emptyList()
            } else {
                emptyList()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择目录") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentPath != "/") {
                        IconButton(onClick = {
                            val parent = File(currentPath).parentFile
                            if (parent != null) {
                                currentPath = parent.absolutePath
                            }
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回上一级")
                        }
                    }
                    Text(
                        text = currentPath,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(directories) { dir ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable { currentPath = dir.absolutePath }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = dir.name,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDirectorySelected(currentPath) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("选择此目录")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}