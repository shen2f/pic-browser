package com.example.picbrowser.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.picbrowser.data.model.ImageItem
import com.example.picbrowser.data.repository.ImageRepository
import com.example.picbrowser.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class FavoritesUiState(
    val images: List<ImageItem> = emptyList(),
    val isLoading: Boolean = true,
    val columns: Int = 3,
    val favoriteIds: Set<Long> = emptySet()
)

class FavoritesViewModel(
    application: Application,
    private val imageRepository: ImageRepository,
    private val settingsRepository: SettingsRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> = _uiState.asStateFlow()

    init {
        loadFavorites()
    }

    fun loadFavorites() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // Load favorite IDs and all images
            val favoriteIds = settingsRepository.favoriteIds.first()
            val allImages = imageRepository.getAllImages()
            val favoriteImages = allImages.filter { it.id in favoriteIds }

            _uiState.value = _uiState.value.copy(
                images = favoriteImages,
                favoriteIds = favoriteIds,
                isLoading = false
            )
        }
    }

    fun setColumns(columns: Int) {
        _uiState.value = _uiState.value.copy(columns = columns)
    }

    fun toggleFavorite(imageId: Long) {
        viewModelScope.launch {
            settingsRepository.toggleFavorite(imageId)
            // Reload after toggle
            loadFavorites()
        }
    }

    fun deleteImage(imageItem: ImageItem) {
        viewModelScope.launch {
            val success = imageRepository.deleteImage(imageItem)
            if (success) {
                loadFavorites()
            }
        }
    }

    fun isFavorite(imageId: Long): Boolean {
        return _uiState.value.favoriteIds.contains(imageId)
    }

    companion object {
        fun Factory(application: Application): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return FavoritesViewModel(
                        application,
                        ImageRepository(application.contentResolver),
                        SettingsRepository(application)
                    ) as T
                }
            }
        }
    }
}