package com.example.picbrowser.data.model

import android.net.Uri

data class CustomDirectory(
    val id: Long = System.currentTimeMillis(),
    val path: String,
    val name: String,
    val coverUri: Uri? = null,
    val imageCount: Int = 0
)