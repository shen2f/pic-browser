package com.example.picbrowser.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.example.picbrowser.ui.viewmodel.SharedTransitionViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.app.Application
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.picbrowser.data.model.ImageItem
import com.example.picbrowser.ui.components.ColumnSizeSelector
import com.example.picbrowser.ui.components.DirectoryPicker
import com.example.picbrowser.ui.components.FolderDrawer
import com.example.picbrowser.ui.components.ImageGridItem
import com.example.picbrowser.ui.components.SortSelector
import com.example.picbrowser.ui.viewmodel.GridViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GridScreen(
    onImageClick: (Long, Long?, String?) -> Unit,
    onNavigateToFavorites: () -> Unit,
    sharedViewModel: SharedTransitionViewModel? = null,
    gridState: LazyGridState = rememberLazyGridState(),
    deletedImageId: Long? = null,
    onRefreshConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val viewModel: GridViewModel = viewModel(
        factory = GridViewModel.Factory(application)
    )
    val uiState by viewModel.uiState.collectAsState()
    val drawerState = rememberDrawerState(androidx.compose.material3.DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    // 监听屏幕方向变化
    androidx.compose.runtime.LaunchedEffect(isLandscape) {
        viewModel.setIsLandscape(isLandscape)
    }

    val transitionState by sharedViewModel?.state?.collectAsState() ?: remember { mutableStateOf(null) }
    val thumbnailPositions = remember { mutableMapOf<Long, androidx.compose.ui.geometry.Rect>() }

    // 当有图片从 PhotoViewer 删除时，立即从内存列表中移除
    LaunchedEffect(deletedImageId) {
        deletedImageId?.let { id ->
            viewModel.removeImageFromMemory(id)
            onRefreshConsumed()
        }
    }

    LaunchedEffect(transitionState?.targetImageId, uiState.images) {
        val targetId = transitionState?.targetImageId ?: return@LaunchedEffect
        val index = uiState.images.indexOfFirst { it.id == targetId }
        if (index >= 0) {
            gridState.animateScrollToItem(index)
        }
    }

    var hasPermission by remember { mutableStateOf(false) }
    var longPressedImage by remember { mutableStateOf<ImageItem?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var imageToDelete by remember { mutableStateOf<ImageItem?>(null) }
    var showDirectoryPicker by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) {
            viewModel.loadFolders()
        }
    }

    LaunchedEffect(Unit) {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        hasPermission = ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            permissionLauncher.launch(permission)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            FolderDrawer(
                folders = uiState.folders,
                selectedFolderId = uiState.selectedFolderId,
                customDirectories = uiState.customDirectories,
                selectedDirectoryPath = uiState.selectedDirectoryPath,
                onFolderSelected = { folderId ->
                    viewModel.loadImages(folderId)
                    scope.launch { drawerState.close() }
                },
                onFavoritesSelected = {
                    scope.launch { drawerState.close() }
                    onNavigateToFavorites()
                },
                onAllPhotosSelected = {
                    viewModel.loadImages(null)
                    scope.launch { drawerState.close() }
                },
                onAddDirectoryClicked = {
                    showDirectoryPicker = true
                },
                onDirectorySelected = { path ->
                    viewModel.loadImagesFromDirectory(path)
                    scope.launch { drawerState.close() }
                },
                onRemoveDirectory = { directoryId ->
                    viewModel.removeCustomDirectory(directoryId)
                }
            )
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 左侧：菜单按钮 + 文件夹名称
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Open drawer")
                            }
                            val selectedFolder = uiState.folders.find { it.id == uiState.selectedFolderId }
                            val selectedDirectory = uiState.customDirectories.find { it.path == uiState.selectedDirectoryPath }
                            Text(
                                selectedDirectory?.name
                                    ?: selectedFolder?.name
                                    ?: "All Photos",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            // 中间：排序选择器
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                SortSelector(
                                    sortType = uiState.sortType,
                                    sortDirection = uiState.sortDirection,
                                    onSortTypeChanged = { viewModel.setSortType(it) },
                                    onSortDirectionToggled = { viewModel.toggleSortDirection() }
                                )
                            }

                            // 右侧：列数选择器
                            ColumnSizeSelector(
                                currentColumns = uiState.columns,
                                onColumnsChange = { viewModel.setColumns(it) }
                            )
                        }
                    },
                    navigationIcon = {},
                    actions = {},
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when {
                    uiState.isLoading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    !hasPermission -> {
                        Text(
                            text = "Storage permission required",
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    uiState.images.isEmpty() -> {
                        Text(
                            text = "No images found",
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    else -> {
                        // Grid 始终保持完全可见，透明度变化由 PhotoViewer 的背景控制
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                        ) {
                            LazyVerticalGrid(
                                state = gridState,
                                columns = GridCells.Fixed(uiState.columns),
                                contentPadding = PaddingValues(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(uiState.images, key = { it.id }) { image ->
                                    ImageGridItem(
                                        uri = image.uri,
                                        isFavorite = viewModel.isFavorite(image.id),
                                        onClick = {
                                            // 点击时保存位置
                                            thumbnailPositions[image.id]?.let { rect ->
                                                sharedViewModel?.setTargetThumbnailRect(rect)
                                            }
                                            onImageClick(image.id, uiState.selectedFolderId, uiState.selectedDirectoryPath)
                                        },
                                        onLongClick = { longPressedImage = image },
                                        onPositioned = { rect ->
                                            thumbnailPositions[image.id] = rect
                                        },
                                        modifier = Modifier.animateItemPlacement()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    longPressedImage?.let { image ->
        GridLongPressMenu(
            image = image,
            isFavorite = viewModel.isFavorite(image.id),
            onDismiss = { longPressedImage = null },
            onToggleFavorite = {
                viewModel.toggleFavorite(image.id)
                longPressedImage = null
            },
            onDelete = {
                imageToDelete = image
                showDeleteDialog = true
                longPressedImage = null
            }
        )
    }

    if (showDeleteDialog && imageToDelete != null) {
        GridDeleteConfirmationDialog(
            onConfirm = {
                viewModel.deleteImage(imageToDelete!!)
                showDeleteDialog = false
                imageToDelete = null
            },
            onDismiss = {
                showDeleteDialog = false
                imageToDelete = null
            }
        )
    }

    if (showDirectoryPicker) {
        DirectoryPicker(
            onDirectorySelected = { path ->
                viewModel.addCustomDirectory(path)
                showDirectoryPicker = false
            },
            onDismiss = {
                showDirectoryPicker = false
            }
        )
    }
}

@Composable
fun GridLongPressMenu(
    image: ImageItem,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.Center)
    ) {
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = {
                showMenu = false
                onDismiss()
            }
        ) {
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isFavorite) "Remove from Favorites" else "Add to Favorites")
                    }
                },
                onClick = onToggleFavorite
            )
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete")
                    }
                },
                onClick = onDelete
            )
        }
    }
}

@Composable
fun GridDeleteConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Photo") },
        text = { Text("Are you sure you want to delete this photo?") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}