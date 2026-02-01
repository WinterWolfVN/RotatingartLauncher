package com.app.ralaunch.controls.editors.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.app.ralaunch.R
import com.app.ralaunch.controls.data.ControlData
import com.app.ralaunch.controls.editors.ControlEditorViewModel
import com.app.ralaunch.controls.packs.ControlLayout
import com.app.ralaunch.controls.views.ControlLayout as ControlLayoutView
import com.app.ralaunch.controls.views.GridOverlayView
import com.app.ralaunch.controls.bridges.DummyInputBridge
import com.app.ralaunch.ui.compose.dialogs.KeyBindingDialog
import com.app.ralaunch.data.SettingsManager
import com.app.ralaunch.controls.KeyMapper
import kotlin.math.roundToInt

@Composable
fun ControlEditorScreen(
    viewModel: ControlEditorViewModel,
    layout: ControlLayout?,
    selectedControl: ControlData?,
    isPropertyPanelVisible: Boolean,
    isPaletteVisible: Boolean,
    onExit: () -> Unit
) {
    val menuOffset by viewModel.menuOffset.collectAsState()
    val isGhostMode by viewModel.isGhostMode.collectAsState()
    val isMenuExpanded by viewModel.isMenuExpanded.collectAsState()
    val showExitDialog by viewModel.showExitDialog.collectAsState()
    val showKeySelector by viewModel.showKeySelector.collectAsState()
    val showJoystickKeyMapping by viewModel.showJoystickKeyMapping.collectAsState()
    val showEditorSettings by viewModel.showEditorSettings.collectAsState()
    val showTextureSelector by viewModel.showTextureSelector.collectAsState()
    val showPolygonEditor by viewModel.showPolygonEditor.collectAsState()
    val isGridVisible by viewModel.isGridVisible.collectAsState()
    
    // 属性面板偏移量（可拖动）
    var propertyPanelOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

    // 处理返回键
    BackHandler {
        if (!viewModel.requestExit()) {
            // 会显示退出确认对话框
        } else {
            onExit()
        }
    }

    // 适配主题背景色
    val backgroundColor = MaterialTheme.colorScheme.background

    Box(modifier = Modifier.fillMaxSize().background(backgroundColor)) {
        // Layer 0: 游戏预览层 (AndroidView)
        AndroidView(
            factory = { context ->
                ControlLayoutView(context).apply {
                    inputBridge = DummyInputBridge()
                    isModifiable = true
                    setEditControlListener { data ->
                        viewModel.selectControl(data)
                    }
                    // 监听控件拖动状态，自动切换幽灵模式
                    setOnControlChangedListener(object : ControlLayoutView.OnControlChangedListener {
                        override fun onControlChanged() {
                            viewModel.saveLayout()
                        }
                        override fun onControlDragging(isDragging: Boolean) {
                            viewModel.setGhostMode(isDragging)
                        }
                    })
                }
            },
            update = { view ->
                if (view.currentLayout != layout) {
                    view.loadLayout(layout)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Layer 1: 网格辅助线 (可切换显示，不拦截触摸事件)
        if (isGridVisible) {
            AndroidView(
                factory = { context -> 
                    GridOverlayView(context).apply {
                        isClickable = false
                        isFocusable = false
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Layer 2: 自由移动的悬浮球菜单系统
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .graphicsLayer { alpha = if (isGhostMode) 0.3f else 1.0f }
        ) {
            // 点击空白区域关闭菜单和属性面板
            if (isMenuExpanded || isPropertyPanelVisible) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {
                            viewModel.toggleMenu(false)
                            viewModel.selectControl(null)
                        }
                )
            }
            
            // 1. 悬浮球 (独立位置，可自由拖拽)
            FloatingBall(
                isExpanded = isMenuExpanded,
                onClick = { viewModel.toggleMenu(!isMenuExpanded) },
                modifier = Modifier
                    .offset { IntOffset(menuOffset.x.roundToInt(), menuOffset.y.roundToInt()) }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { viewModel.setGhostMode(true) },
                            onDragEnd = { viewModel.setGhostMode(false) },
                            onDragCancel = { viewModel.setGhostMode(false) },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                viewModel.updateMenuOffset(dragAmount)
                            }
                        )
                    }
            )

            // 2. 菜单与组件库 (现在改为横向排列，组件库在菜单右边)
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 12.dp)
                    .height(IntrinsicSize.Min), // 确保高度一致
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 展开的操作按钮菜单
                AnimatedVisibility(
                    visible = isMenuExpanded,
                    enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
                    exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
                ) {
                    ActionWindowMenu(
                        isPaletteVisible = isPaletteVisible,
                        isGhostMode = isGhostMode,
                        isGridVisible = isGridVisible,
                        onTogglePalette = { viewModel.togglePalette(!isPaletteVisible) },
                        onToggleGhostMode = { viewModel.setGhostMode(!isGhostMode) },
                        onToggleGrid = { viewModel.toggleGrid(!isGridVisible) },
                        onOpenSettings = { viewModel.toggleEditorSettings(true) },
                        onSave = { viewModel.saveLayout() },
                        onCloseMenu = { viewModel.toggleMenu(false) },
                        onExit = { 
                            if (!viewModel.requestExit()) {
                                // 会显示退出确认对话框
                            } else {
                                onExit()
                            }
                        }
                    )
                }

                // 组件库面板 (现在水平排列在菜单右侧)
                AnimatedVisibility(
                    visible = isPaletteVisible && isMenuExpanded,
                    enter = slideInHorizontally(initialOffsetX = { -20 }) + fadeIn(), // 从左侧轻微滑入
                    exit = slideOutHorizontally(targetOffsetX = { -20 }) + fadeOut()
                ) {
                    ComponentPalette(
                        onAddControl = { viewModel.addNewControl(it) },
                        onClose = { viewModel.togglePalette(false) }
                    )
                }
            }

            // 右侧属性面板 (可拖动)
            AnimatedVisibility(
                visible = isPropertyPanelVisible,
                enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset { IntOffset(propertyPanelOffset.x.roundToInt(), propertyPanelOffset.y.roundToInt()) }
            ) {
                PropertyPanel(
                    control = selectedControl,
                    onUpdate = { viewModel.updateControl(it) },
                    onClose = { viewModel.selectControl(null) },
                    onOpenKeySelector = { viewModel.showKeySelector(it) },
                    onOpenJoystickKeyMapping = { viewModel.showJoystickKeyMapping(it) },
                    onOpenTextureSelector = { control, type -> viewModel.showTextureSelector(control, type) },
                    onOpenPolygonEditor = { viewModel.showPolygonEditor(it) },
                    onDrag = { delta ->
                        propertyPanelOffset = androidx.compose.ui.geometry.Offset(
                            propertyPanelOffset.x + delta.x,
                            propertyPanelOffset.y + delta.y
                        )
                    },
                    onDuplicate = { viewModel.duplicateSelectedControl() },
                    onDelete = { viewModel.deleteSelectedControl() }
                )
            }
        }

        // Layer 5: 底部操作按钮组
        if (selectedControl != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
                    .graphicsLayer { alpha = if (isGhostMode) 0.3f else 1.0f }
            ) {
                FloatingActionButton(
                    onClick = { viewModel.deleteSelectedControl() },
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                    contentColor = MaterialTheme.colorScheme.error
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "删除控件")
                }
            }
        }
    }

    // ========== 对话框组 ==========

    // 退出确认对话框
    if (showExitDialog) {
        ExitConfirmDialog(
            onSaveAndExit = { viewModel.saveAndExit(onExit) },
            onExitWithoutSaving = { viewModel.exitWithoutSaving(onExit) },
            onDismiss = { viewModel.dismissExitDialog() }
        )
    }

    // 按键绑定选择器对话框
    showKeySelector?.let { button ->
        KeyBindingDialog(
            initialGamepadMode = button.keycode.type == ControlData.KeyType.GAMEPAD,
            onKeySelected = { keyCode, _ -> viewModel.updateButtonKeycode(button, keyCode) },
            onDismiss = { viewModel.dismissKeySelector() }
        )
    }

    // 摇杆键位映射对话框
    showJoystickKeyMapping?.let { joystick ->
        JoystickKeyMappingDialog(
            currentKeys = joystick.joystickKeys,
            onUpdateKeys = { viewModel.updateJoystickKeys(joystick, it) },
            onDismiss = { viewModel.dismissJoystickKeyMapping() }
        )
    }

    // 编辑器设置对话框
    if (showEditorSettings) {
        EditorSettingsDialog(
            isGridVisible = isGridVisible,
            onToggleGrid = { viewModel.toggleGrid(it) },
            onDismiss = { viewModel.toggleEditorSettings(false) }
        )
    }

    // 纹理选择器对话框
    showTextureSelector?.let { (control, textureType) ->
        TextureSelectorDialog(
            control = control,
            textureType = textureType,
            onUpdateButtonTexture = { btn, type, path, enabled -> 
                viewModel.updateButtonTexture(btn, type, path, enabled) 
            },
            onUpdateJoystickTexture = { js, type, path, enabled -> 
                viewModel.updateJoystickTexture(js, type, path, enabled) 
            },
            onPickImage = { viewModel.requestPickImage() },
            onDismiss = { viewModel.dismissTextureSelector() }
        )
    }
    
    // 多边形编辑器对话框
    showPolygonEditor?.let { button ->
        PolygonEditorDialog(
            currentPoints = button.polygonPoints,
            onConfirm = { points ->
                viewModel.updatePolygonPoints(button, points)
            },
            onDismiss = { viewModel.dismissPolygonEditor() }
        )
    }
}

