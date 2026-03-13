package com.example.picbrowser.data.model

import android.net.Uri

data class Folder(
    val id: Long,
    val name: String,
    val coverUri: Uri,
    val imageCount: Int
)
