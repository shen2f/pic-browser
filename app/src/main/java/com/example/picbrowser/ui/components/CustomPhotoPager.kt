package com.example.picbrowser.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun CustomPhotoPager(
    pageCount: Int,
    initialPage: Int = 0,
    onPageChanged: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
    pageContent: @Composable (page: Int, onScaleChanged: (Boolean) -> Unit, onHorizontalDrag: (Float) -> Unit, onHorizontalDragEnd: (Float, Float, Long) -> Unit) -> Unit
) {
    val scope = rememberCoroutineScope()
    val pageOffset = remember { Animatable(0f) }
    var currentPage by remember { mutableIntStateOf(initialPage) }
    var isScaled by remember { mutableStateOf(false) }
    var containerWidth by remember { mutableFloatStateOf(0f) }

    val pageGap = with(LocalDensity.current) { 16.dp.toPx() }
    val pageSwitchThreshold = 0.5f

    fun snapToPage(page: Int) {
        scope.launch {
            pageOffset.animateTo(
                targetValue = 0f,
                animationSpec = spring()
            )
            currentPage = page.coerceIn(0, pageCount - 1)
            onPageChanged(currentPage)
        }
    }

    fun animateToPage(targetPage: Int) {
        scope.launch {
            val direction = if (targetPage > currentPage) 1 else -1
            val currentOffsetValue = pageOffset.value
            // 切换 currentPage 时，调整 pageOffset 来保持视觉位置不变
            currentPage = targetPage.coerceIn(0, pageCount - 1)
            pageOffset.snapTo(currentOffsetValue + direction)
            onPageChanged(currentPage)
            pageOffset.animateTo(
                targetValue = 0f,
                animationSpec = spring()
            )
        }
    }

    val onHorizontalDrag: (Float) -> Unit = { deltaX ->
        if (!isScaled && pageCount > 1 && containerWidth > 0) {
            scope.launch {
                val newOffset = (pageOffset.value + deltaX / containerWidth).coerceIn(-1f, 1f)
                pageOffset.snapTo(newOffset)
            }
        }
    }

    val onHorizontalDragEnd: (Float, Float, Long) -> Unit = { velocityX, totalDeltaX, durationMs ->
        if (!isScaled && pageCount > 1) {
            val currentOffset = pageOffset.value
            val isFling = durationMs < 250L && kotlin.math.abs(totalDeltaX) > 50f

            if (isFling) {
                // 快速滑动
                val targetPage = if (totalDeltaX > 0) {
                    // 向右滑动 -> 上一张
                    (currentPage - 1).coerceAtLeast(0)
                } else {
                    // 向左滑动 -> 下一张
                    (currentPage + 1).coerceAtMost(pageCount - 1)
                }
                if (targetPage != currentPage) {
                    animateToPage(targetPage)
                } else {
                    snapToPage(currentPage)
                }
            } else {
                // 缓慢滑动
                if (currentOffset > pageSwitchThreshold && currentPage > 0) {
                    // 向右滑动 -> 上一张
                    animateToPage(currentPage - 1)
                } else if (currentOffset < -pageSwitchThreshold && currentPage < pageCount - 1) {
                    // 向左滑动 -> 下一张
                    animateToPage(currentPage + 1)
                } else {
                    snapToPage(currentPage)
                }
            }
        }
    }

    val onScaleChanged: (Boolean) -> Unit = { scaled ->
        isScaled = scaled
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                containerWidth = size.width.toFloat()
            }
    ) {
        if (containerWidth > 0) {
            val pagesToShow = mutableListOf<Int>()
            if (currentPage > 0) pagesToShow.add(currentPage - 1)
            pagesToShow.add(currentPage)
            if (currentPage < pageCount - 1) pagesToShow.add(currentPage + 1)

            pagesToShow.forEach { page ->
                val offsetMultiplier = (page - currentPage).toFloat() + pageOffset.value

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = offsetMultiplier * (containerWidth + pageGap)
                        }
                ) {
                    pageContent(
                        page,
                        onScaleChanged,
                        onHorizontalDrag,
                        onHorizontalDragEnd
                    )
                }
            }
        }
    }
}