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
import com.example.picbrowser.util.shuffledRandom
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
    private var shuffleMode: Boolean = savedStateHandle["shuffleMode"] ?: false

    init {
        loadImages()
    }

    fun reloadImages(
        newImageId: Long,
        newFolderId: Long?,
        newShowFavorites: Boolean,
        newDirectoryPath: String?,
        newShuffleMode: Boolean = false
    ) {
        if (newImageId != initialImageId || newFolderId != folderId ||
            newShowFavorites != showFavorites || newDirectoryPath != directoryPath ||
            newShuffleMode != shuffleMode) {
            initialImageId = newImageId
            folderId = newFolderId
            showFavorites = newShowFavorites
            directoryPath = newDirectoryPath
            shuffleMode = newShuffleMode
            loadImages()
        }
    }

    private fun loadImages() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

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

            // Apply favorites filter if needed
            val filteredImages = if (showFavorites) {
                val favoriteIds = settingsRepository.getFavoriteIdsSync()
                allImages.filter { it.id in favoriteIds }
            } else {
                allImages
            }

            // If shuffle mode, shuffle the list and put initial image first
            val imagesToUse = if (shuffleMode) {
                val startImage = filteredImages.find { it.id == initialImageId }
                val shuffled = filteredImages.shuffledRandom()
                if (startImage != null) {
                    val mutableList = shuffled.toMutableList()
                    mutableList.remove(startImage)
                    mutableList.add(0, startImage)
                    mutableList
                } else {
                    shuffled
                }
            } else {
                filteredImages
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
            shuffleMode: Boolean = false
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val savedStateHandle = SavedStateHandle().apply {
                        set("imageId", imageId)
                        set("folderId", folderId)
                        set("showFavorites", showFavorites)
                        set("directoryPath", directoryPath)
                        set("shuffleMode", shuffleMode)
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