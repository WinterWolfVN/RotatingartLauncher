package com.app.ralaunch.controls.editors.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 联机模式
 */
enum class MultiplayerMode {
    SELECT,      // 选择模式
    CREATE_ROOM, // 创建房间
    JOIN_ROOM    // 加入房间
}

/**
 * 邀请码工具
 */
object InviteCodeUtils {
    // 简单的 Base64 编码邀请码
    fun encode(roomName: String, password: String): String {
        val data = "$roomName|$password"
        return android.util.Base64.encodeToString(data.toByteArray(), android.util.Base64.NO_WRAP)
    }
    
    fun decode(code: String): Pair<String, String>? {
        return try {
            val data = String(android.util.Base64.decode(code.trim(), android.util.Base64.NO_WRAP))
            val parts = data.split("|", limit = 2)
            if (parts.size == 2) Pair(parts[0], parts[1]) else null
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * 联机弹窗 (独立弹窗 - 横屏风格)
 */
@Composable
fun MultiplayerDialog(
    state: FloatingMenuState,
    callbacks: FloatingMenuCallbacks,
    onDismiss: () -> Unit
) {
    var selectedMode by remember { mutableStateOf<MultiplayerMode?>(null) }
    var inviteCode by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf<String?>(null) }
    var generatedInviteCode by remember { mutableStateOf<String?>(null) }
    var isCreatingRoom by remember { mutableStateOf(false) } // 正在创建房间
    
    val clipboardManager = LocalClipboardManager.current
    var showCopied by remember { mutableStateOf(false) }
    
    // 生成随机房间名和密码
    fun generateRoomCredentials(): Pair<String, String> {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val roomName = "Room_" + (1..6).map { chars.random() }.joinToString("")
        val password = (1..8).map { chars.random() }.joinToString("")
        return Pair(roomName, password)
    }
    
    // 创建房间的函数
    // 流程：请求 VPN 权限 → 初始化 VPN 服务（创建 TUN 接口） → 创建房间
    fun createRoom() {
        isCreatingRoom = true
        showError = null
        
        // 第一步：请求 VPN 权限
        callbacks.prepareVpnPermission(
            onGranted = {
                // 第二步：初始化 VPN 服务（创建 TUN 接口）
                callbacks.initVpnService(
                    onReady = {
                        // 第三步：VPN 就绪，创建房间（作为房主）
                        val (roomName, password) = generateRoomCredentials()
                        generatedInviteCode = InviteCodeUtils.encode(roomName, password)
                        callbacks.onMultiplayerConnect(roomName, password, isHost = true)
                        isCreatingRoom = false
                    },
                    onError = { error ->
                        showError = "VPN 初始化失败: $error"
                        isCreatingRoom = false
                    }
                )
            },
            onDenied = {
                showError = "需要 VPN 权限才能使用联机功能"
                isCreatingRoom = false
            }
        )
    }
    
    // 全屏半透明遮罩
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        // 弹窗内容
        Surface(
            modifier = Modifier
                .widthIn(max = 700.dp)
                .heightIn(max = 400.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {} // 阻止点击穿透
                ),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            tonalElevation = 8.dp
        ) {
            // 检查是否可用
            if (!callbacks.isMultiplayerAvailable()) {
                UnavailableContent(
                    reason = callbacks.getMultiplayerUnavailableReason(),
                    onDismiss = onDismiss
                )
                return@Surface
            }
            
            // 根据连接状态显示不同内容
            when (state.multiplayerConnectionState) {
                MultiplayerState.CONNECTED -> {
                    ConnectedDialogContent(
                        state = state,
                        generatedInviteCode = generatedInviteCode,
                        clipboardManager = clipboardManager,
                        showCopied = showCopied,
                        onShowCopied = { showCopied = it },
                        onDisconnect = {
                            callbacks.onMultiplayerDisconnect()
                            generatedInviteCode = null
                            selectedMode = null
                        },
                        onDismiss = onDismiss
                    )
                }
                
                MultiplayerState.CONNECTING -> {
                    ConnectingDialogContent(onDismiss = onDismiss)
                }
                
                else -> {
                    // 横屏布局：左边选项，右边输入
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp)
                    ) {
                        // 左侧：菜单标题和选项
                        Column(
                            modifier = Modifier
                                .weight(0.4f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // 标题
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Wifi,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                                Text(
                                    text = "联机菜单",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // 我想当房主 - 点击直接创建房间
                            MultiplayerOptionItem(
                                icon = Icons.Default.Home,
                                title = "我想当房主",
                                subtitle = if (isCreatingRoom) "正在创建..." else "创建房间并生成邀请码",
                                isSelected = selectedMode == MultiplayerMode.CREATE_ROOM,
                                onClick = { 
                                    selectedMode = MultiplayerMode.CREATE_ROOM
                                    createRoom() // 直接创建房间
                                },
                                enabled = !isCreatingRoom
                            )
                            
                            // 我想当房客
                            MultiplayerOptionItem(
                                icon = Icons.Default.People,
                                title = "我想当房客",
                                subtitle = "输入房主提供的邀请码",
                                isSelected = selectedMode == MultiplayerMode.JOIN_ROOM,
                                onClick = { selectedMode = MultiplayerMode.JOIN_ROOM }
                            )
                            
                            Spacer(modifier = Modifier.weight(1f))
                            
                            // 关闭按钮
                            TextButton(
                                onClick = onDismiss,
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("关闭")
                            }
                        }
                        
                        // 分隔线
                        VerticalDivider(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        
                        // 右侧：对应的输入界面
                        Box(
                            modifier = Modifier
                                .weight(0.6f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            when (selectedMode) {
                                MultiplayerMode.CREATE_ROOM -> {
                                    CreateRoomDialogContent(
                                        isCreating = isCreatingRoom,
                                        showError = showError,
                                        generatedInviteCode = generatedInviteCode,
                                        clipboardManager = clipboardManager,
                                        onRetry = { createRoom() }
                                    )
                                }
                                
                                MultiplayerMode.JOIN_ROOM -> {
                                    JoinRoomDialogContent(
                                        inviteCode = inviteCode,
                                        showError = showError,
                                        clipboardManager = clipboardManager,
                                        onInviteCodeChange = { inviteCode = it },
                                        onJoinRoom = {
                                            val decoded = InviteCodeUtils.decode(inviteCode)
                                            if (decoded == null) {
                                                showError = "无效的邀请码"
                                                return@JoinRoomDialogContent
                                            }
                                            showError = null
                                            
                                            // 第一步：请求 VPN 权限
                                            callbacks.prepareVpnPermission(
                                                onGranted = {
                                                    // 第二步：初始化 VPN 服务
                                                    callbacks.initVpnService(
                                                        onReady = {
                                                            // 第三步：连接房间（作为加入者）
                                                            callbacks.onMultiplayerConnect(decoded.first, decoded.second, isHost = false)
                                                        },
                                                        onError = { error ->
                                                            showError = "VPN 初始化失败: $error"
                                                        }
                                                    )
                                                },
                                                onDenied = {
                                                    showError = "需要 VPN 权限才能使用联机功能"
                                                }
                                            )
                                        }
                                    )
                                }
                                
                                else -> {
                                    // 未选择时显示提示
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            Icons.Default.TouchApp,
                                            contentDescription = null,
                                            modifier = Modifier.size(48.dp),
                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = "请选择联机方式",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 联机选项项
 */
@Composable
private fun MultiplayerOptionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent,
        border = if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (isSelected) MaterialTheme.colorScheme.primary 
                       else if (enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f) 
                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }
        }
    }
}

/**
 * 不可用内容
 */
@Composable
private fun UnavailableContent(
    reason: String,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "联机功能不可用",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = reason,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onDismiss) {
            Text("关闭")
        }
    }
}

/**
 * 创建房间内容 - 自动生成邀请码
 */
@Composable
private fun CreateRoomDialogContent(
    isCreating: Boolean,
    showError: String?,
    generatedInviteCode: String?,
    clipboardManager: ClipboardManager,
    onRetry: () -> Unit
) {
    var showCopied by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when {
            isCreating -> {
                // 正在创建房间
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "正在创建房间...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "请在系统弹窗中允许 VPN 连接",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            showError != null -> {
                // 显示错误
                Icon(
                    Icons.Default.Error,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = showError,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("重试")
                }
            }
            
            generatedInviteCode != null -> {
                // 显示生成的邀请码
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "房间已创建",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                // 邀请码显示区域
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "邀请码",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = generatedInviteCode,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 复制按钮
                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(generatedInviteCode))
                        showCopied = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        if (showCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (showCopied) "已复制" else "复制邀请码",
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "分享邀请码给好友加入房间",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            else -> {
                // 默认状态 - 提示点击左侧选项
                Icon(
                    Icons.Default.TouchApp,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "点击左侧「我想当房主」",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "自动创建房间并生成邀请码",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * 加入房间内容
 */
@Composable
private fun JoinRoomDialogContent(
    inviteCode: String,
    showError: String?,
    clipboardManager: ClipboardManager,
    onInviteCodeChange: (String) -> Unit,
    onJoinRoom: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "请输入房主提供的邀请码",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        OutlinedTextField(
            value = inviteCode,
            onValueChange = onInviteCodeChange,
            placeholder = { Text("U/XXXX-XXXX-XXXX", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            ),
            trailingIcon = {
                IconButton(
                    onClick = { clipboardManager.getText()?.text?.let { onInviteCodeChange(it) } }
                ) {
                    Icon(
                        Icons.Default.ContentPaste,
                        contentDescription = "粘贴",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        )
        
        showError?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { clipboardManager.getText()?.text?.let { onInviteCodeChange(it) } },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("粘贴")
            }
            
            Button(
                onClick = onJoinRoom,
                modifier = Modifier.weight(1f),
                enabled = inviteCode.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("加入", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * 连接中内容
 */
@Composable
private fun ConnectingDialogContent(onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "正在连接房间...",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(24.dp))
        TextButton(
            onClick = onDismiss,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
        ) {
            Text("取消")
        }
    }
}

/**
 * 已连接内容
 */
@Composable
private fun ConnectedDialogContent(
    state: FloatingMenuState,
    generatedInviteCode: String?,
    clipboardManager: ClipboardManager,
    showCopied: Boolean,
    onShowCopied: (Boolean) -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit
) {
    LaunchedEffect(showCopied) {
        if (showCopied) {
            kotlinx.coroutines.delay(2000)
            onShowCopied(false)
        }
    }
    
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        // 左侧：状态信息
        Column(
            modifier = Modifier
                .weight(0.45f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 连接状态
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = "已连接",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            
            // 在线人数
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (state.multiplayerPeerCount > 0) 
                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f) 
                else 
                    MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.People,
                        contentDescription = null,
                        tint = if (state.multiplayerPeerCount > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            state.multiplayerPeerCount > 0 -> "在线人数: ${state.multiplayerPeerCount + 1}"
                            state.multiplayerIsHost -> "等待玩家加入..."
                            else -> "正在寻找房主..."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // 操作按钮
            OutlinedButton(
                onClick = onDisconnect,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("断开连接")
            }
            
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            ) {
                Text("返回游戏")
            }
        }
        
        // 分隔线
        VerticalDivider(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
        
        // 右侧：IP和邀请码
        Column(
            modifier = Modifier
                .weight(0.55f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 游戏连接地址（NoTun 模式 + 端口转发）
            // 参考 Terracotta：加入者通过端口转发连接本地端口
            // 房主：其他玩家连接 127.0.0.1:端口
            // 加入者：连接 127.0.0.1:端口（端口转发到房主）
            val gameAddress = "127.0.0.1"
            
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (state.multiplayerIsHost) "游戏服务器地址" else "游戏连接地址",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = gameAddress,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(gameAddress))
                                onShowCopied(true)
                            }
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "复制",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                    // 游戏端口说明
                    Column {
                        Text(
                            text = "游戏端口：泰拉瑞亚 7777 / 星露谷 24642",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (state.multiplayerIsHost) 
                                "房主开服后，其他玩家在游戏中连接 $gameAddress:端口" 
                            else 
                                "在游戏「多人游戏」→「通过IP加入」输入 $gameAddress:端口",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // 邀请码
            generatedInviteCode?.let { code ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "分享邀请码给好友",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (code.length > 24) code.take(24) + "..." else code,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Button(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(code))
                                    onShowCopied(true)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("复制", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
            
            // 复制提示
            AnimatedVisibility(visible = showCopied) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "已复制到剪贴板",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
        }
    }
}
