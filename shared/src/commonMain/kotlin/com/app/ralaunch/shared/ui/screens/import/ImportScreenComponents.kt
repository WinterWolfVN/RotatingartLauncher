package com.app.ralaunch.shared.ui.screens.import

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.app.ralaunch.shared.ui.model.DetectedGame
import com.app.ralaunch.shared.ui.model.ImportMethod
import com.app.ralaunch.shared.ui.model.ImportUiState

/**
 * 导入页面内容 - 跨平台（双栏横屏布局）
 */
@Composable
fun ImportScreenContent(
    uiState: ImportUiState,
    onMethodChange: (ImportMethod) -> Unit,
    onBrowseClick: () -> Unit,
    onPathChange: (String) -> Unit,
    onScanClick: () -> Unit,
    onGameSelect: (DetectedGame) -> Unit,
    onImportClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    // 可本地化的文本参数
    titleText: String = "导入游戏",
    pathLabel: String = "游戏文件夹路径",
    pathPlaceholder: String = "/sdcard/Games",
    scanButtonText: String = "扫描游戏",
    scanningText: String = "扫描中...",
    detectedGamesText: String = "检测到的游戏",
    importSelectedText: String = "导入选中的游戏",
    selectGamesText: String = "选择要导入的游戏"
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 标题栏
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp)
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = null
                    )
                }

                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 双栏布局
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // 左侧：导入方式选择（垂直排列）
            ImportMethodPanel(
                currentMethod = uiState.currentMethod,
                onMethodChange = onMethodChange,
                modifier = Modifier
                    .width(200.dp)
                    .fillMaxHeight()
            )

            Spacer(modifier = Modifier.width(16.dp))

            // 右侧：导入内容
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    // 根据导入方式显示不同内容
                    AnimatedContent(
                        targetState = uiState.currentMethod,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "import_content",
                        modifier = Modifier.weight(1f)
                    ) { method ->
                        when (method) {
                            ImportMethod.LOCAL -> {
                                LocalImportContent(
                                    currentPath = uiState.currentPath,
                                    detectedGames = uiState.detectedGames,
                                    isScanning = uiState.isScanning,
                                    scanProgress = uiState.scanProgress,
                                    onBrowseClick = onBrowseClick,
                                    onPathChange = onPathChange,
                                    onScanClick = onScanClick,
                                    onGameSelect = onGameSelect,
                                    onImportClick = onImportClick,
                                    pathLabel = pathLabel,
                                    pathPlaceholder = pathPlaceholder,
                                    scanButtonText = scanButtonText,
                                    scanningText = scanningText,
                                    detectedGamesText = detectedGamesText,
                                    importSelectedText = importSelectedText,
                                    selectGamesText = selectGamesText
                                )
                            }
                            ImportMethod.DEFINITION -> {
                                DefinitionImportContent(onBrowseClick = onBrowseClick)
                            }
                            ImportMethod.SHORTCUT -> {
                                ShortcutImportContent()
                            }
                        }
                    }

                    // 错误提示
                    uiState.errorMessage?.let { error ->
                        Spacer(modifier = Modifier.height(16.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = error,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 左侧导入方式面板（垂直排列）
 */
@Composable
private fun ImportMethodPanel(
    currentMethod: ImportMethod,
    onMethodChange: (ImportMethod) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "导入方式",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        ImportMethod.entries.forEach { method ->
            val isSelected = method == currentMethod

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onMethodChange(method) },
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                },
                border = if (isSelected) {
                    BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                } else {
                    null
                }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when (method) {
                            ImportMethod.LOCAL -> Icons.Default.Folder
                            ImportMethod.DEFINITION -> Icons.Default.Description
                            ImportMethod.SHORTCUT -> Icons.Default.Link
                        },
                        contentDescription = null,
                        tint = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = method.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Text(
                            text = when (method) {
                                ImportMethod.LOCAL -> "扫描本地文件夹"
                                ImportMethod.DEFINITION -> "导入定义文件"
                                ImportMethod.SHORTCUT -> "创建快捷方式"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            }
                        )
                    }
                }
            }
        }
    }
}


/**
 * 本地导入内容
 */
@Composable
private fun LocalImportContent(
    currentPath: String,
    detectedGames: List<DetectedGame>,
    isScanning: Boolean,
    scanProgress: Float,
    onBrowseClick: () -> Unit,
    onPathChange: (String) -> Unit,
    onScanClick: () -> Unit,
    onGameSelect: (DetectedGame) -> Unit,
    onImportClick: () -> Unit,
    pathLabel: String,
    pathPlaceholder: String,
    scanButtonText: String,
    scanningText: String,
    detectedGamesText: String,
    importSelectedText: String,
    selectGamesText: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // 路径输入
        OutlinedTextField(
            value = currentPath,
            onValueChange = onPathChange,
            label = { Text(pathLabel) },
            placeholder = { Text(pathPlaceholder) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = onBrowseClick) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 扫描按钮
        Button(
            onClick = onScanClick,
            enabled = currentPath.isNotBlank() && !isScanning
        ) {
            if (isScanning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(scanningText)
            } else {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(scanButtonText)
            }
        }

        // 扫描进度
        if (isScanning && scanProgress > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { scanProgress },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 检测到的游戏列表
        if (detectedGames.isNotEmpty()) {
            Text(
                text = "$detectedGamesText (${detectedGames.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(detectedGames) { game ->
                    DetectedGameItem(
                        game = game,
                        onClick = { onGameSelect(game) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 导入按钮
            val selectedCount = detectedGames.count { it.isSelected }
            Button(
                onClick = onImportClick,
                enabled = selectedCount > 0,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (selectedCount > 0) "$importSelectedText ($selectedCount)" else selectGamesText)
            }
        }
    }
}

/**
 * 检测到的游戏项
 */
@Composable
private fun DetectedGameItem(
    game: DetectedGame,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (game.isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        border = if (game.isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = game.isSelected,
                onCheckedChange = { onClick() }
            )

            Spacer(modifier = Modifier.width(12.dp))

            // 游戏图标
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.SportsEsports,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 游戏信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = game.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = game.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * 定义文件导入内容
 */
@Composable
private fun DefinitionImportContent(
    onBrowseClick: () -> Unit,
    selectFileText: String = "选择 .radef 定义文件",
    descriptionText: String = "游戏定义文件包含游戏的配置信息",
    buttonText: String = "选择文件"
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Icon(
            imageVector = Icons.Default.Description,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = selectFileText,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = descriptionText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        FilledTonalButton(onClick = onBrowseClick) {
            Icon(Icons.Default.FileOpen, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(buttonText)
        }
    }
}

/**
 * 快捷方式导入内容
 */
@Composable
private fun ShortcutImportContent(
    titleText: String = "创建游戏快捷方式",
    descriptionText: String = "快捷方式可以直接启动其他应用中的游戏",
    buttonText: String = "创建快捷方式"
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Icon(
            imageVector = Icons.Default.Link,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = titleText,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = descriptionText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        FilledTonalButton(onClick = {}) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(buttonText)
        }
    }
}