@Composable
fun FloatingBall(
    isExpanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isExpanded) 1.15f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "ballScale"
    )

    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "ballRotation"
    )

    Box(
        modifier = modifier
            .size(40.dp)  // 缩小悬浮球尺寸
            .scale(scale)
            .graphicsLayer { rotationZ = rotation }
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.primaryContainer
                    )
                )
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_ral),
            contentDescription = "菜单",
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(20.dp)  // 缩小图标
        )
        
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Transparent,
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
            ),
            shape = RoundedCornerShape(10.dp)
        ) {}
    }
}

@Composable
fun ActionWindowMenu(
    isPaletteVisible: Boolean,
    isGhostMode: Boolean,
    isGridVisible: Boolean = true,
    onTogglePalette: () -> Unit,
    onToggleGhostMode: () -> Unit,
    onToggleGrid: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onSave: () -> Unit,
    onCloseMenu: () -> Unit,
    onExit: () -> Unit
) {
    Surface(
        modifier = Modifier.width(240.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f),
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "快捷菜单",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(onClick = onCloseMenu, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.ExpandLess, contentDescription = "收起菜单", modifier = Modifier.size(20.dp))
                }
            }
            
            HorizontalDivider(modifier = Modifier.background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)))

            // 菜单选项列表
            MenuRowItem(
                icon = Icons.Default.AddCircle,
                label = "组件库",
                isActive = isPaletteVisible,
                onClick = onTogglePalette
            )
            
            MenuRowItem(
                icon = if (isGhostMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                label = "幽灵模式",
                isActive = isGhostMode,
                onClick = onToggleGhostMode
            )

            MenuRowItem(
                icon = if (isGridVisible) Icons.Default.GridOn else Icons.Default.GridOff,
                label = "网格显示",
                isActive = isGridVisible,
                onClick = onToggleGrid
            )

            MenuRowItem(
                icon = Icons.Default.Settings,
                label = "编辑器设置",
                isActive = false,
                onClick = onOpenSettings
            )

            HorizontalDivider(modifier = Modifier.background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)))

            MenuRowItem(
                icon = Icons.Default.Save,
                label = "保存布局",
                isActive = false,
                onClick = onSave,
                tint = MaterialTheme.colorScheme.primary
            )

            MenuRowItem(
                icon = Icons.Default.ExitToApp,
                label = "退出编辑器",
                isActive = false,
                onClick = onExit,
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
fun MenuRowItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    tint: Color = Color.Unspecified
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) 
                else Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(20.dp),
                tint = if (isActive) MaterialTheme.colorScheme.primary else if (tint != Color.Unspecified) tint else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            if (isActive) {
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
            }
        }
    }
}

@Composable
fun ComponentPalette(
    onAddControl: (String) -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(240.dp) // 与菜单窗口宽度保持一致
            .wrapContentHeight(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f),
        tonalElevation = 12.dp,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("组件库", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "关闭", modifier = Modifier.size(16.dp))
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PaletteItem(Icons.Default.RadioButtonChecked, "按钮", "button", onAddControl)
                PaletteItem(Icons.Default.Games, "摇杆", "joystick", onAddControl)
                PaletteItem(Icons.Default.TouchApp, "触控", "touchpad", onAddControl)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PaletteItem(Icons.Default.Mouse, "滚轮", "mousewheel", onAddControl)
                PaletteItem(Icons.Default.TextFields, "文本", "text", onAddControl)
                PaletteItem(Icons.Default.DonutLarge, "轮盘", "radialmenu", onAddControl)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PaletteItem(Icons.Default.Gamepad, "十字键", "dpad", onAddControl)
            }
        }
    }
}

