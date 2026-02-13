package com.app.ralaunch.feature.controls.editors.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * 游戏内子布局快捷切换覆盖层
 * 
 * 显示为屏幕边缘的紧凑芯片栏，可拖动、自动淡出。
 * 玩家可以通过点击芯片快速切换子布局（如建筑/战斗/默认等模式）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LayoutSwitcherOverlay(
    subLayouts: List<Pair<String, String>>,  // (id, name) 列表
    activeSubLayoutId: String?,
    onSwitch: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // 拖动偏移量
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    
    // 自动淡出控制
    var isInteracting by remember { mutableStateOf(false) }
    var showFull by remember { mutableStateOf(true) }
    
    // 自动淡出：3 秒无交互后降低透明度
    LaunchedEffect(isInteracting, activeSubLayoutId) {
        showFull = true
        if (!isInteracting) {
            delay(4000)
            showFull = false
        }
    }
    
    val targetAlpha = if (showFull) 1f else 0.3f
    val animatedAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        label = "switcher_alpha"
    )
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 8.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Surface(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .graphicsLayer { alpha = animatedAlpha }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { isInteracting = true },
                        onDragEnd = { isInteracting = false },
                        onDragCancel = { isInteracting = false },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            offsetX += dragAmount.x
                            offsetY += dragAmount.y
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            // 点击时恢复完全可见
                            showFull = true
                            isInteracting = false  // 触发 LaunchedEffect 重新计时
                        }
                    )
                },
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
            tonalElevation = 4.dp,
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                subLayouts.forEach { (id, name) ->
                    val isActive = id == activeSubLayoutId
                    
                    FilterChip(
                        selected = isActive,
                        onClick = {
                            showFull = true
                            isInteracting = false
                            onSwitch(id)
                        },
                        label = {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        modifier = Modifier.height(28.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
        }
    }
}
