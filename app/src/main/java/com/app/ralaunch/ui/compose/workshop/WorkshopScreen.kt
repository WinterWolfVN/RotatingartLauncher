package com.app.ralaunch.ui.compose.workshop

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.ralaunch.steam.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Steam 创意工坊下载页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkshopScreen(
    onItemDownloaded: ((File) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // 状态
    var workshopId by remember { mutableStateOf("") }
    var selectedAppId by remember { mutableStateOf(SteamAppIds.TMODLOADER) }
    var downloadState by remember { mutableStateOf<WorkshopDownloadState>(WorkshopDownloadState.Idle) }
    var outputLog by remember { mutableStateOf(listOf<String>()) }
    var downloadedItems by remember { mutableStateOf<List<WorkshopItem>>(emptyList()) }
    var isSteamCmdInstalled by remember { mutableStateOf(false) }
    var isAutoInstalling by remember { mutableStateOf(false) }
    
    // 自动检查并安装 SteamCMD
    LaunchedEffect(Unit) {
        val installed = SteamCmdManager.isInstalled(context)
        isSteamCmdInstalled = installed
        
        if (!installed) {
            // 自动安装 SteamCMD
            isAutoInstalling = true
            outputLog = listOf("正在自动安装 SteamCMD...")
            downloadState = WorkshopDownloadState.Installing
            
            val success = SteamCmdManager.installSteamCmd(context) { progress, message ->
                outputLog = outputLog + message
            }
            
            isSteamCmdInstalled = success
            isAutoInstalling = false
            downloadState = if (success) {
                outputLog = outputLog + "SteamCMD 安装成功!"
                WorkshopDownloadState.Idle
            } else {
                WorkshopDownloadState.Error("SteamCMD 安装失败")
            }
        }
        
        // 加载已下载的物品
        downloadedItems = SteamCmdManager.getDownloadedItems(context, selectedAppId).map { dir ->
            WorkshopItem(
                workshopId = dir.name,
                appId = selectedAppId,
                localPath = dir,
                downloadedAt = dir.lastModified()
            )
        }
    }
    
    // 当 AppID 改变时刷新列表
    LaunchedEffect(selectedAppId) {
        downloadedItems = SteamCmdManager.getDownloadedItems(context, selectedAppId).map { dir ->
            WorkshopItem(
                workshopId = dir.name,
                appId = selectedAppId,
                localPath = dir,
                downloadedAt = dir.lastModified()
            )
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 标题
        Text(
            text = "Steam 创意工坊",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "通过 SteamCMD 匿名下载创意工坊内容",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 主内容区域 - 双栏布局
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 左侧：下载面板
            Column(
                modifier = Modifier
                    .weight(0.5f)
                    .fillMaxHeight()
            ) {
                // SteamCMD 状态卡片
                SteamCmdStatusCard(
                    isInstalled = isSteamCmdInstalled,
                    isInstalling = downloadState is WorkshopDownloadState.Installing || isAutoInstalling,
                    onInstall = {
                        scope.launch {
                            downloadState = WorkshopDownloadState.Installing
                            outputLog = listOf("正在重新安装 SteamCMD...")
                            
                            val success = SteamCmdManager.installSteamCmd(context) { progress, message ->
                                outputLog = outputLog + message
                            }
                            
                            isSteamCmdInstalled = success
                            downloadState = if (success) {
                                outputLog = outputLog + "SteamCMD 安装成功!"
                                WorkshopDownloadState.Idle
                            } else {
                                WorkshopDownloadState.Error("SteamCMD 安装失败")
                            }
                        }
                    }
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 下载输入区
                DownloadInputCard(
                    workshopId = workshopId,
                    onWorkshopIdChange = { workshopId = it },
                    selectedAppId = selectedAppId,
                    onAppIdChange = { selectedAppId = it },
                    isDownloading = downloadState is WorkshopDownloadState.Downloading,
                    enabled = isSteamCmdInstalled && downloadState !is WorkshopDownloadState.Installing,
                    onDownload = {
                        if (workshopId.isBlank()) return@DownloadInputCard
                        
                        scope.launch {
                            outputLog = listOf("开始下载...")
                            downloadState = WorkshopDownloadState.Downloading(outputLog)
                            
                            val result = SteamCmdManager.downloadWorkshopItem(
                                context = context,
                                appId = selectedAppId,
                                workshopId = workshopId.trim()
                            ) { output ->
                                outputLog = outputLog + output
                                downloadState = WorkshopDownloadState.Downloading(outputLog)
                            }
                            
                            result.fold(
                                onSuccess = { dir ->
                                    val item = WorkshopItem(
                                        workshopId = workshopId.trim(),
                                        appId = selectedAppId,
                                        localPath = dir,
                                        downloadedAt = System.currentTimeMillis()
                                    )
                                    downloadState = WorkshopDownloadState.Success(item)
                                    outputLog = outputLog + "下载完成!"
                                    
                                    // 刷新列表
                                    downloadedItems = SteamCmdManager.getDownloadedItems(context, selectedAppId).map { d ->
                                        WorkshopItem(
                                            workshopId = d.name,
                                            appId = selectedAppId,
                                            localPath = d,
                                            downloadedAt = d.lastModified()
                                        )
                                    }
                                    
                                    onItemDownloaded?.invoke(dir)
                                },
                                onFailure = { e ->
                                    downloadState = WorkshopDownloadState.Error(e.message ?: "下载失败")
                                    outputLog = outputLog + "错误: ${e.message}"
                                }
                            )
                        }
                    }
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 输出日志
                OutputLogCard(
                    logs = outputLog,
                    modifier = Modifier.weight(1f)
                )
            }
            
            // 右侧：已下载列表
            DownloadedItemsCard(
                items = downloadedItems,
                modifier = Modifier
                    .weight(0.5f)
                    .fillMaxHeight()
            )
        }
    }
}

/**
 * SteamCMD 状态卡片
 */
@Composable
private fun SteamCmdStatusCard(
    isInstalled: Boolean,
    isInstalling: Boolean,
    onInstall: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isInstalled) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else 
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = if (isInstalled) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                    contentDescription = null,
                    tint = if (isInstalled) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.error
                )
                
                Column {
                    Text(
                        text = "SteamCMD",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isInstalled) "已安装" else "未安装",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            if (!isInstalled) {
                Button(
                    onClick = onInstall,
                    enabled = !isInstalling
                ) {
                    if (isInstalling) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (isInstalling) "安装中..." else "安装")
                }
            }
        }
    }
}

