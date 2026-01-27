package com.app.ralaunch.controls.editors.ui

import androidx.compose.animation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 悬浮控件菜单模式
 */
enum class FloatingMenuMode {
    /** 独立编辑器模式 */
    EDITOR,
    /** 游戏内模式 */
    IN_GAME
}

/**
 * 悬浮菜单状态
 */
@Stable
class FloatingMenuState(
    initialOffset: androidx.compose.ui.geometry.Offset = androidx.compose.ui.geometry.Offset.Zero,
    initialExpanded: Boolean = false
) {
    var offset by mutableStateOf(initialOffset)
    var isExpanded by mutableStateOf(initialExpanded)
    var isGhostMode by mutableStateOf(false)
    var isPaletteVisible by mutableStateOf(false)
    var isGridVisible by mutableStateOf(true)
    var isInEditMode by mutableStateOf(false)
    
    // 悬浮球可见性 (可通过音量键切换)
    var isFloatingBallVisible by mutableStateOf(true)
    
    // 控件正在使用中 (自动进入幽灵模式)
    var isControlInUse by mutableStateOf(false)
    
    // 游戏内特有状态
    var isFpsDisplayEnabled by mutableStateOf(false)
    var isTouchEventEnabled by mutableStateOf(true)
    var isControlsVisible by mutableStateOf(true)
    
    // 菜单面板偏移量（可拖动）
    var menuPanelOffset by mutableStateOf(androidx.compose.ui.geometry.Offset.Zero)
    
    /** 计算实际的幽灵模式状态 (手动幽灵 或 控件使用中) */
    val effectiveGhostMode: Boolean
        get() = isGhostMode || isControlInUse
    
    fun updateOffset(delta: androidx.compose.ui.geometry.Offset) {
        offset = androidx.compose.ui.geometry.Offset(
            offset.x + delta.x,
            offset.y + delta.y
        )
    }
    
    fun updateMenuPanelOffset(delta: androidx.compose.ui.geometry.Offset) {
        menuPanelOffset = androidx.compose.ui.geometry.Offset(
            menuPanelOffset.x + delta.x,
            menuPanelOffset.y + delta.y
        )
    }
    
    fun toggleMenu() {
        isExpanded = !isExpanded
    }
    
    fun togglePalette() {
        isPaletteVisible = !isPaletteVisible
    }
    
    fun toggleGhostMode() {
        isGhostMode = !isGhostMode
    }
    
    fun toggleGrid() {
        isGridVisible = !isGridVisible
    }
    
    fun toggleEditMode() {
        isInEditMode = !isInEditMode
    }
    
    fun toggleFloatingBallVisibility() {
        isFloatingBallVisible = !isFloatingBallVisible
        // 隐藏悬浮球时同时收起菜单
        if (!isFloatingBallVisible) {
            isExpanded = false
        }
    }
}

@Composable
fun rememberFloatingMenuState(
    initialOffset: androidx.compose.ui.geometry.Offset = androidx.compose.ui.geometry.Offset.Zero,
    initialExpanded: Boolean = false
): FloatingMenuState {
    return remember { FloatingMenuState(initialOffset, initialExpanded) }
}

/**
 * 悬浮菜单回调接口
 */
interface FloatingMenuCallbacks {
    // 通用回调
    fun onAddButton() {}
    fun onAddJoystick() {}
    fun onAddTouchPad() {}
    fun onAddMouseWheel() {}
    fun onAddText() {}
    fun onSave() {}
    fun onOpenSettings() {}
    fun onExit() {}
    
    // 游戏内特有回调
    fun onToggleEditMode() {}
    fun onToggleControls() {}
    fun onFpsDisplayChanged(enabled: Boolean) {}
    fun onTouchEventChanged(enabled: Boolean) {}
    fun onExitGame() {}
}

/**
 * 统一的悬浮控件菜单
 * 支持编辑器模式和游戏内模式
 */
