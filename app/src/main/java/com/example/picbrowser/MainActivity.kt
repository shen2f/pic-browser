package com.example.picbrowser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import com.example.picbrowser.ui.components.PhotoViewerOverlay
import com.example.picbrowser.ui.screens.FavoritesScreen
import com.example.picbrowser.ui.screens.GridScreen
import com.example.picbrowser.ui.screens.PhotoViewerScreen
import com.example.picbrowser.ui.theme.PicBrowserTheme
import com.example.picbrowser.ui.viewmodel.SharedTransitionViewModel

data class PhotoViewerParams(
    val imageId: Long,
    val folderId: Long?,
    val showFavorites: Boolean,
    val directoryPath: String?,
    val shuffleMode: Boolean = false
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PicBrowserTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PicBrowserOverlayApp()
                }
            }
        }
    }
}

@Composable
fun PicBrowserOverlayApp(
    sharedViewModel: SharedTransitionViewModel = viewModel()
) {
    var showPhotoViewer by remember { mutableStateOf(false) }
    var photoViewerParams by remember { mutableStateOf<PhotoViewerParams?>(null) }
    var photoViewerKey by remember { mutableIntStateOf(0) }
    var deletedImageId by remember { mutableStateOf<Long?>(null) }
    val gridState = rememberLazyGridState()

    // 显示 Favorites 页面
    var showFavorites by remember { mutableStateOf(false) }
    var favoritesDeletedImageId by remember { mutableStateOf<Long?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Grid 在底层
        GridScreen(
            isPhotoViewerVisible = showPhotoViewer || showFavorites,
            onImageClick = { imageId, folderId, directoryPath ->
                photoViewerParams = PhotoViewerParams(
                    imageId = imageId,
                    folderId = folderId,
                    showFavorites = false,
                    directoryPath = directoryPath
                )
                photoViewerKey++
                sharedViewModel.reset()
                showPhotoViewer = true
            },
            onDazzleMeClick = { startImageId, folderId, directoryPath ->
                photoViewerParams = PhotoViewerParams(
                    imageId = startImageId,
                    folderId = folderId,
                    showFavorites = false,
                    directoryPath = directoryPath,
                    shuffleMode = true
                )
                photoViewerKey++
                sharedViewModel.reset()
                showPhotoViewer = true
            },
            onNavigateToFavorites = {
                showFavorites = true
            },
            sharedViewModel = sharedViewModel,
            gridState = gridState,
            deletedImageId = deletedImageId,
            onRefreshConsumed = {
                deletedImageId = null
            }
        )

        // Favorites 在上层
        if (showFavorites) {
            FavoritesScreen(
                onImageClick = { imageId ->
                    photoViewerParams = PhotoViewerParams(
                        imageId = imageId,
                        folderId = null,
                        showFavorites = true,
                        directoryPath = null
                    )
                    photoViewerKey++
                    sharedViewModel.reset()
                    showPhotoViewer = true
                },
                onNavigateBack = {
                    showFavorites = false
                },
                deletedImageId = favoritesDeletedImageId,
                onRefreshConsumed = {
                    favoritesDeletedImageId = null
                }
            )
        }

        // PhotoViewer 在最上层
        if (showPhotoViewer) {
            val params = photoViewerParams
            if (params != null) {
                PhotoViewerOverlay(
                    sharedViewModel = sharedViewModel,
                    onDismissComplete = {
                        showPhotoViewer = false
                        sharedViewModel.reset()
                    }
                ) {
                    // 使用 key 强制重新创建 PhotoViewerScreen
                    androidx.compose.runtime.key(photoViewerKey) {
                        PhotoViewerScreen(
                            imageId = params.imageId,
                            folderId = params.folderId,
                            showFavorites = params.showFavorites,
                            directoryPath = params.directoryPath,
                            shuffleMode = params.shuffleMode,
                            sharedViewModel = sharedViewModel,
                            onNavigateBack = { deletedId ->
                                if (deletedId != null) {
                                    if (params.showFavorites) {
                                        favoritesDeletedImageId = deletedId
                                    } else {
                                        deletedImageId = deletedId
                                    }
                                }
                                showPhotoViewer = false
                                sharedViewModel.reset()
                            }
                        )
                    }
                }
            }
        }
    }
}