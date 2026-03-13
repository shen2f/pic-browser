package com.example.picbrowser.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.picbrowser.data.model.ImageItem
import com.example.picbrowser.data.repository.FavoritesRepository
import com.example.picbrowser.data.repository.ImageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PhotoViewerUiState(
    val images: List<ImageItem> = emptyList(),
    val initialIndex: Int = 0,
    val isFavorite: Boolean = false,
    val isLoading: Boolean = true,
    val showDetails: Boolean = false
)

class PhotoViewerViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
    private val imageRepository: ImageRepository,
    private val favoritesRepository: FavoritesRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PhotoViewerUiState())
    val uiState: StateFlow<PhotoViewerUiState> = _uiState.asStateFlow()

    private val folderId: Long? = savedStateHandle["folderId"]
    private val initialImageId: Long = savedStateHandle["imageId"] ?: 0L
    private val showFavorites: Boolean = savedStateHandle["showFavorites"] ?: false

    init {
        loadImages()
    }

    private fun loadImages() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // Get all images first
            val allImages = if (folderId == null) {
                imageRepository.getAllImages()
            } else {
                imageRepository.getImagesByBucket(folderId)
            }

            // Then filter if needed
            val filteredImages = if (showFavorites) {
                val favoriteIds = favoritesRepository.getFavoriteIdsSync()
                allImages.filter { it.id in favoriteIds }
            } else {
                allImages
            }

            val initialIndex = filteredImages.indexOfFirst { it.id == initialImageId }.coerceAtLeast(0)

            _uiState.value = _uiState.value.copy(
                images = filteredImages,
                initialIndex = initialIndex,
                isLoading = false
            )

            if (filteredImages.isNotEmpty()) {
                checkFavorite(filteredImages[initialIndex].id)
            }
        }
    }

    fun checkFavorite(imageId: Long) {
        viewModelScope.launch {
            favoritesRepository.isFavoriteFlow(imageId).collect { isFavorite ->
                _uiState.value = _uiState.value.copy(isFavorite = isFavorite)
            }
        }
    }

    fun toggleFavorite(imageId: Long) {
        viewModelScope.launch {
            favoritesRepository.toggleFavorite(imageId)
        }
    }

    fun deleteImage(imageItem: ImageItem, onDeleted: () -> Unit) {
        viewModelScope.launch {
            val success = imageRepository.deleteImage(imageItem)
            if (success) {
                // Remove from local list
                val currentImages = _uiState.value.images.toMutableList()
                val index = currentImages.indexOfFirst { it.id == imageItem.id }
                if (index >= 0) {
                    currentImages.removeAt(index)
                    _uiState.value = _uiState.value.copy(images = currentImages)
                    if (currentImages.isEmpty()) {
                        onDeleted()
                    }
                }
            }
        }
    }

    fun toggleDetails() {
        _uiState.value = _uiState.value.copy(showDetails = !_uiState.value.showDetails)
    }

    fun hideDetails() {
        _uiState.value = _uiState.value.copy(showDetails = false)
    }

    companion object {
        fun Factory(
            application: Application,
            imageId: Long,
            folderId: Long?,
            showFavorites: Boolean
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val savedStateHandle = SavedStateHandle().apply {
                        set("imageId", imageId)
                        set("folderId", folderId)
                        set("showFavorites", showFavorites)
                    }
                    return PhotoViewerViewModel(
                        application,
                        savedStateHandle,
                        ImageRepository(application.contentResolver),
                        FavoritesRepository(application)
                    ) as T
                }
            }
        }
    }
}
