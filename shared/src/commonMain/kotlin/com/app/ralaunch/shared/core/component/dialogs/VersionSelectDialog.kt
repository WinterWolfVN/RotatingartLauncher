package com.app.ralaunch.shared.core.component.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * 版本信息数据类
 */
data class VersionInfo(
    val id: String,
    val displayName: String,
    val size: String = ""
)

/**
 * 版本选择对话框 - 用于 GOG 游戏安装等场景
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersionSelectDialog(
    title: String,
    gameVersions: List<VersionInfo>,
    modLoaderVersions: List<VersionInfo>,
    gameVersionLabel: String = "游戏版本",
    modLoaderLabel: String = "ModLoader 版本",
    installButtonText: String = "安装",
    cancelButtonText: String = "取消",
    onInstall: (gameVersionIndex: Int, modLoaderVersionIndex: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedGameVersion by remember { mutableIntStateOf(0) }
    var selectedModLoaderVersion by remember { mutableIntStateOf(0) }
    var gameExpanded by remember { mutableStateOf(false) }
    var modLoaderExpanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .widthIn(min = 300.dp, max = 400.dp)
            ) {
                // 标题
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 游戏版本选择
                Text(
                    text = gameVersionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                ExposedDropdownMenuBox(
                    expanded = gameExpanded,
                    onExpandedChange = { gameExpanded = it }
                ) {
                    OutlinedTextField(
                        value = if (gameVersions.isNotEmpty()) {
                            gameVersions[selectedGameVersion].displayName
                        } else "",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = gameExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = gameExpanded,
                        onDismissRequest = { gameExpanded = false }
                    ) {
                        gameVersions.forEachIndexed { index, version ->
                            DropdownMenuItem(
                                text = { Text(version.displayName) },
                                onClick = {
                                    selectedGameVersion = index
                                    gameExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ModLoader 版本选择
                Text(
                    text = modLoaderLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                ExposedDropdownMenuBox(
                    expanded = modLoaderExpanded,
                    onExpandedChange = { modLoaderExpanded = it }
                ) {
                    OutlinedTextField(
                        value = if (modLoaderVersions.isNotEmpty()) {
                            modLoaderVersions[selectedModLoaderVersion].displayName
                        } else "",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modLoaderExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = modLoaderExpanded,
                        onDismissRequest = { modLoaderExpanded = false }
                    ) {
                        modLoaderVersions.forEachIndexed { index, version ->
                            DropdownMenuItem(
                                text = { Text(version.displayName) },
                                onClick = {
                                    selectedModLoaderVersion = index
                                    modLoaderExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(cancelButtonText)
                    }

                    Button(
                        onClick = { onInstall(selectedGameVersion, selectedModLoaderVersion) },
                        modifier = Modifier.weight(1f),
                        enabled = gameVersions.isNotEmpty() && modLoaderVersions.isNotEmpty()
                    ) {
                        Text(installButtonText)
                    }
                }
            }
        }
    }
}
