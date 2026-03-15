package com.example.picbrowser.data.model

import android.net.Uri

data class ImageItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val dateTaken: Long,      // 拍摄时间（fallback策略）
    val dateModified: Long,   // 文件修改时间
    val size: Long,
    val width: Int,
    val height: Int,
    val bucketId: Long,
    val bucketName: String
)
