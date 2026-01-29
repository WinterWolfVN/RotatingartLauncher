package com.app.ralaunch.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.app.ralaunch.controls.editors.ControlEditorActivity
import org.koin.java.KoinJavaComponent
import com.app.ralaunch.controls.packs.ControlPackInfo
import com.app.ralaunch.controls.packs.ControlPackManager
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * 控制布局管理 Screen - 纯 Composable 版本
 * 
 * 从 ControlLayoutComposeFragment 提取，去除 Fragment 依赖
 */
@Composable
fun ControlLayoutScreenWrapper(
    onBack: () -> Unit = {},
    onOpenStore: () -> Unit = {}
) {
    val context = LocalContext.current
    val packManager = remember { KoinJavaComponent.get<ControlPackManager>(ControlPackManager::class.java) }

    // 状态
    var layouts by remember { mutableStateOf<List<ControlPackInfo>>(emptyList()) }
    var selectedPackId by remember { mutableStateOf<String?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<ControlPackInfo?>(null) }
    var showRenameDialog by remember { mutableStateOf<ControlPackInfo?>(null) }
    var showMoreMenu by remember { mutableStateOf<ControlPackInfo?>(null) }
    var exportingPackId by remember { mutableStateOf<String?>(null) }

    // 加载布局列表
    fun loadLayouts() {
        layouts = packManager.getInstalledPacks()
        selectedPackId = packManager.getSelectedPackId()
    }

    // 初始加载
    LaunchedEffect(Unit) {
        loadLayouts()
    }

    // Activity Result Launchers
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { exportUri ->
            exportingPackId?.let { packId ->
                exportLayoutToFile(context, packManager, exportUri, packId)
            }
        }
        exportingPackId = null
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { 
            importPackFromUri(context, packManager, it) { loadLayouts() }
        }
    }

    // 创建新布局
    fun createNewLayout(name: String) {
        if (name.isBlank()) return

        if (layouts.any { it.name.equals(name, ignoreCase = true) }) {
            Toast.makeText(context, "布局名称已存在", Toast.LENGTH_SHORT).show()
            return
        }

        val newPack = packManager.createPack(name)

        if (selectedPackId == null) {
            packManager.setSelectedPackId(newPack.id)
        }

        showCreateDialog = false
        loadLayouts()
        ControlEditorActivity.start(context, newPack.id)
    }

    // 设为默认
    fun setDefaultLayout(pack: ControlPackInfo) {
        packManager.setSelectedPackId(pack.id)
        selectedPackId = pack.id
        Toast.makeText(context, "已设为默认布局", Toast.LENGTH_SHORT).show()
    }

    // 重命名
    fun renameLayout(pack: ControlPackInfo, newName: String) {
        if (newName.isBlank()) return

        if (layouts.any { it.id != pack.id && it.name.equals(newName, ignoreCase = true) }) {
            Toast.makeText(context, "布局名称已存在", Toast.LENGTH_SHORT).show()
            return
        }

        packManager.renamePack(pack.id, newName)
        showRenameDialog = null
        loadLayouts()
    }

    // 删除
    fun deleteLayout(pack: ControlPackInfo) {
        packManager.deletePack(pack.id)

        if (selectedPackId == pack.id) {
            val remaining = packManager.getInstalledPacks()
            packManager.setSelectedPackId(remaining.firstOrNull()?.id)
        }

        showDeleteDialog = null
        loadLayouts()
        Toast.makeText(context, "布局已删除", Toast.LENGTH_SHORT).show()
    }

    // 预览状态
    var showPreviewDialog by remember { mutableStateOf<ControlPackInfo?>(null) }
    
    // 获取预览图路径
    fun getPreviewImages(pack: ControlPackInfo): List<File> {
        val packDir = packManager.getPackDir(pack.id)
        return if (pack.previewImagePaths.isNotEmpty()) {
            pack.previewImagePaths.mapNotNull { path ->
                val file = File(packDir, path)
                if (file.exists()) file else null
            }
        } else {
            // 尝试默认 preview.jpg/png
            listOf("preview.jpg", "preview.png", "preview.webp").mapNotNull { name ->
                val file = File(packDir, name)
                if (file.exists()) file else null
            }
        }
    }

    // UI
    ControlLayoutScreen(
        layouts = layouts,
        selectedPackId = selectedPackId,
        showCreateDialog = showCreateDialog,
        showDeleteDialog = showDeleteDialog,
        showRenameDialog = showRenameDialog,
        showMoreMenu = showMoreMenu,
        onBack = onBack,
        onOpenStore = onOpenStore,
        onCreateClick = { showCreateDialog = true },
        onCreateConfirm = { createNewLayout(it) },
        onCreateDismiss = { showCreateDialog = false },
        onLayoutClick = { pack -> ControlEditorActivity.start(context, pack.id) },
        onSetDefault = { setDefaultLayout(it) },
        onShowMoreMenu = { showMoreMenu = it },
        onDismissMoreMenu = { showMoreMenu = null },
        onRenameClick = { showRenameDialog = it; showMoreMenu = null },
        onRenameConfirm = { pack, name -> renameLayout(pack, name) },
        onRenameDismiss = { showRenameDialog = null },
        onDeleteClick = { showDeleteDialog = it; showMoreMenu = null },
        onDeleteConfirm = { deleteLayout(it) },
        onDeleteDismiss = { showDeleteDialog = null },
        onExportClick = { pack ->
            exportingPackId = pack.id
            exportLauncher.launch("${pack.name}.json")
            showMoreMenu = null
        },
        onImportClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
        onPreviewClick = { showPreviewDialog = it }
    )
    
    // 本地布局预览对话框
    showPreviewDialog?.let { pack ->
        LocalLayoutPreviewDialog(
            pack = pack,
            previewImages = getPreviewImages(pack),
            onDismiss = { showPreviewDialog = null },
            onEditClick = { 
                showPreviewDialog = null
                ControlEditorActivity.start(context, pack.id)
            }
        )
    }
}

