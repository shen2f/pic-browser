package com.example.picbrowser.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.picbrowser.data.model.ImageItem
import com.example.picbrowser.ui.components.CustomPhotoPager
import com.example.picbrowser.ui.components.PhotoDetailSheet
import com.example.picbrowser.ui.components.ZoomableImage
import com.example.picbrowser.ui.viewmodel.PhotoViewerViewModel
import com.example.picbrowser.ui.viewmodel.SharedTransitionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoViewerScreen(
    imageId: Long,
    folderId: Long?,
    showFavorites: Boolean = false,
    directoryPath: String? = null,
    sharedViewModel: SharedTransitionViewModel,
    onNavigateBack: (Long?) -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val viewModel: PhotoViewerViewModel = viewModel(
        factory = PhotoViewerViewModel.Factory(application, imageId, folderId, showFavorites, directoryPath)
    )
    val uiState by viewModel.uiState.collectAsState()
    val transitionState by sharedViewModel.state.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var imageToDelete by remember { mutableStateOf<ImageItem?>(null) }
    var deletedImageId by remember { mutableStateOf<Long?>(null) }
    var showMenuBar by remember { mutableStateOf(true) }

    LaunchedEffect(imageId, folderId, showFavorites, directoryPath) {
        viewModel.reloadImages(imageId, folderId, showFavorites, directoryPath)
    }

    LaunchedEffect(uiState.images, uiState.currentIndex) {
        if (uiState.images.isNotEmpty() && uiState.currentIndex < uiState.images.size) {
            sharedViewModel.setTargetImageId(uiState.images[uiState.currentIndex].id)
        }
    }

    // 根据拖动状态和菜单显示状态决定透明度，相邻页面始终显示以避免拖动时的重组卡顿
    val maxDragOffset = with(LocalDensity.current) { 50.dp.toPx() }
    val dragAlpha = 1f - (transitionState.dragOffset / maxDragOffset).coerceIn(0f, 1f)
    val topBarAlpha = if (showMenuBar) dragAlpha else 0f
    val showAdjacentPages = true

    Box(
        modifier = Modifier.fillMaxSize()
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
                Box(modifier = Modifier.fillMaxSize()) {
                    CustomPhotoPager(
                        pageCount = uiState.images.size,
                        initialPage = uiState.initialIndex,
                        showAdjacentPages = showAdjacentPages,
                        isDragging = transitionState.isDragging,
                        dragOffset = transitionState.dragOffset,
                        onPageChanged = { page ->
                            viewModel.setCurrentIndex(page)
                            if (page < uiState.images.size) {
                                sharedViewModel.setTargetImageId(uiState.images[page].id)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    ) { page, onScaleChanged, onHorizontalDrag, onHorizontalDragEnd ->
                        val image = uiState.images[page]
                        ZoomableImage(
                            imageUri = image.uri,
                            modifier = Modifier.fillMaxSize(),
                            onScaleChanged = onScaleChanged,
                            onHorizontalDrag = onHorizontalDrag,
                            onHorizontalDragEnd = onHorizontalDragEnd,
                            onVerticalDragStart = {
                                sharedViewModel.setDragging(true)
                            },
                            onVerticalDrag = { deltaY, totalY ->
                                sharedViewModel.updateDragOffset(totalY)
                            },
                            onVerticalDragEnd = { shouldDismiss, totalY ->
                                sharedViewModel.setDragging(false)
                                if (shouldDismiss) {
                                    sharedViewModel.startDismiss()
                                }
                            },
                            onDismiss = { },
                            onShowDetails = { viewModel.toggleDetails() },
                            onSingleTap = { showMenuBar = !showMenuBar }
                        )
                    }

                    // Top Bar overlay - 使用 alpha 渐变避免节点移除导致的手势卡顿
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .background(Color.Black.copy(alpha = 0f))
                            .systemBarsPadding()
                            .graphicsLayer {
                                alpha = topBarAlpha
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { onNavigateBack(deletedImageId) }) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White
                                )
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            val currentImage = if (uiState.currentIndex < uiState.images.size) {
                                uiState.images[uiState.currentIndex]
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

    if (uiState.showDetails && uiState.images.isNotEmpty()) {
        val currentImage = if (uiState.currentIndex < uiState.images.size) {
            uiState.images[uiState.currentIndex]
        } else null

        currentImage?.let { image ->
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
                        viewModel.deleteImage(imageToDelete!!) { success ->
                            if (success) {
                                deletedImageId = imageToDelete!!.id
                                val currentImages = viewModel.uiState.value.images
                                if (currentImages.isEmpty()) {
                                    onNavigateBack(deletedImageId)
                                }
                            }
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
