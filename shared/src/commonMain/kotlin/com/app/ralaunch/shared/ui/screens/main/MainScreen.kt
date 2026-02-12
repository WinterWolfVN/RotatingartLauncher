package com.app.ralaunch.shared.ui.screens.main

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.app.ralaunch.shared.domain.model.GameItem
import com.app.ralaunch.shared.ui.components.AppNavigationRail
import com.app.ralaunch.shared.ui.components.game.GameListContent
import com.app.ralaunch.shared.ui.model.toUiModel
import com.app.ralaunch.shared.ui.navigation.NavDestination

/**
 * 主页面 (Compose)
 */
@Composable
fun MainScreen(
    uiState: MainUiState,
    onEvent: (MainEvent) -> Unit,
    onLaunchGame: (GameItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxSize()) {
        // 导航栏
        AppNavigationRail(
            currentDestination = uiState.currentDestination,
            onNavigate = { onEvent(MainEvent.NavigateTo(it)) }
        )

        // 主内容区域
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            when (uiState.currentDestination) {
                NavDestination.GAMES -> {
                    GamesContent(
                        uiState = uiState,
                        onEvent = onEvent,
                        onLaunchGame = onLaunchGame
                    )
                }
                NavDestination.CONTROLS -> {
                    PlaceholderContent(title = "控制布局")
                }
                NavDestination.DOWNLOAD -> {
                    PlaceholderContent(title = "下载")
                }
                NavDestination.IMPORT -> {
                    PlaceholderContent(title = "导入游戏")
                }
                NavDestination.SETTINGS -> {
                    PlaceholderContent(title = "设置")
                }
            }

            // 加载指示器
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // 错误提示
            uiState.error?.let { error ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = { onEvent(MainEvent.ClearError) }) {
                            Text("关闭")
                        }
                    }
                ) {
                    Text(error)
                }
            }
        }
    }

    // 删除确认对话框
    if (uiState.showDeleteDialog && uiState.gameToDelete != null) {
        DeleteGameDialog(
            game = uiState.gameToDelete,
            onConfirm = { onEvent(MainEvent.ConfirmDelete) },
            onDismiss = { onEvent(MainEvent.DismissDeleteDialog) }
        )
    }
}

/**
 * 游戏列表内容
 */
@Composable
private fun GamesContent(
    uiState: MainUiState,
    onEvent: (MainEvent) -> Unit,
    onLaunchGame: (GameItem) -> Unit
) {
    // 转换为 UI 模型
    val gamesUi = remember(uiState.games) {
        uiState.games.map { it.toUiModel() }
    }
    val selectedGameUi = remember(uiState.selectedGame) {
        uiState.selectedGame?.toUiModel()
    }

    GameListContent(
        games = gamesUi,
        selectedGame = selectedGameUi,
        onGameClick = { gameUi ->
            val game = uiState.games.find { it.id == gameUi.id }
            game?.let { onEvent(MainEvent.SelectGame(it)) }
        },
        onGameLongClick = {},
        onLaunchClick = {
            uiState.selectedGame?.let { onLaunchGame(it) }
        },
        onDeleteClick = {
            uiState.selectedGame?.let { onEvent(MainEvent.ShowDeleteDialog(it)) }
        },
        onAddClick = { onEvent(MainEvent.NavigateTo(NavDestination.IMPORT)) },
        isLoading = uiState.isLoading
    )
}

/**
 * 占位内容
 */
@Composable
private fun PlaceholderContent(title: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
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
 * 删除游戏确认对话框
 */
@Composable
private fun DeleteGameDialog(
    game: GameItem,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除游戏") },
        text = {
            Text("确定要删除 \"${game.displayedName}\" 吗？此操作不可撤销。")
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("删除")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
