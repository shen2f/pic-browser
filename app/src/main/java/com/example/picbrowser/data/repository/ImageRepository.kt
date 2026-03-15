package com.example.picbrowser.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.provider.MediaStore
import com.example.picbrowser.data.model.Folder
import com.example.picbrowser.data.model.ImageItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.net.Uri

class ImageRepository(private val contentResolver: ContentResolver) {

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp")

    suspend fun getFolders(): List<Folder> = withContext(Dispatchers.IO) {
        val folders = mutableMapOf<Long, Folder>()
        val projection = arrayOf(
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED
        )

        // Remove DATE_TAKEN IS NOT NULL constraint to include all images
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val bucketNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)

            val bucketImageCounts = mutableMapOf<Long, Int>()

            // First pass: count images per bucket
            while (cursor.moveToNext()) {
                val bucketId = cursor.getLong(bucketIdColumn)
                bucketImageCounts[bucketId] = (bucketImageCounts[bucketId] ?: 0) + 1
            }

            // Second pass: get first image as cover for each bucket
            cursor.moveToPosition(-1)
            while (cursor.moveToNext()) {
                val bucketId = cursor.getLong(bucketIdColumn)
                if (!folders.containsKey(bucketId)) {
                    val bucketName = cursor.getString(bucketNameColumn) ?: "Unknown"
                    val imageId = cursor.getLong(idColumn)
                    val coverUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        imageId
                    )
                    folders[bucketId] = Folder(
                        id = bucketId,
                        name = bucketName,
                        coverUri = coverUri,
                        imageCount = bucketImageCounts[bucketId] ?: 0
                    )
                }
            }
        }

        folders.values.toList().sortedByDescending { it.imageCount }
    }

    suspend fun getImagesByBucket(bucketId: Long): List<ImageItem> = withContext(Dispatchers.IO) {
        val images = mutableListOf<ImageItem>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )

        val selection = "${MediaStore.Images.Media.BUCKET_ID} = ?"
        val selectionArgs = arrayOf(bucketId.toString())
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateTakenColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val bucketNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                val dateTakenRaw = cursor.getLong(dateTakenColumn)
                val dateModifiedRaw = cursor.getLong(dateModifiedColumn) * 1000  // DATE_MODIFIED 是秒，转毫秒
                val dateAddedRaw = cursor.getLong(dateAddedColumn) * 1000      // DATE_ADDED 是秒，转毫秒

                // 拍摄时间 fallback 策略: DATE_TAKEN -> DATE_MODIFIED -> DATE_ADDED
                val finalDateTaken = when {
                    dateTakenRaw > 0 -> dateTakenRaw
                    dateModifiedRaw > 0 -> dateModifiedRaw
                    else -> dateAddedRaw
                }

                images.add(
                    ImageItem(
                        id = id,
                        uri = uri,
                        displayName = cursor.getString(displayNameColumn) ?: "",
                        dateTaken = finalDateTaken,
                        dateModified = dateModifiedRaw,
                        size = cursor.getLong(sizeColumn),
                        width = cursor.getInt(widthColumn),
                        height = cursor.getInt(heightColumn),
                        bucketId = cursor.getLong(bucketIdColumn),
                        bucketName = cursor.getString(bucketNameColumn) ?: ""
                    )
                )
            }
        }

        images
    }

    suspend fun getAllImages(): List<ImageItem> = withContext(Dispatchers.IO) {
        val images = mutableListOf<ImageItem>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateTakenColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val bucketNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                val dateTakenRaw = cursor.getLong(dateTakenColumn)
                val dateModifiedRaw = cursor.getLong(dateModifiedColumn) * 1000  // DATE_MODIFIED 是秒，转毫秒
                val dateAddedRaw = cursor.getLong(dateAddedColumn) * 1000      // DATE_ADDED 是秒，转毫秒

                // 拍摄时间 fallback 策略: DATE_TAKEN -> DATE_MODIFIED -> DATE_ADDED
                val finalDateTaken = when {
                    dateTakenRaw > 0 -> dateTakenRaw
                    dateModifiedRaw > 0 -> dateModifiedRaw
                    else -> dateAddedRaw
                }

                images.add(
                    ImageItem(
                        id = id,
                        uri = uri,
                        displayName = cursor.getString(displayNameColumn) ?: "",
                        dateTaken = finalDateTaken,
                        dateModified = dateModifiedRaw,
                        size = cursor.getLong(sizeColumn),
                        width = cursor.getInt(widthColumn),
                        height = cursor.getInt(heightColumn),
                        bucketId = cursor.getLong(bucketIdColumn),
                        bucketName = cursor.getString(bucketNameColumn) ?: ""
                    )
                )
            }
        }

        images
    }

    suspend fun deleteImage(imageItem: ImageItem): Boolean = withContext(Dispatchers.IO) {
        try {
            val uri = imageItem.uri
            var deleted = false
            if (uri.scheme == "file") {
                // 对于 file:// 格式的 Uri，直接删除文件
                val filePath = uri.path
                if (filePath != null) {
                    val file = java.io.File(filePath)
                    if (file.exists()) {
                        deleted = file.delete()
                    }
                }
            } else {
                // 对于 content:// 格式的 Uri，使用 ContentResolver 删除
                deleted = contentResolver.delete(uri, null, null) > 0
            }
            deleted
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun scanDirectory(directoryPath: String): com.example.picbrowser.data.model.CustomDirectory? = withContext(Dispatchers.IO) {
        android.util.Log.d("ImageRepository", "Scanning directory: $directoryPath")

        // 先尝试用 File API
        val directory = java.io.File(directoryPath)
        if (directory.exists() && directory.isDirectory) {
            val allFiles = directory.listFiles()
            android.util.Log.d("ImageRepository", "File API - Total files: ${allFiles?.size ?: 0}")

            if (!allFiles.isNullOrEmpty()) {
                val imageFiles = allFiles
                    .filter { it.isFile && isImageFile(it.name) }
                    .sortedByDescending { it.lastModified() }

                android.util.Log.d("ImageRepository", "File API - Found ${imageFiles.size} images")

                val coverUri = imageFiles.firstOrNull()?.let { android.net.Uri.fromFile(it) }
                return@withContext com.example.picbrowser.data.model.CustomDirectory(
                    path = directoryPath,
                    name = directory.name,
                    coverUri = coverUri,
                    imageCount = imageFiles.size
                )
            }
        }

        // 后尝试用 MediaStore 查询该目录下的图片
        val imagesFromMediaStore = getImagesFromDirectoryViaMediaStore(directoryPath)
        if (imagesFromMediaStore.isNotEmpty()) {
            android.util.Log.d("ImageRepository", "MediaStore found ${imagesFromMediaStore.size} images")
            return@withContext com.example.picbrowser.data.model.CustomDirectory(
                path = directoryPath,
                name = java.io.File(directoryPath).name,
                coverUri = imagesFromMediaStore.firstOrNull()?.uri,
                imageCount = imagesFromMediaStore.size
            )
        }

        // 都失败了，返回空目录
        com.example.picbrowser.data.model.CustomDirectory(
            path = directoryPath,
            name = java.io.File(directoryPath).name,
            imageCount = 0
        )
    }

    suspend fun getImagesFromDirectory(directoryPath: String): List<ImageItem> = withContext(Dispatchers.IO) {
        android.util.Log.d("ImageRepository", "Loading images from: $directoryPath")

        // 先尝试File API
        val directory = java.io.File(directoryPath)
        if (directory.exists() && directory.isDirectory) {
            val files = directory.listFiles()
                ?.filter { it.isFile && isImageFile(it.name) }
                ?.sortedByDescending { it.lastModified() }
                ?.map { file ->
                    ImageItem(
                        id = file.hashCode().toLong(),
                        uri = android.net.Uri.fromFile(file),
                        displayName = file.name,
                        dateTaken = file.lastModified(),
                        dateModified = file.lastModified(),
                        size = file.length(),
                        width = 0,
                        height = 0,
                        bucketId = directoryPath.hashCode().toLong(),
                        bucketName = directory.name
                    )
                }

            if (!files.isNullOrEmpty()) {
                android.util.Log.d("ImageRepository", "File API loaded ${files.size} images")
                return@withContext files
            }
        }

        // 后尝试 MediaStore（获取 content:// Uri）
        val mediaStoreImages = getImagesFromDirectoryViaMediaStore(directoryPath)
        if (mediaStoreImages.isNotEmpty()) {
            android.util.Log.d("ImageRepository", "MediaStore loaded ${mediaStoreImages.size} images")
            return@withContext mediaStoreImages
        }

        emptyList()
    }

    private suspend fun getImagesFromDirectoryViaMediaStore(directoryPath: String): List<ImageItem> = withContext(Dispatchers.IO) {
        val images = mutableListOf<ImageItem>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DATA
        )

        val selection = "${MediaStore.Images.Media.DATA} LIKE ?"
        val selectionArgs = arrayOf("$directoryPath/%")
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateTakenColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val bucketNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val uri = android.content.ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                val dateTakenRaw = cursor.getLong(dateTakenColumn)
                val dateModifiedRaw = cursor.getLong(dateModifiedColumn) * 1000  // DATE_MODIFIED 是秒，转毫秒
                val dateAddedRaw = cursor.getLong(dateAddedColumn) * 1000      // DATE_ADDED 是秒，转毫秒

                // 拍摄时间 fallback 策略: DATE_TAKEN -> DATE_MODIFIED -> DATE_ADDED
                val finalDateTaken = when {
                    dateTakenRaw > 0 -> dateTakenRaw
                    dateModifiedRaw > 0 -> dateModifiedRaw
                    else -> dateAddedRaw
                }

                images.add(
                    ImageItem(
                        id = id,
                        uri = uri,
                        displayName = cursor.getString(displayNameColumn) ?: "",
                        dateTaken = finalDateTaken,
                        dateModified = dateModifiedRaw,
                        size = cursor.getLong(sizeColumn),
                        width = cursor.getInt(widthColumn),
                        height = cursor.getInt(heightColumn),
                        bucketId = cursor.getLong(bucketIdColumn),
                        bucketName = cursor.getString(bucketNameColumn) ?: ""
                    )
                )
            }
        }

        images
    }

    private fun isImageFile(fileName: String): Boolean {
        val lowerName = fileName.lowercase()
        return IMAGE_EXTENSIONS.any { ext ->
            lowerName.endsWith(".$ext")
        }
    }
}
