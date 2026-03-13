package com.example.picbrowser.data.model

import android.net.Uri

data class ImageItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val dateTaken: Long,
    val size: Long,
    val width: Int,
    val height: Int,
    val bucketId: Long,
    val bucketName: String
)
