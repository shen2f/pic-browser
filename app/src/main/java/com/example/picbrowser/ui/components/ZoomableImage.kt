package com.example.picbrowser.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
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
    onVerticalDragStart: () -> Unit = {},
    onVerticalDrag: (deltaY: Float, totalY: Float) -> Unit = { _, _ -> },
    onVerticalDragEnd: (shouldDismiss: Boolean, totalY: Float) -> Unit = { _, _ -> },
    onDismiss: () -> Unit = {},
    onShowDetails: () -> Unit = {},
    onSingleTap: () -> Unit = {}
) {
    var targetScale by remember { mutableFloatStateOf(1f) }
    var animationDuration by remember { mutableStateOf(0) }
    val displayScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(durationMillis = animationDuration),
        label = "scaleAnimation"
    )
    var offset by remember { mutableStateOf(Offset.Zero) }
    var lastTapTime by remember { mutableLongStateOf(0L) }

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
                            animationDuration = 0
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()

                            val newScale = (targetScale * zoom).coerceIn(minScale, maxScale)
                            targetScale = newScale
                            offset = offset.plus(pan)
                            onScaleChanged(newScale > 1.01f)
                        } else if (event.changes.size == 1) {
                            // 单指拖动
                            val change = event.changes.first()
                            val delta = change.position - change.previousPosition
                            totalDragDelta = totalDragDelta.plus(delta)

                            if (targetScale > 1.01f) {
                                // 放大状态：拖动图片
                                offset = offset.plus(delta)
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
                                            offset = offset.plus(delta)
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

                    var isDoubleTapAction = false
                    if (isTap) {
                        val timeSinceLastTap = currentTime - lastTapTime
                        if (timeSinceLastTap < doubleTapTimeout) {
                            // 双击：在 1x 和 2x 之间切换（带动画）
                            lastTapTime = 0L
                            isDoubleTapAction = true
                            animationDuration = 200
                            if (targetScale > 1.5f) {
                                targetScale = 1f
                                offset = Offset.Zero
                            } else {
                                targetScale = doubleTapScale
                            }
                            onScaleChanged(targetScale > 1.01f)
                        } else {
                            // 单击
                            lastTapTime = currentTime
                            onSingleTap()
                        }
                    }

                    if (!isDoubleTapAction && targetScale <= 1.01f) {
                        animationDuration = 0
                        targetScale = 1f
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
                        offset = Offset.Zero
                    }
                    onScaleChanged(targetScale > 1.01f)
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
                    scaleX = displayScale
                    scaleY = displayScale
                    translationX = offset.x
                    translationY = offset.y
                }
        )
    }
}