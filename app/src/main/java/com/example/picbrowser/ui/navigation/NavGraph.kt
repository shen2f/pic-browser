package com.example.picbrowser.ui.navigation

sealed class Screen(val route: String) {
    object Grid : Screen("grid")
    object PhotoViewer : Screen("photo_viewer")
    object Favorites : Screen("favorites")
}
