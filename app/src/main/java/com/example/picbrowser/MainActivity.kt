package com.example.picbrowser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.picbrowser.data.model.ImageItem
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
    navController: NavHostController = rememberNavController(),
    sharedViewModel: SharedTransitionViewModel = viewModel()
) {
    var showPhotoViewer by remember { mutableStateOf(false) }
    var photoViewerParams by remember { mutableStateOf<PhotoViewerParams?>(null) }
    var photoViewerKey by remember { mutableIntStateOf(0) }
    var deletedImageId by remember { mutableStateOf<Long?>(null) }
    val gridState = rememberLazyGridState()

    // 保留原来的导航用于 Favorites 页面
    var useLegacyNavigation by remember { mutableStateOf(false) }
    var navigateToFavorites by remember { mutableStateOf(false) }

    // 当进入 legacy 模式后，导航到 Favorites 页面
    LaunchedEffect(useLegacyNavigation) {
        if (useLegacyNavigation && navigateToFavorites) {
            navController.navigate(Screen.Favorites.route)
            navigateToFavorites = false
        }
    }

    if (useLegacyNavigation) {
        PicBrowserApp(
            navController = navController,
            onExitLegacyMode = { useLegacyNavigation = false }
        )
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            // Grid 在底层
            GridScreen(
                isPhotoViewerVisible = showPhotoViewer,
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
                    navigateToFavorites = true
                    useLegacyNavigation = true
                },
                sharedViewModel = sharedViewModel,
                gridState = gridState,
                deletedImageId = deletedImageId,
                onRefreshConsumed = {
                    deletedImageId = null
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
                                shuffleMode = params.shuffleMode,
                                sharedViewModel = sharedViewModel,
                                onNavigateBack = { deletedId ->
                                    if (deletedId != null) {
                                        deletedImageId = deletedId
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
                onDazzleMeClick = { _, _, _ -> },
                onNavigateToFavorites = {
                    navController.navigate(Screen.Favorites.route)
                },
                deletedImageId = backStackEntry.savedStateHandle.get<Long>("deletedImageId"),
                onRefreshConsumed = {
                    backStackEntry.savedStateHandle.remove<Long>("deletedImageId")
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
                onNavigateBack = { deletedId ->
                    if (deletedId != null) {
                        navController.previousBackStackEntry?.savedStateHandle?.set("deletedImageId", deletedId)
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
                deletedImageId = backStackEntry.savedStateHandle.get<Long>("deletedImageId"),
                onRefreshConsumed = {
                    backStackEntry.savedStateHandle.remove<Long>("deletedImageId")
                }
            )
        }
    }
}