package com.app.ralaunch.shared.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 开发者设置状态
 */
data class DeveloperState(
    // 日志相关
    val loggingEnabled: Boolean = false,
    val verboseLogging: Boolean = false,
    
    // 性能相关
    val bigCoreAffinityEnabled: Boolean = false,
    val killLauncherUIEnabled: Boolean = false,
    val lowLatencyAudioEnabled: Boolean = false,
    
    // .NET 运行时
    val serverGCEnabled: Boolean = true,
    val concurrentGCEnabled: Boolean = true,
    val tieredCompilationEnabled: Boolean = true,
    
    // FNA 优化
    val fnaMapBufferRangeOptEnabled: Boolean = false
)

/**
 * 开发者设置内容 - 跨平台
 */
@Composable
fun DeveloperSettingsContent(
    state: DeveloperState,
    // 日志回调
    onLoggingChange: (Boolean) -> Unit,
    onVerboseLoggingChange: (Boolean) -> Unit,
    onViewLogsClick: () -> Unit,
    onExportLogsClick: () -> Unit,
    onClearCacheClick: () -> Unit,
    // 性能回调
    onBigCoreAffinityChange: (Boolean) -> Unit,
    onKillLauncherUIChange: (Boolean) -> Unit,
    onLowLatencyAudioChange: (Boolean) -> Unit,
    // .NET 运行时回调
    onServerGCChange: (Boolean) -> Unit,
    onConcurrentGCChange: (Boolean) -> Unit,
    onTieredCompilationChange: (Boolean) -> Unit,
    // FNA 回调
    onFnaMapBufferRangeOptChange: (Boolean) -> Unit,
    // 其他
    onForceReinstallPatchesClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 日志设置
        LoggingSection(
            loggingEnabled = state.loggingEnabled,
            verboseLogging = state.verboseLogging,
            onLoggingChange = onLoggingChange,
            onVerboseLoggingChange = onVerboseLoggingChange,
            onViewLogsClick = onViewLogsClick,
            onExportLogsClick = onExportLogsClick
        )

        // 性能设置
        PerformanceSection(
            bigCoreAffinityEnabled = state.bigCoreAffinityEnabled,
            killLauncherUIEnabled = state.killLauncherUIEnabled,
            lowLatencyAudioEnabled = state.lowLatencyAudioEnabled,
            onBigCoreAffinityChange = onBigCoreAffinityChange,
            onKillLauncherUIChange = onKillLauncherUIChange,
            onLowLatencyAudioChange = onLowLatencyAudioChange
        )

        // .NET 运行时设置
        DotNetRuntimeSection(
            serverGCEnabled = state.serverGCEnabled,
            concurrentGCEnabled = state.concurrentGCEnabled,
            tieredCompilationEnabled = state.tieredCompilationEnabled,
            onServerGCChange = onServerGCChange,
            onConcurrentGCChange = onConcurrentGCChange,
            onTieredCompilationChange = onTieredCompilationChange
        )

        // FNA 优化设置
        FnaOptimizationSection(
            mapBufferRangeOptEnabled = state.fnaMapBufferRangeOptEnabled,
            onMapBufferRangeOptChange = onFnaMapBufferRangeOptChange
        )

        // 维护操作
        MaintenanceSection(
            onClearCacheClick = onClearCacheClick,
            onForceReinstallPatchesClick = onForceReinstallPatchesClick
        )
    }
}

@Composable
private fun LoggingSection(
    loggingEnabled: Boolean,
    verboseLogging: Boolean,
    onLoggingChange: (Boolean) -> Unit,
    onVerboseLoggingChange: (Boolean) -> Unit,
    onViewLogsClick: () -> Unit,
    onExportLogsClick: () -> Unit
) {
    SettingsSection(title = "日志") {
        SwitchSettingItem(
            title = "启用日志系统",
            subtitle = "记录应用运行日志",
            icon = Icons.Default.Description,
            checked = loggingEnabled,
            onCheckedChange = onLoggingChange
        )

        SettingsDivider()

        SwitchSettingItem(
            title = "详细日志",
            subtitle = "输出更多调试信息",
            icon = Icons.Default.BugReport,
            checked = verboseLogging,
            onCheckedChange = onVerboseLoggingChange
        )

        SettingsDivider()

        ClickableSettingItem(
            title = "查看日志",
            subtitle = "查看最近的运行日志",
            icon = Icons.Default.Visibility,
            onClick = onViewLogsClick
        )

        SettingsDivider()

        ClickableSettingItem(
            title = "导出日志",
            subtitle = "分享日志文件以便调试",
            icon = Icons.Default.Share,
            onClick = onExportLogsClick
        )
    }
}

