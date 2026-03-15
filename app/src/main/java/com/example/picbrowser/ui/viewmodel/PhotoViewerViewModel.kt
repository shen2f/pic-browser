package com.example.picbrowser.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.picbrowser.data.model.ImageItem
import com.example.picbrowser.data.repository.ImageRepository
import com.example.picbrowser.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PhotoViewerUiState(
    val images: List<ImageItem> = emptyList(),
    val initialIndex: Int = 0,
    val currentIndex: Int = 0,
    val isFavorite: Boolean = false,
    val isLoading: Boolean = true,
    val showDetails: Boolean = false
)

class PhotoViewerViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
    private val imageRepository: ImageRepository,
    private val settingsRepository: SettingsRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PhotoViewerUiState())
    val uiState: StateFlow<PhotoViewerUiState> = _uiState.asStateFlow()

    private var folderId: Long? = savedStateHandle["folderId"]
    private var initialImageId: Long = savedStateHandle["imageId"] ?: 0L
    private var showFavorites: Boolean = savedStateHandle["showFavorites"] ?: false
    private var directoryPath: String? = savedStateHandle["directoryPath"]
    private var shuffledImages: List<ImageItem>? = savedStateHandle["shuffledImages"]

    init {
        loadImages()
    }

    fun reloadImages(
        newImageId: Long,
        newFolderId: Long?,
        newShowFavorites: Boolean,
        newDirectoryPath: String?,
        newShuffledImages: List<ImageItem>? = null
    ) {
        if (newImageId != initialImageId || newFolderId != folderId ||
            newShowFavorites != showFavorites || newDirectoryPath != directoryPath ||
            newShuffledImages != shuffledImages) {
            initialImageId = newImageId
            folderId = newFolderId
            showFavorites = newShowFavorites
            directoryPath = newDirectoryPath
            shuffledImages = newShuffledImages
            loadImages()
        }
    }

    private fun loadImages() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val imagesToUse = shuffledImages?.let { shuffled ->
                // Use provided shuffled list, apply favorites filter if needed
                if (showFavorites) {
                    val favoriteIds = settingsRepository.getFavoriteIdsSync()
                    shuffled.filter { it.id in favoriteIds }
                } else {
                    shuffled
                }
            } ?: run {
                // Normal loading path
                val dirPath = directoryPath
                val fId = folderId
                val allImages = when {
                    dirPath != null -> {
                        imageRepository.getImagesFromDirectory(dirPath)
                    }
                    fId == null -> {
                        imageRepository.getAllImages()
                    }
                    else -> {
                        imageRepository.getImagesByBucket(fId)
                    }
                }

                if (showFavorites) {
                    val favoriteIds = settingsRepository.getFavoriteIdsSync()
                    allImages.filter { it.id in favoriteIds }
                } else {
                    allImages
                }
            }

            val initialIndex = imagesToUse.indexOfFirst { it.id == initialImageId }.coerceAtLeast(0)

            _uiState.value = _uiState.value.copy(
                images = imagesToUse,
                initialIndex = initialIndex,
                currentIndex = initialIndex,
                isLoading = false
            )

            if (imagesToUse.isNotEmpty()) {
                checkFavorite(imagesToUse[initialIndex].id)
            }
        }
    }

    fun checkFavorite(imageId: Long) {
        viewModelScope.launch {
            settingsRepository.isFavoriteFlow(imageId).collect { isFavorite ->
                _uiState.value = _uiState.value.copy(isFavorite = isFavorite)
            }
        }
    }

    fun toggleFavorite(imageId: Long) {
        viewModelScope.launch {
            settingsRepository.toggleFavorite(imageId)
        }
    }

    fun deleteImage(imageItem: ImageItem, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = imageRepository.deleteImage(imageItem)
            if (success) {
                // Remove from local list
                val currentImages = _uiState.value.images.toMutableList()
                val index = currentImages.indexOfFirst { it.id == imageItem.id }
                if (index >= 0) {
                    currentImages.removeAt(index)
                    _uiState.value = _uiState.value.copy(images = currentImages)
                }
                onResult(true)
            } else {
                onResult(false)
            }
        }
    }

    fun toggleDetails() {
        _uiState.value = _uiState.value.copy(showDetails = !_uiState.value.showDetails)
    }

    fun hideDetails() {
        _uiState.value = _uiState.value.copy(showDetails = false)
    }

    fun setCurrentIndex(index: Int) {
        _uiState.value = _uiState.value.copy(currentIndex = index)
        if (index < _uiState.value.images.size) {
            checkFavorite(_uiState.value.images[index].id)
        }
    }

    companion object {
        fun Factory(
            application: Application,
            imageId: Long,
            folderId: Long?,
            showFavorites: Boolean,
            directoryPath: String? = null,
            shuffledImages: List<ImageItem>? = null
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val savedStateHandle = SavedStateHandle().apply {
                        set("imageId", imageId)
                        set("folderId", folderId)
                        set("showFavorites", showFavorites)
                        set("directoryPath", directoryPath)
                        set("shuffledImages", shuffledImages)
                    }
                    return PhotoViewerViewModel(
                        application,
                        savedStateHandle,
                        ImageRepository(application.contentResolver),
                        SettingsRepository(application)
                    ) as T
                }
            }
        }
    }
}
