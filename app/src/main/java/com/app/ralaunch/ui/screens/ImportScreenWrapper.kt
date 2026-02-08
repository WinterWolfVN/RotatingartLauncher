package com.app.ralaunch.ui.screens

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.ralaunch.R
import com.app.ralaunch.data.model.GameItem
import com.app.ralaunch.data.repository.GameRepository
import com.app.ralaunch.installer.GameInstaller
import com.app.ralaunch.installer.InstallCallback
import com.app.ralaunch.installer.InstallPluginRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent
import java.io.File

// 主线程 Handler，用于在页面切换后仍能执行回调
private val mainHandler = Handler(Looper.getMainLooper())

/**
 * 游戏导入 Screen - 现代化双栏布局
 * 
 * 状态由外部管理，避免导航时丢失
 */
@Composable
fun ImportScreenWrapper(
    gameFilePath: String? = null,
    gameName: String? = null,
    modLoaderFilePath: String? = null,
    modLoaderName: String? = null,
    onBack: () -> Unit = {},
    onImportComplete: (String, GameItem?) -> Unit = { _, _ -> },
    onSelectGameFile: () -> Unit = {},
    onSelectModLoader: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 导入过程状态（这些可以在本地管理，因为导入过程不会离开页面）
    var isImporting by remember { mutableStateOf(false) }
    var importProgress by remember { mutableIntStateOf(0) }
    var importStatus by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var installer by remember { mutableStateOf<GameInstaller?>(null) }

    // 开始导入
    fun startImport() {
        val gamePath = gameFilePath
        if (gamePath.isNullOrEmpty() && modLoaderFilePath.isNullOrEmpty()) {
            errorMessage = "请先选择游戏文件"
            return
        }

        isImporting = true
        importProgress = 0
        importStatus = "准备中..."
        errorMessage = null

        installer = GameInstaller(context)

        // 获取 GameRepository 用于直接保存游戏（避免页面切换导致回调失效）
        val gameRepository: GameRepository? = try {
            KoinJavaComponent.getOrNull(GameRepository::class.java)
        } catch (_: Exception) { null }

        installer?.install(
            gameFilePath = gamePath ?: "",
            modLoaderFilePath = modLoaderFilePath,
            gameName = modLoaderName ?: gameName,
            callback = object : InstallCallback {
                override fun onProgress(message: String, progress: Int) {
                    // 使用 mainHandler 确保即使页面切换也能更新 UI
                    mainHandler.post {
                        importStatus = message
                        importProgress = progress
                    }
                }

                override fun onComplete(gameItem: GameItem) {
                    // 直接保存到仓库，确保游戏被添加（即使页面已切换）
                    gameRepository?.addGame(gameItem)
                    
                    mainHandler.post {
                        isImporting = false
                        importStatus = "导入完成！"
                        importProgress = 100
                        // 尝试调用回调（如果页面还在可能有效）
                        try {
                            onImportComplete("game", gameItem)
                        } catch (_: Exception) {
                            // 页面已切换，忽略回调错误
                        }
                        Toast.makeText(context, "游戏导入成功", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onError(error: String) {
                    mainHandler.post {
                        isImporting = false
                        errorMessage = error
                        Toast.makeText(context, "导入失败: $error", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onCancelled() {
                    mainHandler.post {
                        isImporting = false
                        errorMessage = "导入已取消"
                    }
                }
            }
        )
    }

    // 注意：不在页面切换时自动取消安装
    // 安装任务会在后台继续运行直到完成
    // 用户需要明确点击取消按钮才会取消安装

    // UI
    ModernImportScreen(
        gameFilePath = gameFilePath,
        gameName = gameName,
        modLoaderFilePath = modLoaderFilePath,
        modLoaderName = modLoaderName,
        isImporting = isImporting,
        importProgress = importProgress,
        importStatus = importStatus,
        errorMessage = errorMessage,
        onSelectGameFile = onSelectGameFile,
        onSelectModLoader = onSelectModLoader,
        onStartImport = { startImport() },
        onBack = onBack,
        onDismissError = { errorMessage = null }
    )
}

/**
 * 现代化导入界面
 */
@Composable
private fun ModernImportScreen(
    gameFilePath: String?,
    gameName: String?,
    modLoaderFilePath: String?,
    modLoaderName: String?,
    isImporting: Boolean,
    importProgress: Int,
    importStatus: String,
    errorMessage: String?,
    onSelectGameFile: () -> Unit,
    onSelectModLoader: () -> Unit,
    onStartImport: () -> Unit,
    onBack: () -> Unit,
    onDismissError: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val hasFiles = !gameFilePath.isNullOrEmpty() || !modLoaderFilePath.isNullOrEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 主内容 - 双栏布局
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // ===== 左侧面板 - 引导信息 =====
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(end = 16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 上部：标题
                Text(
                    text = "导入新游戏",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // 检测结果（选择文件后显示）
                val displayName = modLoaderName ?: gameName
                val displayLabel = if (modLoaderName != null) "检测到模组加载器" else "检测到游戏"

                AnimatedVisibility(
                    visible = displayName != null,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        tonalElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = displayLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = displayName ?: "",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }

                // 中部：引导教程（可滚动）
                val guideScrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(guideScrollState),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Step 1: 购买游戏
                    ImportGuideSection(
                        title = "第一步：购买游戏",
                        icon = Icons.Outlined.ShoppingCart,
                        steps = listOf(
                            "前往 GOG.com 注册并登录账号",
                            "搜索并购买游戏（如 Terraria、Stardew Valley）",
                            "如已拥有游戏，跳过此步骤"
                        )
                    )

                    // Step 2: 下载游戏
                    ImportGuideSection(
                        title = "第二步：下载游戏安装包",
                        icon = Icons.Outlined.CloudDownload,
                        steps = listOf(
                            "在 GOG.com 点击头像进入「我的游戏」",
                            "找到游戏，点击进入下载页面",
                            "System 选择「Linux」版本",
                            "下载 .sh 安装包（如 terraria_v1_4_5_4_88511.sh）"
                        ),
                        imageResId = R.drawable.guide_gog_download
                    )

                    // Step 3: 模组加载器（可选）
                    ImportGuideSection(
                        title = "第三步：下载模组加载器（可选）",
                        icon = Icons.Outlined.Build,
                        steps = listOf(
                            "tModLoader（Terraria 模组）：",
                            "  前往 github.com/tModLoader/tModLoader/releases",
                            "  下载最新 stable 版本的 tModLoader.zip",
                            "",
                            "SMAPI（Stardew Valley 模组）：",
                            "  前往 smapi.io 点击 Download",
                            "  下载 SMAPI Linux 版本安装包"
                        ),
                        imageResId = R.drawable.guide_tmodloader_download
                    )

                    // Step 4: 导入到启动器
                    ImportGuideSection(
                        title = "第四步：导入到启动器",
                        icon = Icons.Outlined.InstallMobile,
                        steps = listOf(
                            "点击右侧「游戏文件」→ 选择下载的 .sh 或 .zip 文件",
                            "如需模组加载器，点击「模组加载器」→ 选择对应文件",
                            "确认上方识别结果无误",
                            "点击「开始导入」等待安装完成",
                            "返回游戏列表即可启动游戏"
                        )
                    )
                }
                
                // 下部：进度显示
                AnimatedVisibility(
                    visible = isImporting,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = importStatus,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "$importProgress%",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { importProgress / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                        }
                    }
                }
            }

            // ===== 右侧面板 - 操作区域 =====
            Column(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                // 游戏文件卡片
                ModernFileCard(
                    title = "游戏文件",
                    subtitle = if (gameFilePath != null) File(gameFilePath).name else "选择 .sh 或 .zip 安装包",
                    icon = Icons.Outlined.SportsEsports,
                    isSelected = gameFilePath != null,
                    isPrimary = true,
                    onClick = onSelectGameFile,
                    enabled = !isImporting
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 模组加载器卡片
                ModernFileCard(
                    title = "模组加载器",
                    subtitle = if (modLoaderFilePath != null) File(modLoaderFilePath).name else "tModLoader / SMAPI 等（可选）",
                    icon = Icons.Outlined.Build,
                    isSelected = modLoaderFilePath != null,
                    isPrimary = false,
                    badge = "可选",
                    onClick = onSelectModLoader,
                    enabled = !isImporting
                )

                // 错误显示
                AnimatedVisibility(
                    visible = errorMessage != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = errorMessage ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = onDismissError) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "关闭",
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 导入按钮
                Button(
                    onClick = onStartImport,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = !isImporting && hasFiles,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    if (isImporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "导入中... $importProgress%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "开始导入",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

/**
 * 功能特性项
 */
@Composable
private fun FeatureItem(
    icon: ImageVector,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 现代化文件选择卡片
 */
@Composable
private fun ModernFileCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    isPrimary: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    badge: String? = null
) {
    val primaryColor = MaterialTheme.colorScheme.primary

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 2.dp,
                        color = primaryColor,
                        shape = RoundedCornerShape(20.dp)
                    )
                } else {
                    Modifier
                }
            )
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        tonalElevation = if (isSelected) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (isSelected) {
                            primaryColor.copy(alpha = 0.15f)
                        } else if (isPrimary) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckCircle else icon,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = if (isSelected) {
                        primaryColor
                    } else if (isPrimary) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    }
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // 文本
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    badge?.let {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 箭头图标
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

/**
 * 导入引导步骤区块（支持可选参考图片）
 */
@Composable
private fun ImportGuideSection(
    title: String,
    icon: ImageVector,
    steps: List<String>,
    @DrawableRes imageResId: Int? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            // 步骤列表（跳过空字符串，用于分组间隔）
            var stepNumber = 1
            steps.forEach { step ->
                if (step.isBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                } else if (step.startsWith("  ")) {
                    // 缩进子项（无编号）
                    Text(
                        text = step.trimStart(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier.padding(start = 22.dp, bottom = 2.dp)
                    )
                } else {
                    Row(
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "${stepNumber}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(18.dp)
                        )
                        Text(
                            text = step,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    stepNumber++
                }
            }

            // 可选参考截图
            if (imageResId != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Image(
                    painter = painterResource(id = imageResId),
                    contentDescription = "参考截图",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.FillWidth
                )
            }
        }
    }
}

