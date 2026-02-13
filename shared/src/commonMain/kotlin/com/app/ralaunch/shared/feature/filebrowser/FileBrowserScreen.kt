package com.app.ralaunch.shared.feature.filebrowser

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.remember
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 文件浏览器 Screen - 跨平台（双栏横屏布局）
 */
@Composable
fun FileBrowserScreen(
    state: FileBrowserUiState,
    title: String = "选择文件",
    onSearchQueryChange: (String) -> Unit,
    onSortModeChange: (SortMode) -> Unit,
    onFileClick: (FileItemData) -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSortMenu by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 顶部栏
        FileBrowserTopBar(
            title = title,
            currentPath = state.currentPath,
            sortMode = state.sortMode,
            showSortMenu = showSortMenu,
            onShowSortMenu = { showSortMenu = it },
            onSortModeChange = {
                onSortModeChange(it)
                showSortMenu = false
            },
            onBack = onBack
        )

        if (!state.hasPermission) {
            PermissionRequestContent(onRequestPermission = onRequestPermission)
        } else {
            FileBrowserContentLandscape(
                state = state,
                onSearchQueryChange = onSearchQueryChange,
                onFileClick = onFileClick,
                onConfirm = onConfirm
            )
        }
    }
}

@Composable
private fun FileBrowserTopBar(
    title: String,
    currentPath: String,
    sortMode: SortMode,
    showSortMenu: Boolean,
    onShowSortMenu: (Boolean) -> Unit,
    onSortModeChange: (SortMode) -> Unit,
    onBack: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = currentPath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box {
                    IconButton(onClick = { onShowSortMenu(true) }) {
                        Icon(Icons.Default.Sort, "排序")
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { onShowSortMenu(false) }
                    ) {
                        SortMenuItem("按名称", SortMode.NAME, sortMode, onSortModeChange)
                        SortMenuItem("按大小", SortMode.SIZE, sortMode, onSortModeChange)
                        SortMenuItem("按时间", SortMode.TIME, sortMode, onSortModeChange)
                    }
                }
            }
        }
    }
}