/**
 * 下载输入卡片
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadInputCard(
    workshopId: String,
    onWorkshopIdChange: (String) -> Unit,
    selectedAppId: String,
    onAppIdChange: (String) -> Unit,
    isDownloading: Boolean,
    enabled: Boolean,
    onDownload: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "下载创意工坊物品",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            // AppID 选择
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "游戏:",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                SteamAppIds.SUPPORTED_APPS.forEach { (appId, name) ->
                    FilterChip(
                        selected = selectedAppId == appId,
                        onClick = { onAppIdChange(appId) },
                        label = { Text(name) },
                        enabled = enabled && !isDownloading
                    )
                }
            }
            
            // Workshop ID 输入
            OutlinedTextField(
                value = workshopId,
                onValueChange = onWorkshopIdChange,
                label = { Text("Workshop ID") },
                placeholder = { Text("例如: 2824689072") },
                singleLine = true,
                enabled = enabled && !isDownloading,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { if (enabled && !isDownloading) onDownload() }
                ),
                trailingIcon = {
                    if (workshopId.isNotEmpty()) {
                        IconButton(onClick = { onWorkshopIdChange("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "清除")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            
            // 下载按钮
            Button(
                onClick = onDownload,
                enabled = enabled && !isDownloading && workshopId.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("下载中...")
                } else {
                    Icon(Icons.Filled.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("下载")
                }
            }
        }
    }
}

/**
 * 输出日志卡片
 */
@Composable
private fun OutputLogCard(
    logs: List<String>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Filled.Terminal,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "输出",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E1E1E))
                    .padding(8.dp)
            ) {
                val scrollState = rememberScrollState()
                
                // 自动滚动到底部
                LaunchedEffect(logs.size) {
                    scrollState.animateScrollTo(scrollState.maxValue)
                }
                
                if (logs.isEmpty()) {
                    Text(
                        text = "等待操作...",
                        color = Color.Gray,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                } else {
                    Column(
                        modifier = Modifier.verticalScroll(scrollState)
                    ) {
                        logs.forEach { line ->
                            Text(
                                text = line,
                                color = when {
                                    line.contains("错误") || line.contains("Error") -> Color(0xFFFF6B6B)
                                    line.contains("成功") || line.contains("完成") -> Color(0xFF69DB7C)
                                    line.startsWith("---") -> Color.Gray
                                    else -> Color(0xFFE0E0E0)
                                },
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 已下载物品卡片
 */
@Composable
private fun DownloadedItemsCard(
    items: List<WorkshopItem>,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Filled.Folder,
                    contentDescription = null
                )
                Text(
                    text = "已下载 (${items.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            if (items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Outlined.FolderOff,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "暂无下载内容",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items, key = { it.workshopId }) { item ->
                        DownloadedItemRow(item = item)
                    }
                }
            }
        }
    }
}

/**
 * 已下载物品行
 */
@Composable
private fun DownloadedItemRow(item: WorkshopItem) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.workshopId,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.formattedSize} · ${dateFormat.format(Date(item.downloadedAt))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = "已下载",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
