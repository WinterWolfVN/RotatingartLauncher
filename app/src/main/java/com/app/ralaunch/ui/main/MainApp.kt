package com.app.ralaunch.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.app.ralaunch.data.model.GameItem
import com.app.ralaunch.shared.ui.components.AppNavigationRail
import com.app.ralaunch.shared.ui.navigation.*
import com.app.ralaunch.shared.ui.theme.RaLaunchTheme
import com.app.ralaunch.shared.ui.model.GameItemUi
import com.app.ralaunch.shared.ui.components.game.GameListContent

/**
 * 主应用 Composable
 * 替代 activity_main.xml + PageNavigator
 */
@Composable
fun MainApp(
    navState: NavState,
    modifier: Modifier = Modifier,
    // 应用 Logo
    appLogo: (@Composable () -> Unit)? = null,
    // 游戏列表相关
    games: List<GameItemUi> = emptyList(),
    selectedGame: GameItemUi? = null,
    isLoading: Boolean = false,
    onGameClick: (GameItemUi) -> Unit = {},
    onGameLongClick: (GameItemUi) -> Unit = {},
    onLaunchClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
    iconLoader: @Composable (String?, Modifier) -> Unit = { _, _ -> },
    // 页面内容 Slots
    controlsContent: @Composable () -> Unit = { PlaceholderScreen("控制布局") },
    downloadContent: @Composable () -> Unit = { PlaceholderScreen("下载") },
    settingsContent: @Composable () -> Unit = { PlaceholderScreen("设置") },
    importContent: @Composable () -> Unit = { PlaceholderScreen("导入游戏") },
    controlStoreContent: @Composable () -> Unit = { PlaceholderScreen("控制包商店") },
    fileBrowserContent: @Composable (initialPath: String, allowedExtensions: List<String>, fileType: String) -> Unit = { _, _, _ -> PlaceholderScreen("文件浏览") },
    // 背景层 (视频/图片)
    backgroundLayer: @Composable BoxScope.() -> Unit = {}
) {
    Box(modifier = modifier.fillMaxSize()) {
        // 背景层
        backgroundLayer()

        // 主内容 - NavigationRail + Content
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
        ) {
            // 左侧导航栏
            AppNavigationRail(
                currentDestination = navState.currentDestination,
                onNavigate = { navState.navigateTo(it) },
                logo = appLogo
            )

            // 主内容区域
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                val currentScreen = navState.currentScreen
                
                when (currentScreen) {
                    is Screen.Games -> {
                        GamesPageContent(
                            games = games,
                            selectedGame = selectedGame,
                            isLoading = isLoading,
                            onGameClick = onGameClick,
                            onGameLongClick = onGameLongClick,
                            onLaunchClick = onLaunchClick,
                            onDeleteClick = onDeleteClick,
                            onAddClick = { navState.navigateTo(Screen.Import) },
                            iconLoader = iconLoader
                        )
                    }
                    is Screen.Controls -> controlsContent()
                    is Screen.Download -> downloadContent()
                    is Screen.Settings -> settingsContent()
                    is Screen.Import -> importContent()
                    is Screen.ControlStore -> controlStoreContent()
                    is Screen.FileBrowser -> fileBrowserContent(currentScreen.initialPath, currentScreen.allowedExtensions, currentScreen.fileType)
                    is Screen.GameDetail -> PlaceholderScreen("游戏详情: ${currentScreen.gameId}")
                    is Screen.ControlEditor -> PlaceholderScreen("布局编辑器")
                    is Screen.Initialization -> { /* 已移至独立 Activity */ }
                }

                // 加载指示器
                if (isLoading && currentScreen is Screen.Games) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}

/**
 * 游戏列表页面内容
 */
@Composable
private fun GamesPageContent(
    games: List<GameItemUi>,
    selectedGame: GameItemUi?,
    isLoading: Boolean,
    onGameClick: (GameItemUi) -> Unit,
    onGameLongClick: (GameItemUi) -> Unit,
    onLaunchClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onAddClick: () -> Unit,
    iconLoader: @Composable (String?, Modifier) -> Unit
) {
    GameListContent(
        games = games,
        selectedGame = selectedGame,
        onGameClick = onGameClick,
        onGameLongClick = onGameLongClick,
        onLaunchClick = onLaunchClick,
        onDeleteClick = onDeleteClick,
        onAddClick = onAddClick,
        isLoading = isLoading,
        iconLoader = iconLoader
    )
}

/**
 * 占位屏幕 - 用于尚未实现的页面
 */
@Composable
fun PlaceholderScreen(
    title: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

/**
 * MainApp 预览
 */
@Composable
fun MainAppPreview() {
    RaLaunchTheme {
        MainApp(
            navState = rememberNavState(),
            games = listOf(
                GameItemUi(
                    id = "1",
                    name = "Sample Game",
                    iconPath = null
                )
            )
        )
    }
}
