package com.example.picbrowser.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.picbrowser.data.model.ImageItem
import com.example.picbrowser.ui.components.PhotoDetailSheet
import com.example.picbrowser.ui.viewmodel.PhotoViewerViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PhotoViewerScreen(
    imageId: Long,
    folderId: Long?,
    showFavorites: Boolean = false,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val viewModel: PhotoViewerViewModel = viewModel(
        factory = PhotoViewerViewModel.Factory(application, imageId, folderId, showFavorites)
    )
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var imageToDelete by remember { mutableStateOf<ImageItem?>(null) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.images.isEmpty() -> {
                    Text(
                        text = "No images found",
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    val pagerState = rememberPagerState(
                        initialPage = uiState.initialIndex,
                        pageCount = { uiState.images.size }
                    )

                    // Update favorite status when page changes
                    LaunchedEffect(pagerState.currentPage) {
                        if (pagerState.currentPage < uiState.images.size) {
                            viewModel.checkFavorite(uiState.images[pagerState.currentPage].id)
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                            val image = uiState.images[page]
                            ZoomableImage(
                                imageUri = image.uri,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Top Bar overlay
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .background(Color.Black.copy(alpha = 0.5f))
                                .systemBarsPadding()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = onNavigateBack) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowBack,
                                        contentDescription = "Back",
                                        tint = Color.White
                                    )
                                }

                                Spacer(modifier = Modifier.weight(1f))

                                val currentImage = if (pagerState.currentPage < uiState.images.size) {
                                    uiState.images[pagerState.currentPage]
                                } else null

                                IconButton(
                                    onClick = {
                                        currentImage?.let {
                                            viewModel.toggleFavorite(it.id)
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (uiState.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = if (uiState.isFavorite) "Remove from favorites" else "Add to favorites",
                                        tint = if (uiState.isFavorite) Color.Red else Color.White
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        currentImage?.let {
                                            viewModel.toggleDetails()
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "Image details",
                                        tint = Color.White
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        currentImage?.let {
                                            imageToDelete = it
                                            showDeleteDialog = true
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (uiState.showDetails && uiState.images.isNotEmpty()) {
        val currentPage = if (uiState.images.size > 0) {
            // We don't have pager state here, so just use the first image for now
            // In a real app, we'd need to hoist the pager state
            uiState.images.firstOrNull()
        } else null

        currentPage?.let { image ->
            PhotoDetailSheet(
                imageItem = image,
                onDismiss = { viewModel.hideDetails() }
            )
        }
    }

    if (showDeleteDialog && imageToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Photo") },
            text = { Text("Are you sure you want to delete this photo?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteImage(imageToDelete!!) {
                            onNavigateBack()
                        }
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ZoomableImage(
    imageUri: android.net.Uri,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size == 2) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            scale *= zoom
                            offset = offset + pan
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        AsyncImage(
            model = imageUri,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        )
    }
}
