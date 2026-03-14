package com.example.picbrowser.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.picbrowser.data.model.CustomDirectory
import com.example.picbrowser.data.model.Folder
import com.example.picbrowser.data.model.ImageItem
import com.example.picbrowser.data.repository.ImageRepository
import com.example.picbrowser.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GridUiState(
    val folders: List<Folder> = emptyList(),
    val images: List<ImageItem> = emptyList(),
    val selectedFolderId: Long? = null,
    val isLoading: Boolean = true,
    val columns: Int = 3,
    val favoriteIds: Set<Long> = emptySet(),
    val customDirectories: List<CustomDirectory> = emptyList(),
    val selectedDirectoryPath: String? = null
)

class GridViewModel(
    application: Application,
    private val imageRepository: ImageRepository,
    private val settingsRepository: SettingsRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(GridUiState())
    val uiState: StateFlow<GridUiState> = _uiState.asStateFlow()

    init {
        loadFolders()
        loadFavorites()
        loadCustomDirectories()
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
            if (_uiState.value.selectedFolderId == null && _uiState.value.selectedDirectoryPath == null) {
                loadImages(null)
            }
        }
    }

    fun loadImages(folderId: Long?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                selectedFolderId = folderId,
                selectedDirectoryPath = null
            )
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
            settingsRepository.favoriteIds.collect { favoriteIds ->
                _uiState.value = _uiState.value.copy(favoriteIds = favoriteIds)
            }
        }
    }

    private fun loadCustomDirectories() {
        viewModelScope.launch {
            settingsRepository.customDirectories.collect { directories ->
                _uiState.value = _uiState.value.copy(customDirectories = directories)
            }
        }
    }

    fun setColumns(columns: Int) {
        _uiState.value = _uiState.value.copy(columns = columns)
    }

    fun toggleFavorite(imageId: Long) {
        viewModelScope.launch {
            settingsRepository.toggleFavorite(imageId)
        }
    }

    fun deleteImage(imageItem: ImageItem) {
        viewModelScope.launch {
            val success = imageRepository.deleteImage(imageItem)
            if (success) {
                // 直接从当前列表中移除该图片，立即刷新 UI
                val currentImages = _uiState.value.images.toMutableList()
                val index = currentImages.indexOfFirst { it.id == imageItem.id }
                if (index >= 0) {
                    currentImages.removeAt(index)
                    _uiState.value = _uiState.value.copy(images = currentImages)
                }

                // 如果是在自定义目录中，同时更新该自定义目录的信息
                val currentPath = _uiState.value.selectedDirectoryPath
                if (currentPath != null) {
                    // 短暂延迟后更新目录信息
                    kotlinx.coroutines.delay(200)
                    imageRepository.scanDirectory(currentPath)?.let { updatedDir ->
                        val existingDir = _uiState.value.customDirectories.find { it.path == currentPath }
                        if (existingDir != null) {
                            settingsRepository.updateCustomDirectory(updatedDir.copy(id = existingDir.id))
                        }
                    }
                } else {
                    // 否则重新加载文件夹
                    loadFolders()
                }
            }
        }
    }

    fun isFavorite(imageId: Long): Boolean {
        return _uiState.value.favoriteIds.contains(imageId)
    }

    fun addCustomDirectory(path: String) {
        android.util.Log.d("GridViewModel", "addCustomDirectory called with path: $path")
        viewModelScope.launch {
            android.util.Log.d("GridViewModel", "About to scanDirectory...")
            val directory = imageRepository.scanDirectory(path)
            android.util.Log.d("GridViewModel", "scanDirectory result: $directory")
            directory?.let {
                android.util.Log.d("GridViewModel", "Adding directory to settings: ${it.name}, ${it.imageCount} photos")
                settingsRepository.addCustomDirectory(it)
            }
        }
    }

    fun removeCustomDirectory(directoryId: Long) {
        viewModelScope.launch {
            settingsRepository.removeCustomDirectory(directoryId)
            // 如果正在浏览被删除的目录，切换回所有照片
            if (_uiState.value.selectedDirectoryPath != null) {
                val dir = _uiState.value.customDirectories.find { it.id == directoryId }
                if (dir?.path == _uiState.value.selectedDirectoryPath) {
                    loadImages(null)
                }
            }
        }
    }

    fun loadImagesFromDirectory(path: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                selectedFolderId = null,
                selectedDirectoryPath = path
            )
            val images = imageRepository.getImagesFromDirectory(path)
            _uiState.value = _uiState.value.copy(
                images = images,
                isLoading = false
            )
        }
    }

    fun isCustomDirectorySelected(path: String): Boolean {
        return _uiState.value.selectedDirectoryPath == path
    }

    companion object {
        fun Factory(application: Application): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return GridViewModel(
                        application,
                        ImageRepository(application.contentResolver),
                        SettingsRepository(application)
                    ) as T
                }
            }
        }
    }
}