@Composable
fun FloatingControlMenu(
    mode: FloatingMenuMode,
    state: FloatingMenuState,
    callbacks: FloatingMenuCallbacks,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp)
            .graphicsLayer { alpha = if (state.effectiveGhostMode) 0.25f else 1.0f }
    ) {
        // 悬浮球 (可通过音量键切换可见性)
        AnimatedVisibility(
            visible = state.isFloatingBallVisible,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            FloatingBall(
                isExpanded = state.isExpanded,
                onClick = { state.toggleMenu() },
                modifier = Modifier
                    .offset { IntOffset(state.offset.x.roundToInt(), state.offset.y.roundToInt()) }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { state.isGhostMode = true },
                            onDragEnd = { state.isGhostMode = false },
                            onDragCancel = { state.isGhostMode = false },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                state.updateOffset(androidx.compose.ui.geometry.Offset(dragAmount.x, dragAmount.y))
                            }
                        )
                    }
            )
        }

        // 菜单与组件库
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 12.dp)
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 主菜单
            AnimatedVisibility(
                visible = state.isExpanded,
                enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
            ) {
                when (mode) {
                    FloatingMenuMode.EDITOR -> EditorMenu(state, callbacks)
                    FloatingMenuMode.IN_GAME -> InGameMenu(state, callbacks)
                }
            }

            // 组件库面板 (仅编辑模式下显示)
            val showPalette = when (mode) {
                FloatingMenuMode.EDITOR -> state.isPaletteVisible && state.isExpanded
                FloatingMenuMode.IN_GAME -> state.isPaletteVisible && state.isExpanded && state.isInEditMode
            }
            
            AnimatedVisibility(
                visible = showPalette,
                enter = slideInHorizontally(initialOffsetX = { -20 }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { -20 }) + fadeOut()
            ) {
                // 使用 ControlEditorScreen 中定义的 ComponentPalette
                ComponentPalette(
                    onAddControl = { type ->
                        when (type) {
                            "button" -> callbacks.onAddButton()
                            "joystick" -> callbacks.onAddJoystick()
                            "touchpad" -> callbacks.onAddTouchPad()
                            "mousewheel" -> callbacks.onAddMouseWheel()
                            "text" -> callbacks.onAddText()
                        }
                    },
                    onClose = { state.isPaletteVisible = false }
                )
            }
        }
    }
}

// FloatingBall 组件定义在 ControlEditorScreen.kt 中

/**
 * 编辑器模式菜单
 */
