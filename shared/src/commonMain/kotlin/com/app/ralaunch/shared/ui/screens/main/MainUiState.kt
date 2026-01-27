package com.app.ralaunch.shared.ui.screens.main

import com.app.ralaunch.shared.domain.model.GameItem
import com.app.ralaunch.shared.ui.navigation.NavDestination

/**
 * 主页面 UI 状态
 */
data class MainUiState(
    val games: List<GameItem> = emptyList(),
    val selectedGame: GameItem? = null,
    val currentDestination: NavDestination = NavDestination.GAMES,
    val isLoading: Boolean = true,
    val error: String? = null,
    val showDeleteDialog: Boolean = false,
    val gameToDelete: GameItem? = null
)

/**
 * 主页面事件
 */
sealed class MainEvent {
    data object LoadGames : MainEvent()
    data object RefreshGames : MainEvent()
    data class SelectGame(val game: GameItem) : MainEvent()
    data object ClearSelection : MainEvent()
    data object LaunchSelectedGame : MainEvent()
    data class NavigateTo(val destination: NavDestination) : MainEvent()
    data class ShowDeleteDialog(val game: GameItem) : MainEvent()
    data object DismissDeleteDialog : MainEvent()
    data object ConfirmDelete : MainEvent()
    data class AddGame(val game: GameItem) : MainEvent()
    data object ClearError : MainEvent()
}

/**
 * 主页面副作用
 */
sealed class MainEffect {
    data class LaunchGame(val game: GameItem) : MainEffect()
    data class ShowToast(val message: String) : MainEffect()
    object NavigateToImport : MainEffect()
}