@Composable
private fun SortMenuItem(
    text: String,
    mode: SortMode,
    currentMode: SortMode,
    onSelect: (SortMode) -> Unit
) {
    DropdownMenuItem(
        text = { Text(text) },
        onClick = { onSelect(mode) },
        leadingIcon = {
            if (currentMode == mode) {
                Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    )
}

/**
 * 双栏横屏布局内容
 */
@Composable
private fun FileBrowserContentLandscape(
    state: FileBrowserUiState,
    onSearchQueryChange: (String) -> Unit,
    onFileClick: (FileItemData) -> Unit,
    onConfirm: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // 左侧：搜索 + 文件列表（更宽）
        Column(
            modifier = Modifier
                .weight(0.65f)
                .fillMaxHeight()
        ) {
            // 搜索框（更紧凑）
            SearchBar(
                query = state.searchQuery,
                onQueryChange = onSearchQueryChange
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 文件列表
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (state.files.isEmpty()) {
                        EmptyFileList()
                    } else {
                        FileList(
                            files = state.files,
                            selectedFile = state.selectedFile,
                            onFileClick = onFileClick
                        )
                    }

                    if (state.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // 右侧：文件详情面板（更窄）
        FileDetailPanel(
            selectedFile = state.selectedFile,
            onConfirm = onConfirm,
            modifier = Modifier
                .weight(0.35f)
                .fillMaxHeight()
        )
    }
}

/**
 * 文件详情面板
 */
@Composable
private fun FileDetailPanel(
    selectedFile: FileItemData?,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        if (selectedFile != null && !selectedFile.isDirectory && !selectedFile.isParent) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // 可滚动内容
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                ) {
                    // 文件图标
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = getFileIcon(selectedFile),
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 文件名
                    Text(
                        text = selectedFile.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 文件信息
                    FileInfoRow(label = "大小", value = formatFileSize(selectedFile.size))
                    FileInfoRow(label = "路径", value = selectedFile.path)
                    if (selectedFile.lastModified > 0) {
                        FileInfoRow(label = "修改时间", value = formatLastModified(selectedFile.lastModified))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 确认按钮（固定在底部）
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("选择此文件", style = MaterialTheme.typography.labelLarge)
                }
            }
        } else {
            // 空状态或目录
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.InsertDriveFile,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "选择一个文件",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
private fun FileInfoRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun getFileIcon(file: FileItemData): androidx.compose.ui.graphics.vector.ImageVector {
    return when {
        file.name.endsWith(".zip", true) || file.name.endsWith(".rar", true) -> Icons.Default.Archive
        file.name.endsWith(".sh", true) -> Icons.Default.Terminal
        file.name.endsWith(".dll", true) || file.name.endsWith(".exe", true) -> Icons.Default.Memory
        file.name.endsWith(".png", true) || file.name.endsWith(".jpg", true) -> Icons.Default.Image
        file.name.endsWith(".mp4", true) || file.name.endsWith(".mkv", true) -> Icons.Default.Movie
        file.name.endsWith(".mp3", true) || file.name.endsWith(".ogg", true) -> Icons.Default.MusicNote
        file.name.endsWith(".txt", true) || file.name.endsWith(".log", true) -> Icons.Default.Description
        else -> Icons.Default.InsertDriveFile
    }
}

private fun formatLastModified(timestamp: Long): String {
    if (timestamp <= 0) return "未知"
    val date = java.util.Date(timestamp)
    val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
    return format.format(date)
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth().height(40.dp),
        placeholder = { Text("搜索...", style = MaterialTheme.typography.labelMedium) },
        leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp)) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Clear, "清除", modifier = Modifier.size(16.dp))
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(8.dp),
        textStyle = MaterialTheme.typography.labelMedium
    )
}

@Composable
private fun FileList(
    files: List<FileItemData>,
    selectedFile: FileItemData?,
    onFileClick: (FileItemData) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(4.dp)
    ) {
        items(files, key = { it.path }) { file ->
            FileListItem(
                file = file,
                isSelected = selectedFile?.path == file.path,
                onClick = { onFileClick(file) }
            )
        }
    }
}

@Composable
private fun FileListItem(
    file: FileItemData,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    // 缓存颜色计算
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val backgroundColor = remember(isSelected) {
        if (isSelected) primaryContainer.copy(alpha = 0.5f) else Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FileIcon(file = file)
        Spacer(modifier = Modifier.width(10.dp))
        FileInfo(file = file, modifier = Modifier.weight(1f))
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun FileIcon(file: FileItemData) {
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    val secondaryContainer = MaterialTheme.colorScheme.secondaryContainer
    val onSecondaryContainer = MaterialTheme.colorScheme.onSecondaryContainer
    
    // 缓存图标计算
    val icon = remember(file.isParent, file.isDirectory, file.name) {
        when {
            file.isParent -> Icons.Default.ArrowUpward
            file.isDirectory -> Icons.Default.Folder
            file.name.endsWith(".zip", true) -> Icons.Default.Archive
            file.name.endsWith(".sh", true) -> Icons.Default.Terminal
            file.name.endsWith(".dll", true) || file.name.endsWith(".exe", true) -> Icons.Default.Memory
            else -> Icons.Default.InsertDriveFile
        }
    }
    
    val isPrimary = file.isParent || file.isDirectory
    val containerColor = if (isPrimary) primaryContainer else secondaryContainer
    val contentColor = if (isPrimary) onPrimaryContainer else onSecondaryContainer

    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = contentColor
        )
    }
}

@Composable
private fun FileInfo(file: FileItemData, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = file.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (file.isDirectory) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (!file.isDirectory && !file.isParent) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = formatFileSize(file.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


@Composable
private fun EmptyFileList() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.FolderOff,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "文件夹为空",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PermissionRequestContent(onRequestPermission: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.FolderOff,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "需要存储权限",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "请授予存储访问权限以浏览文件",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onRequestPermission,
                modifier = Modifier.fillMaxWidth(0.6f)
            ) {
                Icon(Icons.Default.Security, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("授予权限")
            }
        }
    }
}