@Composable
private fun EditorMenu(
    state: FloatingMenuState,
    callbacks: FloatingMenuCallbacks
) {
    Surface(
        modifier = Modifier.width(240.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MenuHeader(title = "编辑器菜单", onClose = { state.isExpanded = false })
            
            HorizontalDivider()

            // 使用 ControlEditorScreen 中定义的 MenuRowItem
            MenuRowItem(
                icon = Icons.Default.AddCircle,
                label = "组件库",
                isActive = state.isPaletteVisible,
                onClick = { state.togglePalette() }
            )
            
            MenuRowItem(
                icon = if (state.isGhostMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                label = "幽灵模式",
                isActive = state.isGhostMode,
                onClick = { state.toggleGhostMode() }
            )

            MenuRowItem(
                icon = if (state.isGridVisible) Icons.Default.GridOn else Icons.Default.GridOff,
                label = "网格显示",
                isActive = state.isGridVisible,
                onClick = { state.toggleGrid() }
            )

            MenuRowItem(
                icon = Icons.Default.Settings,
                label = "编辑器设置",
                isActive = false,
                onClick = { callbacks.onOpenSettings() }
            )

            HorizontalDivider()

            MenuRowItem(
                icon = Icons.Default.Save,
                label = "保存布局",
                isActive = false,
                onClick = { callbacks.onSave() },
                tint = MaterialTheme.colorScheme.primary
            )

            MenuRowItem(
                icon = Icons.Default.ExitToApp,
                label = "退出编辑器",
                isActive = false,
                onClick = { callbacks.onExit() },
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * 游戏内模式菜单
 */
@Composable
private fun InGameMenu(
    state: FloatingMenuState,
    callbacks: FloatingMenuCallbacks
) {
    Surface(
        modifier = Modifier
            .width(260.dp)
            .offset { IntOffset(state.menuPanelOffset.x.roundToInt(), state.menuPanelOffset.y.roundToInt()) },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 可拖动的标题栏
            DraggableMenuHeader(
                title = "游戏菜单", 
                onClose = { state.isExpanded = false },
                onDrag = { delta -> state.updateMenuPanelOffset(delta) }
            )
            
            HorizontalDivider()

            // 编辑模式切换
            MenuRowItem(
                icon = if (state.isInEditMode) Icons.Default.Close else Icons.Default.Edit,
                label = if (state.isInEditMode) "退出编辑模式" else "进入编辑模式",
                isActive = state.isInEditMode,
                onClick = { 
                    state.toggleEditMode()
                    callbacks.onToggleEditMode()
                },
                tint = if (state.isInEditMode) MaterialTheme.colorScheme.error else Color.Unspecified
            )

            // 编辑模式下的选项
            AnimatedVisibility(visible = state.isInEditMode) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MenuRowItem(
                        icon = Icons.Default.AddCircle,
                        label = "组件库",
                        isActive = state.isPaletteVisible,
                        onClick = { state.togglePalette() }
                    )
                    
                    MenuRowItem(
                        icon = if (state.isGridVisible) Icons.Default.GridOn else Icons.Default.GridOff,
                        label = "网格显示",
                        isActive = state.isGridVisible,
                        onClick = { state.toggleGrid() }
                    )

                    MenuRowItem(
                        icon = Icons.Default.Save,
                        label = "保存布局",
                        isActive = false,
                        onClick = { callbacks.onSave() },
                        tint = MaterialTheme.colorScheme.primary
                    )
                    
                    HorizontalDivider()
                }
            }

            // 非编辑模式下的游戏选项
            AnimatedVisibility(visible = !state.isInEditMode) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // 控件可见性
                    MenuRowItem(
                        icon = if (state.isControlsVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        label = if (state.isControlsVisible) "隐藏控件" else "显示控件",
                        isActive = !state.isControlsVisible,
                        onClick = { 
                            state.isControlsVisible = !state.isControlsVisible
                            callbacks.onToggleControls()
                        }
                    )

                    // FPS 显示开关
                    MenuSwitchItem(
                        icon = Icons.Default.Speed,
                        label = "FPS 显示",
                        checked = state.isFpsDisplayEnabled,
                        onCheckedChange = { 
                            state.isFpsDisplayEnabled = it
                            callbacks.onFpsDisplayChanged(it)
                        }
                    )

                    // 触摸事件开关
                    MenuSwitchItem(
                        icon = Icons.Default.TouchApp,
                        label = "触摸控制",
                        checked = state.isTouchEventEnabled,
                        onCheckedChange = { 
                            state.isTouchEventEnabled = it
                            callbacks.onTouchEventChanged(it)
                        }
                    )
                    
                    HorizontalDivider()
                }
            }

            // 隐藏悬浮球
            MenuRowItem(
                icon = Icons.Default.VisibilityOff,
                label = "隐藏悬浮球(音量键打开)",
                isActive = false,
                onClick = { state.toggleFloatingBallVisibility() }
            )
            
            Text(
                text = "按音量键可重新显示",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp)
            )

            HorizontalDivider()

            // 退出游戏
            MenuRowItem(
                icon = Icons.Default.ExitToApp,
                label = "退出游戏",
                isActive = false,
                onClick = { callbacks.onExitGame() },
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun MenuHeader(
    title: String,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Default.ExpandLess, contentDescription = "收起菜单", modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * 可拖动的菜单标题栏
 */
@Composable
private fun DraggableMenuHeader(
    title: String,
    onClose: () -> Unit,
    onDrag: (androidx.compose.ui.geometry.Offset) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(androidx.compose.ui.geometry.Offset(dragAmount.x, dragAmount.y))
                }
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 拖动指示器
            Icon(
                Icons.Default.DragIndicator, 
                contentDescription = "拖动",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Default.ExpandLess, contentDescription = "收起菜单", modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * 带开关的菜单项 (游戏内专用)
 */
@Composable
fun MenuSwitchItem(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.scale(0.8f)
            )
        }
    }
}
