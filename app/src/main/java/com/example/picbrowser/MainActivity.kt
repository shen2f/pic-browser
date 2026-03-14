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
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import com.example.picbrowser.ui.components.PhotoViewerOverlay
import com.example.picbrowser.ui.navigation.Screen
import com.example.picbrowser.ui.screens.FavoritesScreen
import com.example.picbrowser.ui.screens.GridScreen
import com.example.picbrowser.ui.screens.PhotoViewerScreen
import com.example.picbrowser.ui.theme.PicBrowserTheme
import com.example.picbrowser.ui.viewmodel.SharedTransitionViewModel

data class PhotoViewerParams(
    val imageId: Long,
    val folderId: Long?,
    val showFavorites: Boolean,
    val directoryPath: String?
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
    navController: NavHostController = rememberNavController(),
    sharedViewModel: SharedTransitionViewModel = viewModel()
) {
    var showPhotoViewer by remember { mutableStateOf(false) }
    var photoViewerParams by remember { mutableStateOf<PhotoViewerParams?>(null) }
    var photoViewerKey by remember { mutableIntStateOf(0) }
    var shouldRefreshGrid by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()

    // 保留原来的导航用于 Favorites 页面
    var useLegacyNavigation by remember { mutableStateOf(false) }

    if (useLegacyNavigation) {
        PicBrowserApp(
            navController = navController,
            onExitLegacyMode = { useLegacyNavigation = false }
        )
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            // Grid 在底层
            GridScreen(
                sharedViewModel = sharedViewModel,
                gridState = gridState,
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
                onNavigateToFavorites = {
                    useLegacyNavigation = true
                    navController.navigate(Screen.Favorites.route)
                },
                shouldRefresh = shouldRefreshGrid,
                onRefreshConsumed = {
                    shouldRefreshGrid = false
                }
            )

            // PhotoViewer 在上层
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
                                sharedViewModel = sharedViewModel,
                                onNavigateBack = { imageDeleted ->
                                    if (imageDeleted) {
                                        shouldRefreshGrid = true
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
}

@Composable
fun PicBrowserApp(
    navController: NavHostController = rememberNavController(),
    onExitLegacyMode: () -> Unit = {}
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Favorites.route
    ) {
        composable(Screen.Grid.route) { backStackEntry ->
            GridScreen(
                onImageClick = { imageId, folderId, directoryPath ->
                    val folderIdParam = folderId?.toString() ?: "-1"
                    val dirPathParam = directoryPath?.let { android.net.Uri.encode(it) } ?: ""
                    navController.navigate("${Screen.PhotoViewer.route}/$imageId?folderId=$folderIdParam&showFavorites=false&directoryPath=$dirPathParam")
                },
                onNavigateToFavorites = {
                    navController.navigate(Screen.Favorites.route)
                },
                shouldRefresh = backStackEntry.savedStateHandle.get<Boolean>("shouldRefresh") ?: false,
                onRefreshConsumed = {
                    backStackEntry.savedStateHandle["shouldRefresh"] = false
                }
            )
        }

        composable(
            route = "${Screen.PhotoViewer.route}/{imageId}?folderId={folderId}&showFavorites={showFavorites}&directoryPath={directoryPath}",
            arguments = listOf(
                navArgument("imageId") { type = NavType.LongType },
                navArgument("folderId") {
                    type = NavType.LongType
                    defaultValue = -1L
                },
                navArgument("showFavorites") {
                    type = NavType.BoolType
                    defaultValue = false
                },
                navArgument("directoryPath") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val imageId = backStackEntry.arguments?.getLong("imageId") ?: 0L
            val folderIdArg = backStackEntry.arguments?.getLong("folderId")
            val folderId = if (folderIdArg == -1L) null else folderIdArg
            val showFavorites = backStackEntry.arguments?.getBoolean("showFavorites") ?: false
            val directoryPath = backStackEntry.arguments?.getString("directoryPath")
                ?.takeIf { it.isNotEmpty() }
                ?.let { android.net.Uri.decode(it) }

            // 这里需要一个临时的 SharedViewModel，但旧导航模式下用原来的方式
            val tempViewModel: SharedTransitionViewModel = viewModel()
            PhotoViewerScreen(
                imageId = imageId,
                folderId = folderId,
                showFavorites = showFavorites,
                directoryPath = directoryPath,
                sharedViewModel = tempViewModel,
                onNavigateBack = { imageDeleted ->
                    if (imageDeleted) {
                        navController.previousBackStackEntry?.savedStateHandle?.set("shouldRefresh", true)
                    }
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Favorites.route) { backStackEntry ->
            FavoritesScreen(
                onImageClick = { imageId ->
                    navController.navigate("${Screen.PhotoViewer.route}/$imageId?folderId=-1&showFavorites=true&directoryPath=")
                },
                onNavigateBack = {
                    if (navController.previousBackStackEntry == null) {
                        onExitLegacyMode()
                    } else {
                        navController.popBackStack()
                    }
                },
                shouldRefresh = backStackEntry.savedStateHandle.get<Boolean>("shouldRefresh") ?: false,
                onRefreshConsumed = {
                    backStackEntry.savedStateHandle["shouldRefresh"] = false
                }
            )
        }
    }
}