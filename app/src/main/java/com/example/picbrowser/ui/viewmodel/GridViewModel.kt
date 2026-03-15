package com.example.picbrowser.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.picbrowser.data.model.CustomDirectory
import com.example.picbrowser.data.model.Folder
import com.example.picbrowser.data.model.ImageItem
import com.example.picbrowser.data.model.SortDirection
import com.example.picbrowser.data.model.SortType
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
    val isLandscape: Boolean = false,
    val favoriteIds: Set<Long> = emptySet(),
    val customDirectories: List<CustomDirectory> = emptyList(),
    val selectedDirectoryPath: String? = null,
    val sortType: SortType = SortType.DATE_MODIFIED,
    val sortDirection: SortDirection = SortDirection.DESCENDING
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

    fun setIsLandscape(isLandscape: Boolean) {
        viewModelScope.launch {
            val newColumns = if (isLandscape) {
                settingsRepository.getLandscapeColumns()
            } else {
                settingsRepository.getPortraitColumns()
            }
            _uiState.value = _uiState.value.copy(
                isLandscape = isLandscape,
                columns = newColumns
            )
        }
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
            val sortedImages = sortImages(images, _uiState.value.sortType, _uiState.value.sortDirection)
            _uiState.value = _uiState.value.copy(
                images = sortedImages,
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
        viewModelScope.launch {
            if (_uiState.value.isLandscape) {
                settingsRepository.saveLandscapeColumns(columns)
            } else {
                settingsRepository.savePortraitColumns(columns)
            }
        }
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
                removeImageFromMemory(imageItem.id)

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
                }
            }
        }
    }

    /**
     * 仅从内存列表中移除图片，不删除文件
     * 用于 PhotoViewer 删除图片后立即更新 Grid 列表
     */
    fun removeImageFromMemory(imageId: Long) {
        val currentImages = _uiState.value.images.toMutableList()
        val index = currentImages.indexOfFirst { it.id == imageId }
        if (index >= 0) {
            currentImages.removeAt(index)
            _uiState.value = _uiState.value.copy(images = currentImages)
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
            val sortedImages = sortImages(images, _uiState.value.sortType, _uiState.value.sortDirection)
            _uiState.value = _uiState.value.copy(
                images = sortedImages,
                isLoading = false
            )
        }
    }

    fun isCustomDirectorySelected(path: String): Boolean {
        return _uiState.value.selectedDirectoryPath == path
    }

    fun setSortType(sortType: SortType) {
        _uiState.value = _uiState.value.copy(sortType = sortType)
        val sortedImages = sortImages(_uiState.value.images, sortType, _uiState.value.sortDirection)
        _uiState.value = _uiState.value.copy(images = sortedImages)
    }

    fun toggleSortDirection() {
        val newDirection = if (_uiState.value.sortDirection == SortDirection.ASCENDING) {
            SortDirection.DESCENDING
        } else {
            SortDirection.ASCENDING
        }
        _uiState.value = _uiState.value.copy(sortDirection = newDirection)
        val sortedImages = sortImages(_uiState.value.images, _uiState.value.sortType, newDirection)
        _uiState.value = _uiState.value.copy(images = sortedImages)
    }

    private fun sortImages(
        images: List<ImageItem>,
        sortType: SortType,
        sortDirection: SortDirection
    ): List<ImageItem> {
        val comparator: Comparator<ImageItem> = when (sortType) {
            SortType.DATE_TAKEN -> compareBy { it.dateTaken }
            SortType.DATE_MODIFIED -> compareBy { it.dateModified }
            SortType.NAME -> compareBy { it.displayName.lowercase() }
            SortType.SIZE -> compareBy { it.size }
        }

        return if (sortDirection == SortDirection.DESCENDING) {
            images.sortedWith(comparator.reversed())
        } else {
            images.sortedWith(comparator)
        }
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
