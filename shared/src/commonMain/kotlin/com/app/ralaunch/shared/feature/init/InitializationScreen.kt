package com.app.ralaunch.shared.feature.init

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.app.ralaunch.shared.core.model.ui.ComponentState
import com.app.ralaunch.shared.core.model.ui.InitStep
import com.app.ralaunch.shared.core.model.ui.InitUiState

/**
 * 初始化页面主屏幕 - 跨平台
 */
@Composable
fun InitializationScreen(
    uiState: InitUiState,
    strings: InitStrings,
    onAcceptLegal: () -> Unit,
    onDeclineLegal: () -> Unit,
    onOpenOfficialLink: () -> Unit,
    onRequestPermissions: () -> Unit,
    onSkipPermissions: () -> Unit,
    onStartExtraction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                )
            )
    ) {
        // 步骤指示器
        StepIndicator(
            currentStep = uiState.step,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
        )

        // 内容区域
        Crossfade(
            targetState = uiState.step,
            animationSpec = tween(300),
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 60.dp),
            label = "init_content"
        ) { step ->
            when (step) {
                InitStep.LEGAL -> LegalAgreementContent(
                    strings = strings,
                    onAccept = onAcceptLegal,
                    onDecline = onDeclineLegal,
                    onOpenOfficialLink = onOpenOfficialLink
                )
                InitStep.PERMISSION -> PermissionRequestContent(
                    strings = strings,
                    hasPermissions = uiState.hasPermissions,
                    onRequestPermissions = onRequestPermissions,
                    onSkipPermissions = onSkipPermissions
                )
                InitStep.EXTRACTION -> ExtractionContent(
                    strings = strings,
                    components = uiState.components,
                    isExtracting = uiState.isExtracting,
                    overallProgress = uiState.overallProgress,
                    statusMessage = uiState.statusMessage,
                    onStartExtraction = onStartExtraction
                )
            }
        }

        // 错误提示
        uiState.error?.let { error ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                Text(error)
            }
        }
    }
}

/**
 * 初始化字符串 - 用于跨平台传递本地化字符串
 */
data class InitStrings(
    val legalTitle: String = "法律声明",
    val legalTerms: String = "",
    val legalOfficialDownload: String = "官方下载",
    val exit: String = "退出",
    val acceptAndContinue: String = "同意并继续",
    val grantPermissions: String = "授予权限",
    val permissionDesc: String = "需要存储权限才能正常运行",
    val permissionsGrantedCheck: String = "权限已授予",
    val continueText: String = "继续",
    val skip: String = "跳过",
    val installing: String = "正在安装",
    val clickToStart: String = "点击开始安装组件"
)

/**
 * 步骤指示器
 */
@Composable
private fun StepIndicator(
    currentStep: InitStep,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        InitStep.entries.forEachIndexed { index, step ->
            val isActive = step == currentStep
            val isPassed = step.ordinal < currentStep.ordinal

            if (index > 0) {
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(2.dp)
                        .background(
                            if (isPassed || isActive)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.outlineVariant
                        )
                )
            }

            Surface(
                shape = MaterialTheme.shapes.small,
                color = when {
                    isActive -> MaterialTheme.colorScheme.primary
                    isPassed -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier.size(if (isActive) 12.dp else 8.dp)
            ) {}
        }
    }
}

/**
 * 法律声明页面
 */
@Composable
fun LegalAgreementContent(
    strings: InitStrings,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onOpenOfficialLink: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Icon(
            imageVector = Icons.Default.Security,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = strings.legalTitle,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = strings.legalTerms,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(onClick = onOpenOfficialLink) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.legalOfficialDownload)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = onDecline,
                modifier = Modifier.weight(1f)
            ) {
                Text(strings.exit)
            }

            Button(
                onClick = onAccept,
                modifier = Modifier.weight(1f)
            ) {
                Text(strings.acceptAndContinue)
            }
        }
    }
}

/**
 * 权限请求页面
 */
@Composable
fun PermissionRequestContent(
    strings: InitStrings,
    hasPermissions: Boolean,
    onRequestPermissions: () -> Unit,
    onSkipPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (hasPermissions) Icons.Default.CheckCircle else Icons.Default.FolderOpen,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = if (hasPermissions) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = strings.grantPermissions,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = strings.permissionDesc,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (hasPermissions) {
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = strings.permissionsGrantedCheck,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onRequestPermissions,
            modifier = Modifier.fillMaxWidth(0.7f)
        ) {
            Icon(
                imageVector = if (hasPermissions) Icons.Default.ArrowForward else Icons.Default.Security,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (hasPermissions) strings.continueText else strings.grantPermissions)
        }

        if (!hasPermissions) {
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onSkipPermissions) {
                Text(strings.skip)
            }
        }
    }
}

/**
 * 组件解压页面
 */
@Composable
fun ExtractionContent(
    strings: InitStrings,
    components: List<ComponentState>,
    isExtracting: Boolean,
    overallProgress: Int,
    statusMessage: String,
    onStartExtraction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            text = strings.installing,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = strings.clickToStart,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(components) { component ->
                ComponentItem(component)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isExtracting) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$overallProgress%",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { overallProgress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onStartExtraction,
            enabled = !isExtracting,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isExtracting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(strings.installing)
            } else {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(strings.acceptAndContinue)
            }
        }
    }
}

/**
 * 单个组件项
 */
@Composable
private fun ComponentItem(component: ComponentState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        when {
                            component.isInstalled -> MaterialTheme.colorScheme.primaryContainer
                            component.progress > 0 -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                when {
                    component.isInstalled -> Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    component.progress > 0 -> CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                    else -> Icon(
                        Icons.Default.Inventory,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = component.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = component.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (component.progress > 0 && !component.isInstalled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { component.progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                }
            }
        }
    }
}