@Composable
fun PaletteItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    type: String,
    onAdd: (String) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onAdd(type) }
            .padding(4.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.padding(12.dp).size(24.dp))
        }
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
    }
}

/**
 * DPad 按键选择行
 */
@Composable
fun DPadKeyRow(
    label: String,
    keycode: ControlData.KeyCode,
    onKeycodeChange: (ControlData.KeyCode) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        
        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(
                    keycode.name
                        .removePrefix("KEYBOARD_")
                        .removePrefix("XBOX_BUTTON_"),
                    maxLines = 1
                )
            }
            
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.heightIn(max = 300.dp)
            ) {
                // 常用方向键
                val commonKeys = listOf<Pair<ControlData.KeyCode, String>>(
                    ControlData.KeyCode.KEYBOARD_W to "W",
                    ControlData.KeyCode.KEYBOARD_A to "A",
                    ControlData.KeyCode.KEYBOARD_S to "S",
                    ControlData.KeyCode.KEYBOARD_D to "D",
                    ControlData.KeyCode.KEYBOARD_UP to "↑ 上",
                    ControlData.KeyCode.KEYBOARD_DOWN to "↓ 下",
                    ControlData.KeyCode.KEYBOARD_LEFT to "← 左",
                    ControlData.KeyCode.KEYBOARD_RIGHT to "→ 右",
                    ControlData.KeyCode.XBOX_BUTTON_DPAD_UP to "手柄 ↑",
                    ControlData.KeyCode.XBOX_BUTTON_DPAD_DOWN to "手柄 ↓",
                    ControlData.KeyCode.XBOX_BUTTON_DPAD_LEFT to "手柄 ←",
                    ControlData.KeyCode.XBOX_BUTTON_DPAD_RIGHT to "手柄 →"
                )
                
                commonKeys.forEach { pair ->
                    val code = pair.first
                    val name = pair.second
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = {
                            onKeycodeChange(code)
                            expanded = false
                        },
                        leadingIcon = {
                            if (code == keycode) {
                                Icon(Icons.Default.Check, contentDescription = null)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PropertyPanel(
    control: ControlData?,
    onUpdate: (ControlData) -> Unit,
    onClose: () -> Unit,
    onOpenKeySelector: ((ControlData.Button) -> Unit)? = null,
    onOpenJoystickKeyMapping: ((ControlData.Joystick) -> Unit)? = null,
    onOpenTextureSelector: ((ControlData, String) -> Unit)? = null,
    onOpenPolygonEditor: ((ControlData.Button) -> Unit)? = null,
    onDrag: ((androidx.compose.ui.geometry.Offset) -> Unit)? = null,
    onDuplicate: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .width(320.dp)
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f),
        tonalElevation = 12.dp,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 可拖动的标题栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (onDrag != null) {
                            Modifier.pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    onDrag(androidx.compose.ui.geometry.Offset(dragAmount.x, dragAmount.y))
                                }
                            }
                        } else Modifier
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (onDrag != null) {
                        Icon(
                            Icons.Default.DragIndicator,
                            contentDescription = "拖动",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "属性编辑",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // 复制按钮
                    if (onDuplicate != null) {
                        IconButton(onClick = onDuplicate) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "复制控件")
                        }
                    }
                    // 关闭按钮
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }
            }

            if (control != null) {
                PropertySection(title = "基础设置") {
                    OutlinedTextField(
                        value = control.name,
                        onValueChange = { 
                            val updated = control.deepCopy().apply { name = it }
                            onUpdate(updated)
                        },
                        label = { Text("控件名称") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )

                    PropertySlider(
                        label = "背景透明度",
                        value = control.opacity,
                        onValueChange = { 
                            val updated = control.deepCopy().apply { opacity = it }
                            onUpdate(updated)
                        }
                    )
                }

                // ===== 尺寸与位置 =====
                PropertySection(title = "尺寸与位置") {
                    // X 坐标滑块
                    PropertySlider(
                        label = "X 位置",
                        value = control.x,
                        onValueChange = { 
                            val updated = control.deepCopy().apply { x = it }
                            onUpdate(updated)
                        }
                    )
                    // Y 坐标滑块
                    PropertySlider(
                        label = "Y 位置",
                        value = control.y,
                        onValueChange = { 
                            val updated = control.deepCopy().apply { y = it }
                            onUpdate(updated)
                        }
                    )
                    // 锁定宽高比按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("锁定宽高比", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = control.isSizeRatioLocked,
                            onCheckedChange = {
                                val updated = control.deepCopy().apply { 
                                    isSizeRatioLocked = it 
                                }
                                onUpdate(updated)
                            }
                        )
                    }
                    
                    // 宽度滑块
                    PropertySlider(
                        label = "宽度",
                        value = control.width,
                        valueRange = 0.02f..0.5f,
                        onValueChange = { newWidth ->
                            val updated = control.deepCopy().apply { 
                                width = newWidth
                                // 如果锁定宽高比，同步修改高度
                                if (isSizeRatioLocked) {
                                    height = newWidth
                                }
                            }
                            onUpdate(updated)
                        }
                    )
                    
                    // 高度滑块
                    PropertySlider(
                        label = "高度",
                        value = control.height,
                        valueRange = 0.02f..0.5f,
                        onValueChange = { newHeight ->
                            val updated = control.deepCopy().apply { 
                                height = newHeight
                                // 如果锁定宽高比，同步修改宽度
                                if (isSizeRatioLocked) {
                                    width = newHeight
                                }
                            }
                            onUpdate(updated)
                        }
                    )
                    PropertySlider(
                        label = "旋转角度",
                        value = control.rotation / 360f,
                        onValueChange = {
                            val updated = control.deepCopy().apply { rotation = it * 360f }
                            onUpdate(updated)
                        }
                    )
                }

                when (control) {
                    is ControlData.Button -> {
                        PropertySection(title = "按钮设置") {
                            // 按键选择
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("绑定按键", style = MaterialTheme.typography.bodyMedium)
                                OutlinedButton(
                                    onClick = { onOpenKeySelector?.invoke(control) }
                                ) {
                                    Text(control.keycode.name.removePrefix("KEYBOARD_").removePrefix("MOUSE_").removePrefix("GAMEPAD_"))
                                }
                            }
                            
                            // 输入模式选择
                            Text("输入模式", style = MaterialTheme.typography.labelMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = control.mode == ControlData.Button.Mode.KEYBOARD,
                                    onClick = {
                                        val updated = control.deepCopy() as ControlData.Button
                                        updated.mode = ControlData.Button.Mode.KEYBOARD
                                        onUpdate(updated)
                                    },
                                    label = { Text("键盘") }
                                )
                                FilterChip(
                                    selected = control.mode == ControlData.Button.Mode.GAMEPAD,
                                    onClick = {
                                        val updated = control.deepCopy() as ControlData.Button
                                        updated.mode = ControlData.Button.Mode.GAMEPAD
                                        onUpdate(updated)
                                    },
                                    label = { Text("手柄") }
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("切换模式 (Toggle)", style = MaterialTheme.typography.bodyMedium)
                                    Text("按下后保持状态", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = control.isToggle,
                                    onCheckedChange = {
                                        val updated = control.deepCopy() as ControlData.Button
                                        updated.isToggle = it
                                        onUpdate(updated)
                                    }
                                )
                            }

                            Text("形状", style = MaterialTheme.typography.labelMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = control.shape == ControlData.Button.Shape.RECTANGLE,
                                    onClick = {
                                        val updated = control.deepCopy() as ControlData.Button
                                        updated.shape = ControlData.Button.Shape.RECTANGLE
                                        onUpdate(updated)
                                    },
                                    label = { Text("矩形") }
                                )
                                FilterChip(
                                    selected = control.shape == ControlData.Button.Shape.CIRCLE,
                                    onClick = {
                                        val updated = control.deepCopy() as ControlData.Button
                                        updated.shape = ControlData.Button.Shape.CIRCLE
                                        onUpdate(updated)
                                    },
                                    label = { Text("圆形") }
                                )
                                FilterChip(
                                    selected = control.shape == ControlData.Button.Shape.POLYGON,
                                    onClick = {
                                        val updated = control.deepCopy() as ControlData.Button
                                        updated.shape = ControlData.Button.Shape.POLYGON
                                        // 如果没有多边形点，设置默认三角形
                                        if (updated.polygonPoints.isEmpty()) {
                                            updated.polygonPoints = listOf(
                                                ControlData.Button.Point(0.5f, 0.1f),
                                                ControlData.Button.Point(0.9f, 0.9f),
                                                ControlData.Button.Point(0.1f, 0.9f)
                                            )
                                        }
                                        onUpdate(updated)
                                    },
                                    label = { Text("多边形") }
                                )
                            }
                            
                            // 多边形编辑按钮
                            if (control.shape == ControlData.Button.Shape.POLYGON) {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = { onOpenPolygonEditor?.invoke(control) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("编辑多边形 (${control.polygonPoints.size}个顶点)")
                                }
                            }
                        }

                        // 按钮纹理设置
                        PropertySection(title = "纹理") {
                            TextureSettingItem(
                                label = "普通状态",
                                hasTexture = control.texture.normal.enabled,
                                onClick = { onOpenTextureSelector?.invoke(control, "normal") }
                            )
                            TextureSettingItem(
                                label = "按下状态",
                                hasTexture = control.texture.pressed.enabled,
                                onClick = { onOpenTextureSelector?.invoke(control, "pressed") }
                            )
                            if (control.isToggle) {
                                TextureSettingItem(
                                    label = "切换状态",
                                    hasTexture = control.texture.toggled.enabled,
                                    onClick = { onOpenTextureSelector?.invoke(control, "toggled") }
                                )
                            }
                            
                            // 透明纹理点击区域开关
                            if (control.texture.normal.enabled) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("自定义形状", style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            "使用纹理透明度作为控件形状，透明区域不响应点击",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = control.useTextureAlphaHitTest,
                                        onCheckedChange = {
                                            val updated = control.deepCopy() as ControlData.Button
                                            updated.useTextureAlphaHitTest = it
                                            onUpdate(updated)
                                        }
                                    )
                                }
                            }
                        }
                    }
                    is ControlData.Joystick -> {
                        PropertySection(title = "摇杆设置") {
                            // 键位映射按钮
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("键位映射", style = MaterialTheme.typography.bodyMedium)
                                OutlinedButton(
                                    onClick = { onOpenJoystickKeyMapping?.invoke(control) }
                                ) {
                                    Icon(Icons.Default.Gamepad, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("设置")
                                }
                            }

                            PropertySlider(
                                label = "摇杆球大小",
                                value = control.stickKnobSize,
                                onValueChange = {
                                    val updated = control.deepCopy() as ControlData.Joystick
                                    updated.stickKnobSize = it
                                    onUpdate(updated)
                                }
                            )
                            
                            PropertySlider(
                                label = "摇杆球透明度",
                                value = control.stickOpacity,
                                onValueChange = {
                                    val updated = control.deepCopy() as ControlData.Joystick
                                    updated.stickOpacity = it
                                    onUpdate(updated)
                                }
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("右摇杆模式", style = MaterialTheme.typography.bodyMedium)
                                    Text("手柄模式下用于右摇杆", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = control.isRightStick,
                                    onCheckedChange = {
                                        val updated = control.deepCopy() as ControlData.Joystick
                                        updated.isRightStick = it
                                        onUpdate(updated)
                                    }
                                )
                            }

                            // 摇杆模式选择
                            Text("输入模式", style = MaterialTheme.typography.labelMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = control.mode == ControlData.Joystick.Mode.KEYBOARD,
                                    onClick = {
                                        val updated = control.deepCopy() as ControlData.Joystick
                                        updated.mode = ControlData.Joystick.Mode.KEYBOARD
                                        onUpdate(updated)
                                    },
                                    label = { Text("键盘") }
                                )
                                FilterChip(
                                    selected = control.mode == ControlData.Joystick.Mode.GAMEPAD,
                                    onClick = {
                                        val updated = control.deepCopy() as ControlData.Joystick
                                        updated.mode = ControlData.Joystick.Mode.GAMEPAD
                                        onUpdate(updated)
                                    },
                                    label = { Text("手柄") }
                                )
                                FilterChip(
                                    selected = control.mode == ControlData.Joystick.Mode.MOUSE,
                                    onClick = {
                                        val updated = control.deepCopy() as ControlData.Joystick
                                        updated.mode = ControlData.Joystick.Mode.MOUSE
                                        onUpdate(updated)
                                    },
                                    label = { Text("鼠标") }
                                )
                            }
                            
                            // 鼠标模式下显示速度和范围设置
                            if (control.mode == ControlData.Joystick.Mode.MOUSE) {
                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider()
                                Spacer(modifier = Modifier.height(8.dp))
                                MouseModeSettings()
                            }
                        }

                        // 摇杆纹理设置
                        PropertySection(title = "纹理") {
                            TextureSettingItem(
                                label = "背景",
                                hasTexture = control.texture.background.enabled,
                                onClick = { onOpenTextureSelector?.invoke(control, "background") }
                            )
                            TextureSettingItem(
                                label = "摇杆球",
                                hasTexture = control.texture.knob.enabled,
                                onClick = { onOpenTextureSelector?.invoke(control, "knob") }
                            )
                        }
                    }
                    
                    is ControlData.TouchPad -> {
                        PropertySection(title = "触控板设置") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("双指点击模拟摇杆", style = MaterialTheme.typography.bodyMedium)
                                    Text("双指点击时模拟摇杆移动", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = control.isDoubleClickSimulateJoystick,
                                    onCheckedChange = {
                                        val updated = control.deepCopy() as ControlData.TouchPad
                                        updated.isDoubleClickSimulateJoystick = it
                                        onUpdate(updated)
                                    }
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // 触控板鼠标速度和范围设置
                            MouseModeSettings()
                        }
                        
                        // 触控板纹理设置
                        PropertySection(title = "纹理") {
                            TextureSettingItem(
                                label = "背景",
                                hasTexture = control.texture.background.enabled,
                                onClick = { onOpenTextureSelector?.invoke(control, "background") }
                            )
                        }
                    }
                    
                    is ControlData.MouseWheel -> {
                        PropertySection(title = "滚轮设置") {
                            // 滚轮方向
                            Text("滚轮方向", style = MaterialTheme.typography.labelMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = control.orientation == ControlData.MouseWheel.Orientation.VERTICAL,
                                    onClick = {
                                        val updated = control.deepCopy() as ControlData.MouseWheel
                                        updated.orientation = ControlData.MouseWheel.Orientation.VERTICAL
                                        onUpdate(updated)
                                    },
                                    label = { Text("垂直") }
                                )
                                FilterChip(
                                    selected = control.orientation == ControlData.MouseWheel.Orientation.HORIZONTAL,
                                    onClick = {
                                        val updated = control.deepCopy() as ControlData.MouseWheel
                                        updated.orientation = ControlData.MouseWheel.Orientation.HORIZONTAL
                                        onUpdate(updated)
                                    },
                                    label = { Text("水平") }
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("反转滚动方向", style = MaterialTheme.typography.bodyMedium)
                                    Text("上滑变下滚，左滑变右滚", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = control.reverseDirection,
                                    onCheckedChange = {
                                        val updated = control.deepCopy() as ControlData.MouseWheel
                                        updated.reverseDirection = it
                                        onUpdate(updated)
                                    }
                                )
                            }
                            
                            PropertySlider(
                                label = "灵敏度",
                                value = (control.scrollSensitivity - 10f) / 90f, // 范围 10-100
                                onValueChange = {
                                    val updated = control.deepCopy() as ControlData.MouseWheel
                                    updated.scrollSensitivity = 10f + it * 90f
                                    onUpdate(updated)
                                }
                            )
                            
                            PropertySlider(
                                label = "速度倍率",
                                value = (control.scrollRatio - 0.1f) / 4.9f, // 范围 0.1-5.0
                                onValueChange = {
                                    val updated = control.deepCopy() as ControlData.MouseWheel
                                    updated.scrollRatio = 0.1f + it * 4.9f
                                    onUpdate(updated)
                                }
                            )
                        }
                        
                        // 滚轮纹理设置
                        PropertySection(title = "纹理") {
                            TextureSettingItem(
                                label = "背景",
                                hasTexture = control.texture.background.enabled,
                                onClick = { onOpenTextureSelector?.invoke(control, "background") }
                            )
                        }
                    }
                    
                    is ControlData.Text -> {
                        PropertySection(title = "文本设置") {
                            OutlinedTextField(
                                value = control.displayText,
                                onValueChange = { 
                                    val updated = control.deepCopy() as ControlData.Text
                                    updated.displayText = it
                                    onUpdate(updated)
                                },
                                label = { Text("显示文本") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text("形状", style = MaterialTheme.typography.labelMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = control.shape == ControlData.Text.Shape.RECTANGLE,
                                    onClick = {
                                        val updated = control.deepCopy() as ControlData.Text
                                        updated.shape = ControlData.Text.Shape.RECTANGLE
                                        onUpdate(updated)
                                    },
                                    label = { Text("矩形") }
                                )
                                FilterChip(
                                    selected = control.shape == ControlData.Text.Shape.CIRCLE,
                                    onClick = {
                                        val updated = control.deepCopy() as ControlData.Text
                                        updated.shape = ControlData.Text.Shape.CIRCLE
                                        onUpdate(updated)
                                    },
                                    label = { Text("圆形") }
                                )
                            }
                        }
                        
                        // 文本纹理设置
                        PropertySection(title = "纹理") {
                            TextureSettingItem(
                                label = "背景",
                                hasTexture = control.texture.background.enabled,
                                onClick = { onOpenTextureSelector?.invoke(control, "background") }
                            )
                        }
                    }
                    
                    is ControlData.RadialMenu -> {
                        PropertySection(title = "轮盘设置") {
                            // 扇区数量
                            Text("扇区数量: ${control.sectorCount}", style = MaterialTheme.typography.labelMedium)
                            Slider(
                                value = control.sectorCount.toFloat(),
                                onValueChange = {
                                    val updated = control.deepCopy() as ControlData.RadialMenu
                                    updated.sectorCount = it.toInt().coerceIn(4, 12)
                                    // 确保 sectors 列表大小匹配
                                    while (updated.sectors.size < updated.sectorCount) {
                                        updated.sectors.add(ControlData.RadialMenu.Sector(
                                            keycode = ControlData.KeyCode.UNKNOWN,
                                            label = "${updated.sectors.size + 1}"
                                        ))
                                    }
                                    onUpdate(updated)
                                },
                                valueRange = 4f..12f,
                                steps = 7
                            )
                            
                            // 展开倍数
                            PropertySlider(
                                label = "展开大小",
                                value = control.expandedScale / 4f,
                                onValueChange = { newValue ->
                                    val updated = control.deepCopy() as ControlData.RadialMenu
                                    updated.expandedScale = newValue * 4f
                                    onUpdate(updated)
                                }
                            )
                            
                            // 死区比例
                            PropertySlider(
                                label = "中心死区",
                                value = control.deadZoneRatio,
                                onValueChange = { newValue ->
                                    val updated = control.deepCopy() as ControlData.RadialMenu
                                    updated.deadZoneRatio = newValue
                                    onUpdate(updated)
                                }
                            )
                            
                            // 显示分隔线
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("显示分隔线", style = MaterialTheme.typography.bodyMedium)
                                Switch(
                                    checked = control.showDividers,
                                    onCheckedChange = {
                                        val updated = control.deepCopy() as ControlData.RadialMenu
                                        updated.showDividers = it
                                        onUpdate(updated)
                                    }
                                )
                            }
                        }
                    }
                    
                    is ControlData.DPad -> {
                        PropertySection(title = "十字键设置") {
                            // 样式选择
                            Text("样式", style = MaterialTheme.typography.labelMedium)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ControlData.DPad.Style.entries.forEach { style ->
                                    FilterChip(
                                        selected = control.style == style,
                                        onClick = {
                                            val updated = control.deepCopy() as ControlData.DPad
                                            updated.style = style
                                            onUpdate(updated)
                                        },
                                        label = {
                                            Text(
                                                when (style) {
                                                    ControlData.DPad.Style.CROSS -> "十字"
                                                    ControlData.DPad.Style.ROUND -> "圆形"
                                                    ControlData.DPad.Style.SQUARE -> "方形"
                                                }
                                            )
                                        }
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // 按钮大小
                            PropertySlider(
                                label = "按钮大小",
                                value = control.buttonSize,
                                onValueChange = { newValue ->
                                    val updated = control.deepCopy() as ControlData.DPad
                                    updated.buttonSize = newValue
                                    onUpdate(updated)
                                }
                            )
                            
                            // 按钮间距
                            PropertySlider(
                                label = "按钮间距",
                                value = control.buttonSpacing / 0.2f,
                                onValueChange = { newValue ->
                                    val updated = control.deepCopy() as ControlData.DPad
                                    updated.buttonSpacing = newValue * 0.2f
                                    onUpdate(updated)
                                }
                            )
                            
                            // 死区（仅圆形模式）
                            if (control.style == ControlData.DPad.Style.ROUND) {
                                PropertySlider(
                                    label = "中心死区",
                                    value = control.deadZone / 0.5f,
                                    onValueChange = { newValue ->
                                        val updated = control.deepCopy() as ControlData.DPad
                                        updated.deadZone = newValue * 0.5f
                                        onUpdate(updated)
                                    }
                                )
                            }
                            
                            // 允许斜向
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("允许斜向", style = MaterialTheme.typography.bodyMedium)
                                Switch(
                                    checked = control.allowDiagonal,
                                    onCheckedChange = {
                                        val updated = control.deepCopy() as ControlData.DPad
                                        updated.allowDiagonal = it
                                        onUpdate(updated)
                                    }
                                )
                            }
                            
                            // 显示方向标签
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("显示方向标签", style = MaterialTheme.typography.bodyMedium)
                                Switch(
                                    checked = control.showLabels,
                                    onCheckedChange = {
                                        val updated = control.deepCopy() as ControlData.DPad
                                        updated.showLabels = it
                                        onUpdate(updated)
                                    }
                                )
                            }
                        }
                        
                        // 按键绑定
                        PropertySection(title = "方向按键") {
                            // 上
                            DPadKeyRow(
                                label = "↑ 上",
                                keycode = control.upKeycode,
                                onKeycodeChange = { keycode ->
                                    val updated = control.deepCopy() as ControlData.DPad
                                    updated.upKeycode = keycode
                                    onUpdate(updated)
                                }
                            )
                            
                            // 下
                            DPadKeyRow(
                                label = "↓ 下",
                                keycode = control.downKeycode,
                                onKeycodeChange = { keycode ->
                                    val updated = control.deepCopy() as ControlData.DPad
                                    updated.downKeycode = keycode
                                    onUpdate(updated)
                                }
                            )
                            
                            // 左
                            DPadKeyRow(
                                label = "← 左",
                                keycode = control.leftKeycode,
                                onKeycodeChange = { keycode ->
                                    val updated = control.deepCopy() as ControlData.DPad
                                    updated.leftKeycode = keycode
                                    onUpdate(updated)
                                }
                            )
                            
                            // 右
                            DPadKeyRow(
                                label = "→ 右",
                                keycode = control.rightKeycode,
                                onKeycodeChange = { keycode ->
                                    val updated = control.deepCopy() as ControlData.DPad
                                    updated.rightKeycode = keycode
                                    onUpdate(updated)
                                }
                            )
                        }
                        
                        // 输入模式选择
                        PropertySection(title = "输入模式") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = control.mode == ControlData.Button.Mode.KEYBOARD,
                                    onClick = {
                                        val updated = control.deepCopy() as ControlData.DPad
                                        updated.mode = ControlData.Button.Mode.KEYBOARD
                                        onUpdate(updated)
                                    },
                                    label = { Text("键盘") }
                                )
                                FilterChip(
                                    selected = control.mode == ControlData.Button.Mode.GAMEPAD,
                                    onClick = {
                                        val updated = control.deepCopy() as ControlData.DPad
                                        updated.mode = ControlData.Button.Mode.GAMEPAD
                                        onUpdate(updated)
                                    },
                                    label = { Text("手柄") }
                                )
                            }
                        }
                    }
                }

                // ===== 外观设置 =====
                PropertySection(title = "外观") {
                    // 背景颜色选择
                    ColorPickerRow(
                        label = "背景颜色",
                        color = Color(control.bgColor),
                        onColorSelected = { color ->
                            val updated = control.deepCopy().apply { bgColor = color.toArgb() }
                            onUpdate(updated)
                        }
                    )
                    
                    // 边框颜色选择
                    ColorPickerRow(
                        label = "边框颜色",
                        color = Color(control.strokeColor),
                        onColorSelected = { color ->
                            val updated = control.deepCopy().apply { strokeColor = color.toArgb() }
                            onUpdate(updated)
                        }
                    )
                    
                    // 文本颜色选择
                    ColorPickerRow(
                        label = "文本颜色",
                        color = Color(control.textColor),
                        onColorSelected = { color ->
                            val updated = control.deepCopy().apply { textColor = color.toArgb() }
                            onUpdate(updated)
                        }
                    )
                    
                    PropertySlider(
                        label = "圆角大小",
                        value = control.cornerRadius / 50f,
                        onValueChange = {
                            val updated = control.deepCopy().apply { cornerRadius = it * 50f }
                            onUpdate(updated)
                        }
                    )
                    PropertySlider(
                        label = "边框宽度",
                        value = control.strokeWidth / 10f,
                        onValueChange = {
                            val updated = control.deepCopy().apply { strokeWidth = it * 10f }
                            onUpdate(updated)
                        }
                    )
                    PropertySlider(
                        label = "边框透明度",
                        value = control.borderOpacity,
                        onValueChange = {
                            val updated = control.deepCopy().apply { borderOpacity = it }
                            onUpdate(updated)
                        }
                    )
                    PropertySlider(
                        label = "文字透明度",
                        value = control.textOpacity,
                        onValueChange = {
                            val updated = control.deepCopy().apply { textOpacity = it }
                            onUpdate(updated)
                        }
                    )
                }

                // ===== 高级设置 =====
                PropertySection(title = "高级") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("可见性", style = MaterialTheme.typography.bodyMedium)
                            Text("隐藏后不显示但仍可响应", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = control.isVisible,
                            onCheckedChange = {
                                val updated = control.deepCopy().apply { isVisible = it }
                                onUpdate(updated)
                            }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("穿透模式", style = MaterialTheme.typography.bodyMedium)
                            Text("触摸事件穿透到下层", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = control.isPassThrough,
                            onCheckedChange = {
                                val updated = control.deepCopy().apply { isPassThrough = it }
                                onUpdate(updated)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun PropertySection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.Bold
        )
        content()
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)))
    }
}

