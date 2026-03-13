package com.example.picbrowser.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.picbrowser.data.model.Folder
import com.example.picbrowser.data.model.ImageItem
import com.example.picbrowser.data.repository.FavoritesRepository
import com.example.picbrowser.data.repository.ImageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class GridUiState(
    val folders: List<Folder> = emptyList(),
    val images: List<ImageItem> = emptyList(),
    val selectedFolderId: Long? = null,
    val isLoading: Boolean = true,
    val columns: Int = 3,
    val favoriteIds: Set<Long> = emptySet()
)

class GridViewModel(
    application: Application,
    private val imageRepository: ImageRepository,
    private val favoritesRepository: FavoritesRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(GridUiState())
    val uiState: StateFlow<GridUiState> = _uiState.asStateFlow()

    init {
        loadFolders()
        loadFavorites()
    }

    fun loadFolders() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val folders = imageRepository.getFolders()
            _uiState.value = _uiState.value.copy(
                folders = folders,
                isLoading = false
            )
            // Load all images by default if no folder selected
            if (_uiState.value.selectedFolderId == null) {
                loadImages(null)
            }
        }
    }

    fun loadImages(folderId: Long?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, selectedFolderId = folderId)
            val images = if (folderId == null) {
                imageRepository.getAllImages()
            } else {
                imageRepository.getImagesByBucket(folderId)
            }
            _uiState.value = _uiState.value.copy(
                images = images,
                isLoading = false
            )
        }
    }

    private fun loadFavorites() {
        viewModelScope.launch {
            favoritesRepository.favoriteIds.collect { favoriteIds ->
                _uiState.value = _uiState.value.copy(favoriteIds = favoriteIds)
            }
        }
    }

    fun setColumns(columns: Int) {
        _uiState.value = _uiState.value.copy(columns = columns)
    }

    fun toggleFavorite(imageId: Long) {
        viewModelScope.launch {
            favoritesRepository.toggleFavorite(imageId)
        }
    }

    fun deleteImage(imageItem: ImageItem) {
        viewModelScope.launch {
            val success = imageRepository.deleteImage(imageItem)
            if (success) {
                // Reload images and folders
                loadFolders()
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
                    return GridViewModel(
                        application,
                        ImageRepository(application.contentResolver),
                        FavoritesRepository(application)
                    ) as T
                }
            }
        }
    }
}
