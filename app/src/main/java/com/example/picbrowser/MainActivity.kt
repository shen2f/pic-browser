package com.example.picbrowser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.picbrowser.ui.navigation.Screen
import com.example.picbrowser.ui.screens.FavoritesScreen
import com.example.picbrowser.ui.screens.GridScreen
import com.example.picbrowser.ui.screens.PhotoViewerScreen
import com.example.picbrowser.ui.theme.PicBrowserTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PicBrowserTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PicBrowserApp()
                }
            }
        }
    }
}

@Composable
fun PicBrowserApp(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Grid.route
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

            PhotoViewerScreen(
                imageId = imageId,
                folderId = folderId,
                showFavorites = showFavorites,
                directoryPath = directoryPath,
                onNavigateBack = { imageDeleted ->
                    if (imageDeleted) {
                        // 通知上一个页面需要刷新
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
                onNavigateBack = { navController.popBackStack() },
                shouldRefresh = backStackEntry.savedStateHandle.get<Boolean>("shouldRefresh") ?: false,
                onRefreshConsumed = {
                    backStackEntry.savedStateHandle["shouldRefresh"] = false
                }
            )
        }
    }
}
