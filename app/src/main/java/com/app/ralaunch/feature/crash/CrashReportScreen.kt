package com.app.ralaunch.feature.crash

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

// 主题色彩现在使用 MaterialTheme.colorScheme

/**
 * 崩溃报告页面 - Compose 版本
 * 横屏专业设计，分区展示错误信息
 */
@Composable
fun CrashReportScreen(
    errorDetails: String?,
    stackTrace: String?,
    onClose: () -> Unit,
    onRestart: () -> Unit
) {
    val context = LocalContext.current
    var stackTraceExpanded by remember { mutableStateOf(true) }
    val rotationAngle by animateFloatAsState(
        targetValue = if (stackTraceExpanded) 180f else 0f,
        label = "rotation"
    )

    // 解析错误详情
    val parsedInfo = remember(errorDetails) { parseErrorDetails(errorDetails) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.background)
                )
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 左侧 - 错误概览
            Column(
                modifier = Modifier
                    .weight(0.35f)
                    .fillMaxHeight()
            ) {
                // 错误标题卡片
                ErrorHeaderCard(parsedInfo)

                Spacer(modifier = Modifier.height(12.dp))

                // 设备信息卡片
                DeviceInfoCard(parsedInfo)

                Spacer(modifier = Modifier.weight(1f))

                // 操作按钮
                ActionButtons(
                    onShare = { shareLog(context, errorDetails, stackTrace) },
                    onRestart = onRestart,
                    onClose = onClose
                )
            }

            // 右侧 - 详细日志
            Column(
                modifier = Modifier
                    .weight(0.65f)
                    .fillMaxHeight()
            ) {
                // 堆栈跟踪卡片
                Card(
                    modifier = Modifier.fillMaxSize(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // 标题栏
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "详细日志",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.weight(1f))

                            // 展开/收起按钮
                            IconButton(
                                onClick = { stackTraceExpanded = !stackTraceExpanded },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (stackTraceExpanded) "收起" else "展开",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.rotate(rotationAngle)
                                )
                            }
                        }

                        // 日志内容
                        AnimatedVisibility(
                            visible = stackTraceExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background)
                                    .padding(1.dp)
                            ) {
                                val scrollState = rememberScrollState()
                                val horizontalScrollState = rememberScrollState()

                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(scrollState)
                                        .horizontalScroll(horizontalScrollState)
                                        .padding(12.dp)
                                ) {
                                    // 错误信息部分
                                    if (!parsedInfo.errorType.isNullOrEmpty() || !parsedInfo.errorMessage.isNullOrEmpty()) {
                                        LogSection(
                                            title = "错误信息",
                                            content = buildString {
                                                parsedInfo.errorType?.let { append("类型: $it\n") }
                                                parsedInfo.errorMessage?.let { append("信息: $it\n") }
                                                parsedInfo.exitCode?.let { append("退出代码: $it") }
                                            },
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                    }

                                    // 堆栈跟踪
                                    if (!stackTrace.isNullOrEmpty()) {
                                        LogSection(
                                            title = "堆栈跟踪 / Logcat",
                                            content = stackTrace,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        Text(
                                            text = "无可用的堆栈跟踪信息",
                                            color = MaterialTheme.colorScheme.outline,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 13.sp
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

@Composable
private fun ErrorHeaderCard(info: ParsedErrorInfo) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 错误图标
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(MaterialTheme.colorScheme.error.copy(alpha = 0.3f), Color.Transparent)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Column {
                    Text(
                        text = "应用已停止运行",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = info.errorType ?: "游戏异常退出",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 错误摘要
            if (!info.errorMessage.isNullOrEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.background)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = info.errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            // 退出代码
            info.exitCode?.let { code ->
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "退出代码",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = code,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (code != "0") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceInfoCard(info: ParsedErrorInfo) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "环境信息",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            InfoRow("发生时间", info.timestamp ?: "未知")
            InfoRow("应用版本", info.appVersion ?: "未知")
            InfoRow("设备型号", info.deviceModel ?: "未知")
            InfoRow("Android", info.androidVersion ?: "未知")
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 160.dp)
        )
    }
}

@Composable
private fun LogSection(
    title: String,
    content: String,
    color: Color
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(4.dp, 16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = content,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ActionButtons(
    onShare: () -> Unit,
    onRestart: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 分享日志
        Button(
            onClick = onShare,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("分享日志", fontSize = 14.sp)
        }

        // 重启应用
        OutlinedButton(
            onClick = onRestart,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            border = ButtonDefaults.outlinedButtonBorder.copy(
                brush = Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.outlineVariant, MaterialTheme.colorScheme.outlineVariant))
            ),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("重启应用", fontSize = 14.sp)
        }

        // 关闭应用
        OutlinedButton(
            onClick = onClose,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            border = ButtonDefaults.outlinedButtonBorder.copy(
                brush = Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.outlineVariant, MaterialTheme.colorScheme.outlineVariant))
            ),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("关闭应用", fontSize = 14.sp)
        }
    }
}

// 解析错误详情
private data class ParsedErrorInfo(
    val timestamp: String? = null,
    val appVersion: String? = null,
    val deviceModel: String? = null,
    val androidVersion: String? = null,
    val errorType: String? = null,
    val errorMessage: String? = null,
    val exitCode: String? = null
)

private fun parseErrorDetails(errorDetails: String?): ParsedErrorInfo {
    if (errorDetails.isNullOrEmpty()) return ParsedErrorInfo()

    val lines = errorDetails.lines()
    var timestamp: String? = null
    var appVersion: String? = null
    var deviceModel: String? = null
    var androidVersion: String? = null
    var errorType: String? = null
    var errorMessage: String? = null
    var exitCode: String? = null

    for (line in lines) {
        when {
            line.startsWith("发生时间:") -> timestamp = line.substringAfter(":").trim()
            line.startsWith("应用版本:") -> appVersion = line.substringAfter(":").trim()
            line.startsWith("设备型号:") -> deviceModel = line.substringAfter(":").trim()
            line.startsWith("Android 版本:") || line.startsWith("Android:") -> 
                androidVersion = line.substringAfter(":").trim()
            line.startsWith("错误类型:") || line.startsWith("异常类型:") -> 
                errorType = line.substringAfter(":").trim()
            line.startsWith("错误信息:") || line.startsWith("异常信息:") -> 
                errorMessage = line.substringAfter(":").trim()
            line.startsWith("退出代码:") -> exitCode = line.substringAfter(":").trim()
            line.startsWith("C层错误:") && errorMessage.isNullOrEmpty() -> 
                errorMessage = line.substringAfter(":").trim()
        }
    }

    return ParsedErrorInfo(
        timestamp = timestamp,
        appVersion = appVersion,
        deviceModel = deviceModel,
        androidVersion = androidVersion,
        errorType = errorType,
        errorMessage = errorMessage,
        exitCode = exitCode
    )
}

private fun shareLog(
    context: android.content.Context,
    errorDetails: String?,
    stackTrace: String?
) {
    try {
        val logDir = File(context.filesDir, "crash_logs").apply {
            if (!exists()) mkdirs()
        }
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val logFile = File(logDir, "crash_${sdf.format(Date())}.log")

        val logContent = buildString {
            append("=" .repeat(60))
            append("\n")
            append("                    RaLaunch 崩溃报告\n")
            append("=" .repeat(60))
            append("\n\n")

            errorDetails?.takeIf { it.isNotEmpty() }?.let {
                append("【基本信息】\n")
                append(it)
                append("\n\n")
            }

            stackTrace?.takeIf { it.isNotEmpty() }?.let {
                append("【详细日志】\n")
                append("-".repeat(40))
                append("\n")
                append(it)
            }

            if (isEmpty()) append("无法获取错误日志")
        }

        FileWriter(logFile).use { it.write(logContent) }

        val fileUri = FileProvider.getUriForFile(
            context,
            "com.app.ralaunch.fileprovider",
            logFile
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, fileUri)
            putExtra(Intent.EXTRA_SUBJECT, "RaLaunch 崩溃日志")
            putExtra(Intent.EXTRA_TEXT, "RaLaunch 崩溃日志 - ${sdf.format(Date())}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(
            Intent.createChooser(shareIntent, "分享崩溃日志").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    } catch (e: Exception) {
        Toast.makeText(context, "分享日志失败: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
