package com.app.ralaunch.crash

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * 崩溃报告页面 - Compose 版本
 * 横屏极简设计
 */
@Composable
fun CrashReportScreen(
    errorDetails: String?,
    stackTrace: String?,
    onClose: () -> Unit,
    onRestart: () -> Unit
) {
    val context = LocalContext.current
    var detailsExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A1A1A),
                        Color(0xFF0D0D0D)
                    )
                )
            )
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 标题
            Text(
                text = "应用已停止运行",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF44336),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )

            // 错误详情区域
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    // 错误信息
                    Text(
                        text = errorDetails ?: "无法获取错误详情",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White.copy(alpha = 0.9f),
                        lineHeight = 20.sp
                    )

                    // 堆栈跟踪（可展开）
                    AnimatedVisibility(
                        visible = detailsExpanded && !stackTrace.isNullOrEmpty(),
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Text(
                            text = stackTrace ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White.copy(alpha = 0.7f),
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 按钮区域 - 横向排列
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 显示/隐藏堆栈
                if (!stackTrace.isNullOrEmpty()) {
                    TextButton(
                        onClick = { detailsExpanded = !detailsExpanded },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color.White.copy(alpha = 0.8f)
                        )
                    ) {
                        Text(
                            text = if (detailsExpanded) "隐藏堆栈" else "显示堆栈",
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // 分享日志
                Button(
                    onClick = {
                        shareLog(context, errorDetails, stackTrace)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("分享日志", fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.width(6.dp))

                // 重启应用
                OutlinedButton(
                    onClick = onRestart,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("重启应用", fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.width(6.dp))

                // 关闭应用
                OutlinedButton(
                    onClick = onClose,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("关闭应用", fontSize = 13.sp)
                }
            }
        }
    }
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
            errorDetails?.takeIf { it.isNotEmpty() }?.let { append(it) }
            stackTrace?.takeIf { it.isNotEmpty() }?.let {
                if (isNotEmpty()) append("\n\n=== 堆栈跟踪 ===\n")
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
            putExtra(Intent.EXTRA_SUBJECT, "应用崩溃日志")
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
