package com.app.ralaunch.shared.ui.screens.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.ralaunch.shared.domain.model.GameItem
import com.app.ralaunch.shared.domain.repository.GameRepository
import com.app.ralaunch.shared.ui.navigation.NavDestination
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 主页面 ViewModel (MVI 架构)
 */
class MainViewModel(
    private val gameRepository: GameRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _effect = Channel<MainEffect>(Channel.BUFFERED)
    val effect: Flow<MainEffect> = _effect.receiveAsFlow()

    init {
        observeGames()
    }

    /**
     * 处理事件
     */
    fun onEvent(event: MainEvent) {
        when (event) {
            is MainEvent.LoadGames -> loadGames()
            is MainEvent.RefreshGames -> refreshGames()
            is MainEvent.SelectGame -> selectGame(event.game)
            is MainEvent.ClearSelection -> clearSelection()
            is MainEvent.LaunchSelectedGame -> launchSelectedGame()
            is MainEvent.NavigateTo -> navigateTo(event.destination)
            is MainEvent.ShowDeleteDialog -> showDeleteDialog(event.game)
            is MainEvent.DismissDeleteDialog -> dismissDeleteDialog()
            is MainEvent.ConfirmDelete -> confirmDelete()
            is MainEvent.AddGame -> addGame(event.game)
            is MainEvent.ClearError -> clearError()
        }
    }

    private fun observeGames() {
        viewModelScope.launch {
            gameRepository.getGames()
                .catch { e ->
                    _uiState.update { it.copy(error = e.message, isLoading = false) }
                }
                .collect { games ->
                    _uiState.update { 
                        it.copy(
                            games = games, 
                            isLoading = false,
                            // 如果选中的游戏被删除，清除选中状态
                            selectedGame = if (games.any { g -> g.id == it.selectedGame?.id }) {
                                it.selectedGame
                            } else {
                                null
                            }
                        )
                    }
                }
        }
    }

    private fun loadGames() {
        _uiState.update { it.copy(isLoading = true) }
        // observeGames 已经在收集数据
    }

    private fun refreshGames() {
        // 触发重新加载
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            // Flow 会自动更新
        }
    }

    private fun selectGame(game: GameItem) {
        _uiState.update { it.copy(selectedGame = game) }
    }

    private fun clearSelection() {
        _uiState.update { it.copy(selectedGame = null) }
    }

    private fun launchSelectedGame() {
        val game = _uiState.value.selectedGame
        if (game != null) {
            viewModelScope.launch {
                _effect.send(MainEffect.LaunchGame(game))
            }
        } else {
            viewModelScope.launch {
                _effect.send(MainEffect.ShowToast("请先选择一个游戏"))
            }
        }
    }

    private fun navigateTo(destination: NavDestination) {
        _uiState.update { it.copy(currentDestination = destination) }
    }

    private fun showDeleteDialog(game: GameItem) {
        _uiState.update { 
            it.copy(
                showDeleteDialog = true, 
                gameToDelete = game
            ) 
        }
    }

    private fun dismissDeleteDialog() {
        _uiState.update { 
            it.copy(
                showDeleteDialog = false, 
                gameToDelete = null
            ) 
        }
    }

    private fun confirmDelete() {
        val game = _uiState.value.gameToDelete ?: return
        
        viewModelScope.launch {
            try {
                gameRepository.deleteGame(game.id)
                _effect.send(MainEffect.ShowToast("游戏已删除"))
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "删除失败: ${e.message}") }
            }
        }
        
        dismissDeleteDialog()
    }

    private fun addGame(game: GameItem) {
        viewModelScope.launch {
            try {
                gameRepository.addGame(game)
                _effect.send(MainEffect.ShowToast("游戏添加成功"))
                navigateTo(NavDestination.GAMES)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "添加失败: ${e.message}") }
            }
        }
    }

    private fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
