package com.app.ralaunch.feature.controls.editors.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.app.ralaunch.feature.controls.ControlData
import com.app.ralaunch.feature.controls.editors.ControlEditorViewModel
import com.app.ralaunch.feature.controls.packs.ControlLayout
import com.app.ralaunch.feature.controls.packs.SubLayout
import com.app.ralaunch.feature.controls.views.ControlLayout as ControlLayoutView
import com.app.ralaunch.feature.controls.views.GridOverlayView
import com.app.ralaunch.feature.controls.bridges.DummyInputBridge
import com.app.ralaunch.core.ui.dialog.KeyBindingDialog
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
    val subLayouts by viewModel.subLayouts.collectAsState()
    val activeEditTarget by viewModel.activeEditTarget.collectAsState()
    
    // 属性面板偏移量（可拖动）
    var propertyPanelOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    
    // 子布局管理对话框
    var showAddSubLayoutDialog by remember { mutableStateOf(false) }
    var showRenameSubLayoutDialog by remember { mutableStateOf<SubLayout?>(null) }
    var showDeleteSubLayoutDialog by remember { mutableStateOf<SubLayout?>(null) }

    // 处理返回键
    BackHandler {
        if (!viewModel.requestExit()) {
            // 会显示退出确认对话框
        } else {
            onExit()
        }
    }

    // 编辑器背景固定用深灰色，模拟游戏中的深色背景
    // 不受浅色/深色主题影响，确保控件（通常为浅色边框/文字）始终清晰可见
    val backgroundColor = androidx.compose.ui.graphics.Color(0xFF2D2D2D)

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

        // Layer 1: 网格辅助线
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

        // Layer 1.5: 子布局标签栏（顶部居中）
        SubLayoutTabBar(
            subLayouts = subLayouts,
            activeEditTarget = activeEditTarget,
            onSwitchTarget = { viewModel.switchEditTarget(it) },
            onAddSubLayout = { showAddSubLayoutDialog = true },
            onRenameSubLayout = { showRenameSubLayoutDialog = it },
            onDeleteSubLayout = { showDeleteSubLayoutDialog = it },
            modifier = Modifier.align(Alignment.TopCenter)
        )

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
            
            // 1. 悬浮球
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

            // 2. 菜单与组件库
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 12.dp)
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
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

                AnimatedVisibility(
                    visible = isPaletteVisible && isMenuExpanded,
                    enter = slideInHorizontally(initialOffsetX = { -20 }) + fadeIn(),
                    exit = slideOutHorizontally(targetOffsetX = { -20 }) + fadeOut()
                ) {
                    ComponentPalette(
                        onAddControl = { viewModel.addNewControl(it) },
                        onClose = { viewModel.togglePalette(false) }
                    )
                }
            }

            // 右侧属性面板
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

    if (showExitDialog) {
        ExitConfirmDialog(
            onSaveAndExit = { viewModel.saveAndExit(onExit) },
            onExitWithoutSaving = { viewModel.exitWithoutSaving(onExit) },
            onDismiss = { viewModel.dismissExitDialog() }
        )
    }

    showKeySelector?.let { button ->
        KeyBindingDialog(
            initialGamepadMode = button.keycode.type == ControlData.KeyType.GAMEPAD,
            onKeySelected = { keyCode, _ -> viewModel.updateButtonKeycode(button, keyCode) },
            onDismiss = { viewModel.dismissKeySelector() }
        )
    }

    showJoystickKeyMapping?.let { joystick ->
        JoystickKeyMappingDialog(
            currentKeys = joystick.joystickKeys,
            onUpdateKeys = { viewModel.updateJoystickKeys(joystick, it) },
            onDismiss = { viewModel.dismissJoystickKeyMapping() }
        )
    }

    if (showEditorSettings) {
        EditorSettingsDialog(
            isGridVisible = isGridVisible,
            onToggleGrid = { viewModel.toggleGrid(it) },
            onDismiss = { viewModel.toggleEditorSettings(false) }
        )
    }

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
    
    showPolygonEditor?.let { button ->
        PolygonEditorDialog(
            currentPoints = button.polygonPoints,
            onConfirm = { points ->
                viewModel.updatePolygonPoints(button, points)
            },
            onDismiss = { viewModel.dismissPolygonEditor() }
        )
    }
    
    // ========== 子布局管理对话框 ==========
    
    if (showAddSubLayoutDialog) {
        SubLayoutNameDialog(
            title = "添加子布局",
            initialName = "",
            onConfirm = { name ->
                viewModel.addSubLayout(name)
                showAddSubLayoutDialog = false
            },
            onDismiss = { showAddSubLayoutDialog = false }
        )
    }
    
    showRenameSubLayoutDialog?.let { subLayout ->
        SubLayoutNameDialog(
            title = "重命名子布局",
            initialName = subLayout.name,
            onConfirm = { name ->
                viewModel.renameSubLayout(subLayout.id, name)
                showRenameSubLayoutDialog = null
            },
            onDismiss = { showRenameSubLayoutDialog = null }
        )
    }
    
    showDeleteSubLayoutDialog?.let { subLayout ->
        AlertDialog(
            onDismissRequest = { showDeleteSubLayoutDialog = null },
            title = { Text("删除子布局") },
            text = { Text("确定要删除子布局 \"${subLayout.name}\" 吗？其中的所有控件也会被删除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSubLayout(subLayout.id)
                        showDeleteSubLayoutDialog = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSubLayoutDialog = null }) { Text("取消") }
            }
        )
    }
}