@Composable
fun PropertySlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text("${(value * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange
        )
    }
}

/**
 * 鼠标模式设置组件 - 用于摇杆和触控板的鼠标速度和范围设置
 */
@Composable
fun MouseModeSettings() {
    val settingsManager = remember { SettingsManager.getInstance() }
    
    // 速度设置 (60-200，默认200)
    var mouseSpeed by remember { mutableStateOf(settingsManager.mouseRightStickSpeed) }
    // 范围设置 (0.0-1.0)
    var rangeLeft by remember { mutableStateOf(settingsManager.mouseRightStickRangeLeft) }
    var rangeTop by remember { mutableStateOf(settingsManager.mouseRightStickRangeTop) }
    var rangeRight by remember { mutableStateOf(settingsManager.mouseRightStickRangeRight) }
    var rangeBottom by remember { mutableStateOf(settingsManager.mouseRightStickRangeBottom) }
    
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 速度设置
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("移动速度", style = MaterialTheme.typography.labelMedium)
                Text("$mouseSpeed", style = MaterialTheme.typography.labelSmall)
            }
            Slider(
                value = (mouseSpeed - 60f) / 140f, // 60-200 映射到 0-1
                onValueChange = { 
                    mouseSpeed = (60 + (it * 140)).toInt()
                    settingsManager.mouseRightStickSpeed = mouseSpeed
                },
                valueRange = 0f..1f
            )
        }
        
        // 范围设置标题
        Text("移动范围", style = MaterialTheme.typography.labelMedium)
        Text(
            "控制鼠标可移动的屏幕区域（从中心向各方向扩展的比例）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        // 左侧范围
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("左", style = MaterialTheme.typography.labelSmall)
                Text("${(rangeLeft * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
            }
            Slider(
                value = rangeLeft,
                onValueChange = { 
                    rangeLeft = it
                    settingsManager.mouseRightStickRangeLeft = it
                },
                valueRange = 0f..1f
            )
        }
        
        // 上方范围
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("上", style = MaterialTheme.typography.labelSmall)
                Text("${(rangeTop * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
            }
            Slider(
                value = rangeTop,
                onValueChange = { 
                    rangeTop = it
                    settingsManager.mouseRightStickRangeTop = it
                },
                valueRange = 0f..1f
            )
        }
        
        // 右侧范围
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("右", style = MaterialTheme.typography.labelSmall)
                Text("${(rangeRight * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
            }
            Slider(
                value = rangeRight,
                onValueChange = { 
                    rangeRight = it
                    settingsManager.mouseRightStickRangeRight = it
                },
                valueRange = 0f..1f
            )
        }
        
        // 下方范围
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("下", style = MaterialTheme.typography.labelSmall)
                Text("${(rangeBottom * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
            }
            Slider(
                value = rangeBottom,
                onValueChange = { 
                    rangeBottom = it
                    settingsManager.mouseRightStickRangeBottom = it
                },
                valueRange = 0f..1f
            )
        }
        
        // 快捷设置按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SuggestionChip(
                onClick = {
                    rangeLeft = 1f; rangeTop = 1f; rangeRight = 1f; rangeBottom = 1f
                    settingsManager.mouseRightStickRangeLeft = 1f
                    settingsManager.mouseRightStickRangeTop = 1f
                    settingsManager.mouseRightStickRangeRight = 1f
                    settingsManager.mouseRightStickRangeBottom = 1f
                },
                label = { Text("全屏") }
            )
            SuggestionChip(
                onClick = {
                    rangeLeft = 0.5f; rangeTop = 0.5f; rangeRight = 0.5f; rangeBottom = 0.5f
                    settingsManager.mouseRightStickRangeLeft = 0.5f
                    settingsManager.mouseRightStickRangeTop = 0.5f
                    settingsManager.mouseRightStickRangeRight = 0.5f
                    settingsManager.mouseRightStickRangeBottom = 0.5f
                },
                label = { Text("半屏") }
            )
        }
    }
}

