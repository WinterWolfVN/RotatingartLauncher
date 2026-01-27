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
 * 外观设置状态
 */
data class AppearanceState(
    val themeMode: Int = 0,              // 0=跟随系统, 1=深色, 2=浅色
    val themeColor: Int = 0,             // 主题颜色ID
    val backgroundType: Int = 0,         // 0=默认, 1=图片, 2=视频
    val backgroundOpacity: Int = 0,      // 背景透明度 0-100
    val videoPlaybackSpeed: Float = 1.0f,// 视频播放速度 0.5-2.0
    val language: String = "简体中文"
)

/**
 * 外观设置内容 - 跨平台
 */
@Composable
fun AppearanceSettingsContent(
    state: AppearanceState,
    onThemeModeChange: (Int) -> Unit,
    onThemeColorClick: () -> Unit,
    onBackgroundTypeChange: (Int) -> Unit,
    onSelectImageClick: () -> Unit,
    onSelectVideoClick: () -> Unit,
    onBackgroundOpacityChange: (Int) -> Unit,
    onVideoSpeedChange: (Float) -> Unit,
    onRestoreDefaultBackground: () -> Unit,
    onLanguageClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 主题设置
        ThemeSection(
            themeMode = state.themeMode,
            onThemeModeChange = onThemeModeChange,
            themeColor = state.themeColor,
            onThemeColorClick = onThemeColorClick
        )

        // 背景设置
        BackgroundSection(
            backgroundType = state.backgroundType,
            backgroundOpacity = state.backgroundOpacity,
            videoSpeed = state.videoPlaybackSpeed,
            onSelectImageClick = onSelectImageClick,
            onSelectVideoClick = onSelectVideoClick,
            onOpacityChange = onBackgroundOpacityChange,
            onVideoSpeedChange = onVideoSpeedChange,
            onRestoreDefault = onRestoreDefaultBackground
        )

        // 语言设置
        LanguageSection(
            language = state.language,
            onLanguageClick = onLanguageClick
        )
    }
}

@Composable
private fun ThemeSection(
    themeMode: Int,
    onThemeModeChange: (Int) -> Unit,
    themeColor: Int,
    onThemeColorClick: () -> Unit
) {
    SettingsSection(title = "主题") {
        // 主题模式
        ClickableSettingItem(
            title = "深色模式",
            subtitle = "选择应用主题",
            value = when (themeMode) {
                0 -> "跟随系统"
                1 -> "深色"
                2 -> "浅色"
                else -> "跟随系统"
            },
            icon = Icons.Default.DarkMode,
            onClick = {
                // 循环切换主题模式
                val nextMode = (themeMode + 1) % 3
                onThemeModeChange(nextMode)
            }
        )

        SettingsDivider()

        // 主题颜色
        ClickableSettingItem(
            title = "主题颜色",
            subtitle = "自定义应用主色调",
            icon = Icons.Default.Palette,
            onClick = onThemeColorClick
        )
    }
}

@Composable
private fun BackgroundSection(
    backgroundType: Int,
    backgroundOpacity: Int,
    videoSpeed: Float,
    onSelectImageClick: () -> Unit,
    onSelectVideoClick: () -> Unit,
    onOpacityChange: (Int) -> Unit,
    onVideoSpeedChange: (Float) -> Unit,
    onRestoreDefault: () -> Unit
) {
    val hasBackground = backgroundType != 0

    SettingsSection(title = "背景") {
        // 选择背景图片
        ClickableSettingItem(
            title = "背景图片",
            subtitle = if (backgroundType == 1) "已设置" else "点击选择图片",
            icon = Icons.Default.Image,
            onClick = onSelectImageClick
        )

        SettingsDivider()

        // 选择背景视频
        ClickableSettingItem(
            title = "背景视频",
            subtitle = if (backgroundType == 2) "已设置" else "点击选择视频",
            icon = Icons.Default.VideoLibrary,
            onClick = onSelectVideoClick
        )

        // 背景透明度（仅在有背景时显示）
        if (hasBackground) {
            SettingsDivider()

            SliderSettingItem(
                title = "背景透明度",
                subtitle = "调整UI元素透明度",
                icon = Icons.Default.Opacity,
                value = backgroundOpacity.toFloat(),
                valueRange = 0f..100f,
                steps = 9,
                valueLabel = "${backgroundOpacity}%",
                onValueChange = { onOpacityChange(it.toInt()) }
            )
        }

        // 视频播放速度（仅在视频背景时显示）
        if (backgroundType == 2) {
            SettingsDivider()

            SliderSettingItem(
                title = "视频播放速度",
                subtitle = "调整背景视频播放速度",
                icon = Icons.Default.Speed,
                value = videoSpeed,
                valueRange = 0.5f..2.0f,
                steps = 5,
                valueLabel = String.format("%.1fx", videoSpeed),
                onValueChange = onVideoSpeedChange
            )
        }

        // 恢复默认背景（仅在有背景时显示）
        if (hasBackground) {
            SettingsDivider()

            ClickableSettingItem(
                title = "恢复默认背景",
                subtitle = "清除自定义背景设置",
                icon = Icons.Default.Restore,
                onClick = onRestoreDefault
            )
        }
    }
}

@Composable
private fun LanguageSection(
    language: String,
    onLanguageClick: () -> Unit
) {
    SettingsSection(title = "语言") {
        ClickableSettingItem(
            title = "应用语言",
            subtitle = "更改后需重启生效",
            value = language,
            icon = Icons.Default.Language,
            onClick = onLanguageClick
        )
    }
}
