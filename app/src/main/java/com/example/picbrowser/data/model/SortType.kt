package com.example.picbrowser.data.model

enum class SortType(val displayName: String) {
    DATE_TAKEN("拍摄时间"),
    DATE_MODIFIED("修改时间"),
    NAME("文件名称"),
    SIZE("文件大小")
}

enum class SortDirection(val isAscending: Boolean) {
    DESCENDING(false),  // 降序（最新/最大在前）
    ASCENDING(true)     // 升序（最旧/最小在前）
}