/**
 * 颜色选择行 - 显示颜色预览和触发颜色选择器
 * 使用项目已有的 ColorPickerDialog
 */
@Composable
fun ColorPickerRow(
    label: String,
    color: Color,
    onColorSelected: (Color) -> Unit
) {
    var showColorPicker by remember { mutableStateOf(false) }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 显示颜色十六进制值
            Text(
                text = String.format("#%08X", color.toArgb()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // 颜色预览按钮
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(color)
                    .clickable { showColorPicker = true }
            )
        }
    }
    
    if (showColorPicker) {
        // 使用共享模块中的 ColorPickerDialog
        com.app.ralaunch.shared.ui.components.dialogs.ColorPickerDialog(
            currentColor = color.toArgb(),
            onSelect = { selectedColor ->
                onColorSelected(Color(selectedColor))
            },
            onDismiss = { showColorPicker = false }
        )
    }
}

// ==================== 对话框实现 ====================

/**
 * 退出确认对话框
 */
@Composable
fun ExitConfirmDialog(
    onSaveAndExit: () -> Unit,
    onExitWithoutSaving: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("退出编辑器", fontWeight = FontWeight.Bold) },
        text = { Text("您有未保存的更改，是否保存后退出？") },
        confirmButton = {
            Button(
                onClick = onSaveAndExit,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("保存并退出")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
                OutlinedButton(
                    onClick = onExitWithoutSaving,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("不保存")
                }
            }
        }
    )
}

