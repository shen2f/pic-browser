package com.example.picbrowser.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

    // 当 isDismissing 为 true 时，执行消失动画
    LaunchedEffect(state.isDismissing) {
        if (state.isDismissing) {
            onDismissComplete()
        }
    }

    val progress = if (state.isDragging) {
        (state.dragOffset / maxDragOffset).coerceIn(0f, 1f)
    } else {
        0f
    }

    val currentOffsetY = state.dragOffset
    val currentScale = 1f - progress * 0.3f
    val backgroundAlpha = 1f - progress

    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        // 背景层 - 单独控制透明度
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = backgroundAlpha))
        )

        // 内容层 - 应用变换
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = currentOffsetY
                    scaleX = currentScale
                    scaleY = currentScale
                }
        ) {
            content()
        }
    }
}