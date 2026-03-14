package com.example.picbrowser.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

enum class DragDirection {
    Horizontal,
    Vertical
}

@Composable
fun ZoomableImage(
    imageUri: android.net.Uri,
    modifier: Modifier = Modifier,
    onScaleChanged: (isScaled: Boolean) -> Unit = {},
    onHorizontalDrag: (deltaX: Float) -> Unit = {},
    onHorizontalDragEnd: (velocityX: Float, totalDeltaX: Float, durationMs: Long) -> Unit = { _, _, _ -> },
    onDismiss: () -> Unit = {},
    onShowDetails: () -> Unit = {}
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val touchSlop = with(LocalDensity.current) { 8.dp.toPx() }
    val dismissThreshold = with(LocalDensity.current) { 80.dp.toPx() }
    val minScale = 1f
    val maxScale = 5f

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val startTime = down.uptimeMillis

                    var dragDirection: DragDirection? = null
                    var hasLockedDirection = false
                    var totalDragDelta = Offset.Zero
                    var endTime = startTime

                    do {
                        val event = awaitPointerEvent()
                        endTime = event.changes.firstOrNull()?.uptimeMillis ?: endTime

                        if (event.changes.size == 2) {
                            // 双指缩放
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()

                            val newScale = (scale * zoom).coerceIn(minScale, maxScale)
                            scale = newScale
                            offset = offset + pan
                            onScaleChanged(scale > 1.01f)
                        } else if (event.changes.size == 1) {
                            // 单指拖动
                            val change = event.changes.first()
                            val delta = change.position - change.previousPosition
                            totalDragDelta += delta

                            if (scale > 1.01f) {
                                // 放大状态：拖动图片
                                offset = offset + delta
                            } else {
                                // 原始大小
                                if (!hasLockedDirection) {
                                    val dx = totalDragDelta.x
                                    val dy = totalDragDelta.y
                                    val maxAbs = kotlin.math.abs(dx).coerceAtLeast(kotlin.math.abs(dy))

                                    if (maxAbs > touchSlop) {
                                        hasLockedDirection = true
                                        dragDirection = if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                                            DragDirection.Horizontal
                                        } else {
                                            DragDirection.Vertical
                                        }
                                    }
                                }

                                if (hasLockedDirection) {
                                    when (dragDirection) {
                                        DragDirection.Horizontal -> {
                                            onHorizontalDrag(delta.x)
                                        }
                                        DragDirection.Vertical -> {
                                            offset = offset + delta
                                        }
                                        null -> {}
                                    }
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    val durationMs = endTime - startTime
                    // 计算"虚拟"速度：总位移 / 时间
                    val velocityX = if (durationMs > 0) {
                        totalDragDelta.x / (durationMs / 1000f)
                    } else {
                        0f
                    }

                    if (scale <= 1.01f) {
                        scale = 1f
                        when (dragDirection) {
                            DragDirection.Horizontal -> {
                                onHorizontalDragEnd(velocityX, totalDragDelta.x, durationMs)
                            }
                            DragDirection.Vertical -> {
                                if (totalDragDelta.y > dismissThreshold) {
                                    onDismiss()
                                } else if (totalDragDelta.y < -dismissThreshold) {
                                    onShowDetails()
                                }
                            }
                            null -> {}
                        }
                        offset = Offset.Zero
                    }
                    onScaleChanged(scale > 1.01f)
                }
            }
    ) {
        AsyncImage(
            model = imageUri,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        )
    }
}