/**
 * 摇杆键位映射对话框
 */
@Composable
fun JoystickKeyMappingDialog(
    currentKeys: Array<ControlData.KeyCode>,
    onUpdateKeys: (Array<ControlData.KeyCode>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val directions = listOf("↑ 上", "→ 右", "↓ 下", "← 左")
    
    // 初始化可变状态
    var keys by remember { mutableStateOf(currentKeys.clone()) }
    // 当前正在设置的方向索引
    var selectingDirectionIndex by remember { mutableStateOf<Int?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("摇杆键位映射", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                
                // 快捷设置
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SuggestionChip(
                        onClick = { 
                            keys = arrayOf(
                                ControlData.KeyCode.KEYBOARD_W,
                                ControlData.KeyCode.KEYBOARD_D,
                                ControlData.KeyCode.KEYBOARD_S,
                                ControlData.KeyCode.KEYBOARD_A
                            )
                        },
                        label = { Text("WASD") }
                    )
                    SuggestionChip(
                        onClick = { 
                            keys = arrayOf(
                                ControlData.KeyCode.KEYBOARD_UP,
                                ControlData.KeyCode.KEYBOARD_RIGHT,
                                ControlData.KeyCode.KEYBOARD_DOWN,
                                ControlData.KeyCode.KEYBOARD_LEFT
                            )
                        },
                        label = { Text("方向键 ↑←↓→") }
                    )
                }

                HorizontalDivider()

                // 四个方向的键位设置
                directions.forEachIndexed { index, direction ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(direction, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        
                        val keyCode = keys.getOrNull(index)
                        val displayName = keyCode?.let { KeyMapper.getKeyName(context, it) } ?: "未设置"
                        
                        OutlinedButton(
                            onClick = { selectingDirectionIndex = index },
                            colors = if (selectingDirectionIndex == index) {
                                ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            } else {
                                ButtonDefaults.outlinedButtonColors()
                            }
                        ) {
                            Text(displayName)
                        }
                    }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onUpdateKeys(keys) }) {
                        Text("确定")
                    }
                }
            }
        }
    }
    
    // 按键选择器对话框
    selectingDirectionIndex?.let { dirIndex ->
        KeyBindingDialog(
            initialGamepadMode = false,
            onKeySelected = { keyCode, _ ->
                val newKeys = keys.clone()
                newKeys[dirIndex] = keyCode
                keys = newKeys
                selectingDirectionIndex = null
            },
            onDismiss = { selectingDirectionIndex = null }
        )
    }
}