private fun exportLayoutToFile(
    context: android.content.Context,
    packManager: ControlPackManager,
    uri: Uri,
    packId: String
) {
    try {
        val layout = packManager.getPackLayout(packId) ?: return
        val json = layout.toJson()

        context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(json.toByteArray(StandardCharsets.UTF_8))
        }
        Toast.makeText(context, "导出成功", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun importPackFromUri(
    context: android.content.Context,
    packManager: ControlPackManager,
    uri: Uri,
    onSuccess: () -> Unit
) {
    try {
        // 创建临时文件来存储控件包
        val tempFile = File(context.cacheDir, "import_temp.ralpack")
        
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            tempFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        
        // 使用 ControlPackManager 安装控件包
        val result = packManager.installFromFile(tempFile)
        
        result.onSuccess { packInfo ->
            onSuccess()
            Toast.makeText(context, "导入成功: ${packInfo.name}", Toast.LENGTH_SHORT).show()
        }.onFailure { error ->
            Toast.makeText(context, "导入失败: ${error.message}", Toast.LENGTH_SHORT).show()
        }
        
        // 清理临时文件
        tempFile.delete()
        
    } catch (e: Exception) {
        Toast.makeText(context, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

// ==================== UI 组件 ====================

@Composable
private fun ControlLayoutScreen(
    layouts: List<ControlPackInfo>,
    selectedPackId: String?,
    showCreateDialog: Boolean,
    showDeleteDialog: ControlPackInfo?,
    showRenameDialog: ControlPackInfo?,
    showMoreMenu: ControlPackInfo?,
    onBack: () -> Unit,
    onOpenStore: () -> Unit,
    onCreateClick: () -> Unit,
    onCreateConfirm: (String) -> Unit,
    onCreateDismiss: () -> Unit,
    onLayoutClick: (ControlPackInfo) -> Unit,
    onSetDefault: (ControlPackInfo) -> Unit,
    onShowMoreMenu: (ControlPackInfo) -> Unit,
    onDismissMoreMenu: () -> Unit,
    onRenameClick: (ControlPackInfo) -> Unit,
    onRenameConfirm: (ControlPackInfo, String) -> Unit,
    onRenameDismiss: () -> Unit,
    onDeleteClick: (ControlPackInfo) -> Unit,
    onDeleteConfirm: (ControlPackInfo) -> Unit,
    onDeleteDismiss: () -> Unit,
    onExportClick: (ControlPackInfo) -> Unit,
    onImportClick: () -> Unit,
    onPreviewClick: (ControlPackInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    // 当前选中的布局（用于右侧详情面板）
    var selectedLayout by remember { mutableStateOf<ControlPackInfo?>(null) }
    
    // 初始选中默认布局
    LaunchedEffect(layouts, selectedPackId) {
        if (selectedLayout == null || layouts.none { it.id == selectedLayout?.id }) {
            selectedLayout = layouts.find { it.id == selectedPackId } ?: layouts.firstOrNull()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 顶部栏
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                }
                Text(
                    text = "控制布局",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onImportClick) {
                    Icon(Icons.Default.FileDownload, "导入")
                }
                IconButton(onClick = onOpenStore) {
                    Icon(Icons.Default.Store, "控件商店")
                }
            }
        }

        // 双栏布局
        Row(modifier = Modifier.fillMaxSize()) {
            // 左侧：布局列表
            LayoutListPanel(
                layouts = layouts,
                selectedPackId = selectedPackId,
                selectedLayout = selectedLayout,
                showMoreMenu = showMoreMenu,
                onLayoutSelect = { selectedLayout = it },
                onLayoutClick = onLayoutClick,
                onSetDefault = onSetDefault,
                onShowMoreMenu = onShowMoreMenu,
                onDismissMoreMenu = onDismissMoreMenu,
                onRenameClick = onRenameClick,
                onDeleteClick = onDeleteClick,
                onExportClick = onExportClick,
                onCreateClick = onCreateClick,
                modifier = Modifier
                    .width(320.dp)
                    .fillMaxHeight()
            )

            // 分隔线
            VerticalDivider(
                modifier = Modifier.fillMaxHeight(),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // 右侧：布局详情/预览
            LayoutDetailPanel(
                layout = selectedLayout,
                isDefault = selectedLayout?.id == selectedPackId,
                onEditClick = { selectedLayout?.let { onLayoutClick(it) } },
                onSetDefault = { selectedLayout?.let { onSetDefault(it) } },
                onPreviewClick = { selectedLayout?.let { onPreviewClick(it) } },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
    }

    // 对话框
    if (showCreateDialog) {
        CreateLayoutDialog(onConfirm = onCreateConfirm, onDismiss = onCreateDismiss)
    }

    showRenameDialog?.let { pack ->
        RenameLayoutDialog(
            currentName = pack.name,
            onConfirm = { onRenameConfirm(pack, it) },
            onDismiss = onRenameDismiss
        )
    }

    showDeleteDialog?.let { pack ->
        AlertDialog(
            onDismissRequest = onDeleteDismiss,
            title = { Text("删除布局") },
            text = { Text("确定要删除 \"${pack.name}\" 吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = { onDeleteConfirm(pack) },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = onDeleteDismiss) { Text("取消") }
            }
        )
    }
}

/**
 * 左侧布局列表面板
 */
@Composable
private fun LayoutListPanel(
    layouts: List<ControlPackInfo>,
    selectedPackId: String?,
    selectedLayout: ControlPackInfo?,
    showMoreMenu: ControlPackInfo?,
    onLayoutSelect: (ControlPackInfo) -> Unit,
    onLayoutClick: (ControlPackInfo) -> Unit,
    onSetDefault: (ControlPackInfo) -> Unit,
    onShowMoreMenu: (ControlPackInfo) -> Unit,
    onDismissMoreMenu: () -> Unit,
    onRenameClick: (ControlPackInfo) -> Unit,
    onDeleteClick: (ControlPackInfo) -> Unit,
    onExportClick: (ControlPackInfo) -> Unit,
    onCreateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
        if (layouts.isEmpty()) {
            // 空状态
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.TouchApp,
                    null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "暂无控制布局",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                FilledTonalButton(onClick = onCreateClick) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("新建布局")
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(layouts, key = { it.id }) { pack ->
                    LayoutListItem(
                        pack = pack,
                        isDefault = pack.id == selectedPackId,
                        isSelected = pack.id == selectedLayout?.id,
                        showMoreMenu = showMoreMenu?.id == pack.id,
                        onClick = { onLayoutSelect(pack) },
                        onDoubleClick = { onLayoutClick(pack) },
                        onSetDefault = { onSetDefault(pack) },
                        onShowMoreMenu = { onShowMoreMenu(pack) },
                        onDismissMoreMenu = onDismissMoreMenu,
                        onRenameClick = { onRenameClick(pack) },
                        onDeleteClick = { onDeleteClick(pack) },
                        onExportClick = { onExportClick(pack) }
                    )
                }
            }
        }

        // FAB
        if (layouts.isNotEmpty()) {
            FloatingActionButton(
                onClick = onCreateClick,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(Icons.Default.Add, "新建布局")
            }
        }
    }
}

/**
 * 布局列表项
 */
@Composable
private fun LayoutListItem(
    pack: ControlPackInfo,
    isDefault: Boolean,
    isSelected: Boolean,
    showMoreMenu: Boolean,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
    onSetDefault: () -> Unit,
    onShowMoreMenu: () -> Unit,
    onDismissMoreMenu: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onExportClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = when {
            isSelected -> MaterialTheme.colorScheme.primaryContainer
            isDefault -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            else -> MaterialTheme.colorScheme.surface
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                },
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.TouchApp,
                        null,
                        modifier = Modifier.size(20.dp),
                        tint = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = pack.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isDefault) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = "默认",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Text(
                    text = pack.author.ifBlank { "自定义布局" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 更多菜单
            Box {
                IconButton(onClick = onShowMoreMenu, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.MoreVert, "更多", modifier = Modifier.size(18.dp))
                }
                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = onDismissMoreMenu
                ) {
                    DropdownMenuItem(
                        text = { Text("编辑") },
                        onClick = { onDoubleClick(); onDismissMoreMenu() },
                        leadingIcon = { Icon(Icons.Default.Edit, null) }
                    )
                    if (!isDefault) {
                        DropdownMenuItem(
                            text = { Text("设为默认") },
                            onClick = { onSetDefault(); onDismissMoreMenu() },
                            leadingIcon = { Icon(Icons.Default.Check, null) }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("重命名") },
                        onClick = onRenameClick,
                        leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("导出") },
                        onClick = onExportClick,
                        leadingIcon = { Icon(Icons.Default.FileUpload, null) }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                        onClick = onDeleteClick,
                        leadingIcon = {
                            Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                        }
                    )
                }
            }
        }
    }
}

/**
 * 右侧布局详情面板
 */
@Composable
private fun LayoutDetailPanel(
    layout: ControlPackInfo?,
    isDefault: Boolean,
    onEditClick: () -> Unit,
    onSetDefault: () -> Unit,
    onPreviewClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
    ) {
        if (layout != null) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 布局信息
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.TouchApp,
                                null,
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = layout.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            if (isDefault) {
                                Spacer(modifier = Modifier.width(8.dp))
                                AssistChip(
                                    onClick = {},
                                    label = { Text("默认") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Star,
                                            null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = layout.author.ifBlank { "自定义布局" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (layout.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = layout.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // 操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onPreviewClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Visibility, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("预览")
                    }
                    if (!isDefault) {
                        OutlinedButton(
                            onClick = onSetDefault,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Star, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("设为默认")
                        }
                    }
                    Button(
                        onClick = onEditClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("编辑")
                    }
                }
            }
        } else {
            // 空状态
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.TouchApp,
                        null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "选择一个布局",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}


@Composable
private fun CreateLayoutDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("新建布局") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建布局") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("布局名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }) { Text("创建") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun RenameLayoutDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名布局") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("新名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 本地布局预览对话框 - 横向布局，参考控件商店设计
 */
@Composable
private fun LocalLayoutPreviewDialog(
    pack: ControlPackInfo,
    previewImages: List<File>,
    onDismiss: () -> Unit,
    onEditClick: () -> Unit
) {
    val context = LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            decorFitsSystemWindows = false
        )
    ) {
        Card(
            modifier = Modifier
                .statusBarsPadding()
                .navigationBarsPadding()
                .fillMaxWidth(if (isLandscape) 0.9f else 0.95f)
                .fillMaxHeight(if (isLandscape) 0.92f else 0.75f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // 左侧：预览图区域（占 60%）
                Box(
                    modifier = Modifier
                        .weight(0.6f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (previewImages.isNotEmpty()) {
                        // 直接使用 File 对象加载（与商店一致）
                        AsyncImage(
                            model = previewImages.first(),
                            contentDescription = "布局预览",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.ImageNotSupported,
                                null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "暂无预览图",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                // 右侧：信息区域（占 40%）
                Column(
                    modifier = Modifier
                        .weight(0.4f)
                        .fillMaxHeight()
                ) {
                    // 标题栏
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = pack.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.Close, "关闭")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // 可滚动的信息区域
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // 作者
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Person,
                                null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = pack.author.ifBlank { "自定义布局" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        // 版本
                        Text(
                            text = "v${pack.version}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        // 标签
                        if (pack.tags.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            androidx.compose.foundation.lazy.LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(pack.tags.size) { index ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                    ) {
                                        Text(
                                            text = pack.tags[index],
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }
                        
                        // 描述
                        if (pack.description.isNotBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = pack.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // 操作按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onEditClick,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("编辑")
                        }
                    }
                }
            }
        }
    }
}