// ========== 子布局标签栏 ==========

/**
 * 子布局编辑标签栏
 * 显示在编辑器顶部，用于在共享控件和各子布局之间切换编辑目标
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubLayoutTabBar(
    subLayouts: List<SubLayout>,
    activeEditTarget: ControlEditorViewModel.EditTarget,
    onSwitchTarget: (ControlEditorViewModel.EditTarget) -> Unit,
    onAddSubLayout: () -> Unit,
    onRenameSubLayout: (SubLayout) -> Unit,
    onDeleteSubLayout: (SubLayout) -> Unit,
    modifier: Modifier = Modifier
) {
    // 长按菜单状态
    var longPressedSubLayout by remember { mutableStateOf<SubLayout?>(null) }
    var showContextMenu by remember { mutableStateOf(false) }
    
    Surface(
        modifier = modifier.padding(top = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
        tonalElevation = 4.dp,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 共享控件标签
            val isSharedActive = activeEditTarget is ControlEditorViewModel.EditTarget.Shared
            FilterChip(
                selected = isSharedActive,
                onClick = { onSwitchTarget(ControlEditorViewModel.EditTarget.Shared) },
                label = {
                    Text(
                        text = if (subLayouts.isEmpty()) "所有控件" else "共享控件",
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                leadingIcon = if (isSharedActive) {
                    { Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null,
                modifier = Modifier.height(32.dp)
            )
            
            // 子布局标签
            subLayouts.forEach { subLayout ->
                val isActive = activeEditTarget is ControlEditorViewModel.EditTarget.SubLayoutTarget
                    && (activeEditTarget as ControlEditorViewModel.EditTarget.SubLayoutTarget).subLayoutId == subLayout.id
                
                Box {
                    FilterChip(
                        selected = isActive,
                        onClick = { 
                            onSwitchTarget(
                                ControlEditorViewModel.EditTarget.SubLayoutTarget(subLayout.id)
                            )
                        },
                        label = {
                            Text(
                                text = subLayout.name,
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        modifier = Modifier
                            .height(32.dp)
                            .pointerInput(subLayout.id) {
                                detectDragGestures { _, _ -> } // consume drags
                                // Long press handled via combinedClickable below
                            },
                        trailingIcon = if (isActive) {
                            {
                                IconButton(
                                    onClick = {
                                        longPressedSubLayout = subLayout
                                        showContextMenu = true
                                    },
                                    modifier = Modifier.size(16.dp)
                                ) {
                                    Icon(
                                        Icons.Default.MoreVert,
                                        contentDescription = "更多操作",
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                    
                    // 上下文菜单（三点按钮触发）
                    DropdownMenu(
                        expanded = showContextMenu && longPressedSubLayout?.id == subLayout.id,
                        onDismissRequest = { showContextMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("重命名") },
                            onClick = {
                                showContextMenu = false
                                onRenameSubLayout(subLayout)
                            },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showContextMenu = false
                                onDeleteSubLayout(subLayout)
                            },
                            leadingIcon = { 
                                Icon(
                                    Icons.Default.Delete, 
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        )
                    }
                }
            }
            
            // 添加子布局按钮
            IconButton(
                onClick = onAddSubLayout,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "添加子布局",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * 子布局名称输入对话框
 */
@Composable
private fun SubLayoutNameDialog(
    title: String,
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("名称") },
                placeholder = { Text("如: 建筑、战斗、默认") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank()
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