/**
 * 编辑器设置对话框
 */
@Composable
fun EditorSettingsDialog(
    isGridVisible: Boolean,
    onToggleGrid: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("编辑器设置", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }

                HorizontalDivider()

                // 网格显示开关
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("显示网格", style = MaterialTheme.typography.bodyLarge)
                        Text("辅助对齐控件位置", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = isGridVisible,
                        onCheckedChange = onToggleGrid
                    )
                }

                // 吸附阈值 (未来可添加)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("吸附阈值", style = MaterialTheme.typography.bodyLarge)
                        Text("控件自动对齐灵敏度", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("10px", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("完成")
                }
            }
        }
    }
}

/**
 * 纹理设置项组件
 */
@Composable
fun TextureSettingItem(
    label: String,
    hasTexture: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (hasTexture) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (hasTexture) Icons.Default.Image else Icons.Default.ImageNotSupported,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (hasTexture) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(label, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                text = if (hasTexture) "已设置" else "未设置",
                style = MaterialTheme.typography.labelSmall,
                color = if (hasTexture) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 纹理选择器对话框 (简化版)
 * - 启用/禁用开关
 * - 从相册/文件选择图片按钮
 */
@Composable
fun TextureSelectorDialog(
    control: ControlData,
    textureType: String,
    onUpdateButtonTexture: (ControlData.Button, String, String, Boolean) -> Unit,
    onUpdateJoystickTexture: (ControlData.Joystick, String, String, Boolean) -> Unit,
    onPickImage: () -> Unit,
    onDismiss: () -> Unit
) {
    // 获取当前纹理配置
    val currentConfig = remember(control, textureType) {
        when (control) {
            is ControlData.Button -> when (textureType) {
                "normal" -> control.texture.normal
                "pressed" -> control.texture.pressed
                "toggled" -> control.texture.toggled
                else -> null
            }
            is ControlData.Joystick -> when (textureType) {
                "background" -> control.texture.background
                "knob" -> control.texture.knob
                "backgroundPressed" -> control.texture.backgroundPressed
                "knobPressed" -> control.texture.knobPressed
                else -> null
            }
            else -> null
        }
    }

    var textureEnabled by remember { mutableStateOf(currentConfig?.enabled ?: false) }
    val currentPath = currentConfig?.path ?: ""

    val textureTypeName = when (textureType) {
        "normal" -> "普通状态"
        "pressed" -> "按下状态"
        "toggled" -> "切换状态"
        "background" -> "背景"
        "knob" -> "摇杆球"
        "backgroundPressed" -> "按下背景"
        "knobPressed" -> "按下摇杆球"
        else -> textureType
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 标题
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("自定义纹理", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(textureTypeName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }

                HorizontalDivider()

                // 启用开关
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("启用自定义纹理", style = MaterialTheme.typography.bodyLarge)
                        Text("替换默认控件外观", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = textureEnabled,
                        onCheckedChange = { textureEnabled = it }
                    )
                }

                // 当前纹理状态
                if (currentPath.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column(modifier = Modifier.weight(1f)) {
                                Text("已设置纹理", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(currentPath.substringAfterLast("/"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                // 选择图片按钮
                Button(
                    onClick = onPickImage,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = textureEnabled
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (currentPath.isEmpty()) "选择图片" else "更换图片")
                }

                // 清除按钮
                if (currentPath.isNotEmpty()) {
                    OutlinedButton(
                        onClick = {
                            when (control) {
                                is ControlData.Button -> onUpdateButtonTexture(control, textureType, "", false)
                                is ControlData.Joystick -> onUpdateJoystickTexture(control, textureType, "", false)
                                else -> {}
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("清除纹理")
                    }
                }

                HorizontalDivider()

                // 底部按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("关闭")
                    }
                }
            }
        }
    }
}
