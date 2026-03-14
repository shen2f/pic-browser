package com.example.picbrowser.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
    onVerticalDragStart: () -> Unit = {},
    onVerticalDrag: (deltaY: Float, totalY: Float) -> Unit = { _, _ -> },
    onVerticalDragEnd: (shouldDismiss: Boolean, totalY: Float) -> Unit = { _, _ -> },
    onDismiss: () -> Unit = {},
    onShowDetails: () -> Unit = {},
    onSingleTap: () -> Unit = {}
) {
    val scaleState = remember { mutableFloatStateOf(1f) }
    val offsetState = remember { mutableStateOf(Offset.Zero) }
    val lastTapTimeState = remember { mutableLongStateOf(0L) }

    val touchSlop = with(LocalDensity.current) { 8.dp.toPx() }
    val dismissThreshold = with(LocalDensity.current) { 80.dp.toPx() }
    val doubleTapTimeout = 300L
    val minScale = 1f
    val maxScale = 5f
    val doubleTapScale = 2f

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
                            // 双指缩放 - 即时更新
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()

                            val newScale = (scaleState.floatValue * zoom).coerceIn(minScale, maxScale)
                            scaleState.floatValue = newScale
                            offsetState.value = offsetState.value.plus(pan)
                            onScaleChanged(newScale > 1.01f)
                        } else if (event.changes.size == 1) {
                            // 单指拖动
                            val change = event.changes.first()
                            val delta = change.position - change.previousPosition
                            totalDragDelta = totalDragDelta.plus(delta)

                            if (scaleState.floatValue > 1.01f) {
                                // 放大状态：拖动图片
                                offsetState.value = offsetState.value.plus(delta)
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
                                            DragDirection.Vertical.also { onVerticalDragStart() }
                                        }
                                    }
                                }

                                if (hasLockedDirection) {
                                    when (dragDirection) {
                                        DragDirection.Horizontal -> {
                                            onHorizontalDrag(delta.x)
                                        }
                                        DragDirection.Vertical -> {
                                            offsetState.value = offsetState.value.plus(delta)
                                            onVerticalDrag(delta.y, totalDragDelta.y)
                                        }
                                        null -> {}
                                    }
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    val durationMs = endTime - startTime
                    val currentTime = down.uptimeMillis
                    // 计算"虚拟"速度：总位移 / 时间
                    val velocityX = if (durationMs > 0) {
                        totalDragDelta.x / (durationMs / 1000f)
                    } else {
                        0f
                    }

                    // 检查是否是单击或双击（无论是否放大都支持）
                    val isTap = durationMs < 300L &&
                        kotlin.math.abs(totalDragDelta.x) < touchSlop &&
                        kotlin.math.abs(totalDragDelta.y) < touchSlop

                    if (isTap) {
                        val timeSinceLastTap = currentTime - lastTapTimeState.longValue
                        if (timeSinceLastTap < doubleTapTimeout) {
                            // 双击：在 1x 和 2x 之间切换（无动画，即时响应）
                            lastTapTimeState.longValue = 0L
                            if (scaleState.floatValue > 1.5f) {
                                scaleState.floatValue = 1f
                                offsetState.value = Offset.Zero
                            } else {
                                scaleState.floatValue = doubleTapScale
                            }
                            onScaleChanged(scaleState.floatValue > 1.01f)
                        } else {
                            // 单击
                            lastTapTimeState.longValue = currentTime
                            onSingleTap()
                        }
                    }

                    if (scaleState.floatValue <= 1.01f) {
                        scaleState.floatValue = 1f
                        when (dragDirection) {
                            DragDirection.Horizontal -> {
                                onHorizontalDragEnd(velocityX, totalDragDelta.x, durationMs)
                            }
                            DragDirection.Vertical -> {
                                val shouldDismiss = totalDragDelta.y > dismissThreshold
                                onVerticalDragEnd(shouldDismiss, totalDragDelta.y)
                                if (shouldDismiss) {
                                    onDismiss()
                                } else if (totalDragDelta.y < -dismissThreshold) {
                                    onShowDetails()
                                }
                            }
                            null -> {}
                        }
                        offsetState.value = Offset.Zero
                    }
                    onScaleChanged(scaleState.floatValue > 1.01f)
                }
            }
    ) {
        AsyncImage(
            model = imageUri,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scaleState.floatValue
                    scaleY = scaleState.floatValue
                    translationX = offsetState.value.x
                    translationY = offsetState.value.y
                }
        )
    }
}