@Composable
private fun PerformanceSection(
    bigCoreAffinityEnabled: Boolean,
    killLauncherUIEnabled: Boolean,
    lowLatencyAudioEnabled: Boolean,
    onBigCoreAffinityChange: (Boolean) -> Unit,
    onKillLauncherUIChange: (Boolean) -> Unit,
    onLowLatencyAudioChange: (Boolean) -> Unit
) {
    SettingsSection(title = "性能") {
        SwitchSettingItem(
            title = "大核心亲和性",
            subtitle = "将游戏线程绑定到高性能核心",
            icon = Icons.Default.Memory,
            checked = bigCoreAffinityEnabled,
            onCheckedChange = onBigCoreAffinityChange
        )

        SettingsDivider()

        SwitchSettingItem(
            title = "启动后关闭启动器UI",
            subtitle = "释放更多内存给游戏",
            icon = Icons.Default.ExitToApp,
            checked = killLauncherUIEnabled,
            onCheckedChange = onKillLauncherUIChange
        )

        SettingsDivider()

        SwitchSettingItem(
            title = "低延迟音频",
            subtitle = "降低音频延迟，可能增加功耗",
            icon = Icons.Default.Audiotrack,
            checked = lowLatencyAudioEnabled,
            onCheckedChange = onLowLatencyAudioChange
        )
    }
}

@Composable
private fun DotNetRuntimeSection(
    serverGCEnabled: Boolean,
    concurrentGCEnabled: Boolean,
    tieredCompilationEnabled: Boolean,
    onServerGCChange: (Boolean) -> Unit,
    onConcurrentGCChange: (Boolean) -> Unit,
    onTieredCompilationChange: (Boolean) -> Unit
) {
    SettingsSection(title = ".NET 运行时") {
        SwitchSettingItem(
            title = "Server GC",
            subtitle = "使用服务器模式垃圾回收",
            icon = Icons.Default.Storage,
            checked = serverGCEnabled,
            onCheckedChange = onServerGCChange
        )

        SettingsDivider()

        SwitchSettingItem(
            title = "Concurrent GC",
            subtitle = "启用并发垃圾回收",
            icon = Icons.Default.Sync,
            checked = concurrentGCEnabled,
            onCheckedChange = onConcurrentGCChange
        )

        SettingsDivider()

        SwitchSettingItem(
            title = "Tiered Compilation",
            subtitle = "启用分层编译优化",
            icon = Icons.Default.Layers,
            checked = tieredCompilationEnabled,
            onCheckedChange = onTieredCompilationChange
        )
    }
}

@Composable
private fun FnaOptimizationSection(
    mapBufferRangeOptEnabled: Boolean,
    onMapBufferRangeOptChange: (Boolean) -> Unit
) {
    SettingsSection(title = "FNA 优化") {
        SwitchSettingItem(
            title = "MapBufferRange 优化",
            subtitle = "启用 OpenGL 缓冲区优化",
            icon = Icons.Default.Speed,
            checked = mapBufferRangeOptEnabled,
            onCheckedChange = onMapBufferRangeOptChange
        )
    }
}

@Composable
private fun MaintenanceSection(
    onClearCacheClick: () -> Unit,
    onForceReinstallPatchesClick: () -> Unit
) {
    SettingsSection(title = "维护") {
        ClickableSettingItem(
            title = "清除缓存",
            subtitle = "清理应用缓存文件",
            icon = Icons.Default.DeleteSweep,
            onClick = onClearCacheClick
        )

        SettingsDivider()

        ClickableSettingItem(
            title = "强制重装补丁",
            subtitle = "重新安装所有内置补丁",
            icon = Icons.Default.Refresh,
            onClick = onForceReinstallPatchesClick
        )
    }
}
