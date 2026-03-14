package com.example.picbrowser.ui.viewmodel

import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TransitionState(
    val dragOffset: Float = 0f,
    val isDragging: Boolean = false,
    val isDismissing: Boolean = false,
    val targetImageId: Long? = null,
    val targetThumbnailRect: Rect? = null,
    val needsScrollToPosition: Int? = null
)

class SharedTransitionViewModel : ViewModel() {
    private val _state = MutableStateFlow(TransitionState())
    val state: StateFlow<TransitionState> = _state.asStateFlow()

    fun updateDragOffset(offset: Float) {
        _state.value = _state.value.copy(dragOffset = offset.coerceAtLeast(0f))
    }

    fun setDragging(isDragging: Boolean) {
        _state.value = _state.value.copy(isDragging = isDragging)
    }

    fun setTargetImageId(imageId: Long) {
        _state.value = _state.value.copy(targetImageId = imageId)
    }

    fun setTargetThumbnailRect(rect: Rect?) {
        _state.value = _state.value.copy(targetThumbnailRect = rect)
    }

    fun startDismiss() {
        _state.value = _state.value.copy(isDismissing = true)
    }

    fun reset() {
        _state.value = TransitionState()
    }
}
