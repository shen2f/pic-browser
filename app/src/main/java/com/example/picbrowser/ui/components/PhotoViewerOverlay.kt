package com.example.picbrowser.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.picbrowser.ui.viewmodel.SharedTransitionViewModel

@Composable
fun PhotoViewerOverlay(
    sharedViewModel: SharedTransitionViewModel,
    onDismissComplete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val state by sharedViewModel.state.collectAsState()
    val maxDragOffset = with(LocalDensity.current) { 400.dp.toPx() }

    // 打开动画
    val openProgress = remember { Animatable(0f) }

    // 当 isDismissing 为 true 时，执行消失动画
    LaunchedEffect(state.isDismissing) {
        if (state.isDismissing) {
            onDismissComplete()
        }
    }

    val dragProgress = if (state.isDragging) {
        (state.dragOffset / maxDragOffset).coerceIn(0f, 1f)
    } else {
        0f
    }

    val currentOffsetY = state.dragOffset
    val dragScale = 1f - dragProgress * 0.3f
    val dragBackgroundAlpha = 1f - dragProgress

    // 最终效果结合打开动画和拖拽动画
    val finalAlpha = if (openProgress.value < 1f) {
        openProgress.value
    } else {
        dragBackgroundAlpha
    }
    val finalScale = if (openProgress.value < 1f) {
        0.85f + openProgress.value * 0.15f
    } else {
        dragScale
    }
    val contentAlpha = if (openProgress.value < 1f) openProgress.value else 1f

    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        // 背景层 - 单独控制透明度
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = finalAlpha))
        )

        // 内容层 - 应用变换
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = currentOffsetY
                    scaleX = finalScale
                    scaleY = finalScale
                    alpha = contentAlpha
                }
        ) {
            content()
        }
    }

    // 组件初始化时触发打开动画
    LaunchedEffect(Unit) {
        openProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 250)
        )
    }
}