package com.app.ralaunch.feature.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.app.ralaunch.R
import com.app.ralaunch.core.common.SettingsAccess
import com.app.ralaunch.feature.main.AddGameUseCase
import com.app.ralaunch.feature.main.DeleteGameFilesUseCase
import com.app.ralaunch.feature.main.DeleteGameUseCase
import com.app.ralaunch.feature.main.LaunchGameUseCase
import com.app.ralaunch.feature.main.LoadGamesUseCase
import com.app.ralaunch.feature.main.UpdateGameUseCase
import com.app.ralaunch.core.common.GameDeletionManager
import com.app.ralaunch.core.common.GameLaunchManager
import com.app.ralaunch.shared.core.model.domain.GameItem
import com.app.ralaunch.shared.core.contract.repository.GameRepositoryV2
import com.app.ralaunch.shared.core.model.ui.applyFromUiModel
import com.app.ralaunch.shared.core.model.ui.toUiModels
import com.app.ralaunch.feature.main.contracts.MainUiEffect
import com.app.ralaunch.feature.main.contracts.MainUiEvent
import com.app.ralaunch.feature.main.contracts.MainUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent

class MainViewModel(
    private val appContext: Context,
    private val loadGamesUseCase: LoadGamesUseCase,
    private val addGameUseCase: AddGameUseCase,
    private val updateGameUseCase: UpdateGameUseCase,
    private val deleteGameUseCase: DeleteGameUseCase,
    private val launchGameUseCase: LaunchGameUseCase,
    private val deleteGameFilesUseCase: DeleteGameFilesUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<MainUiEffect>()
    val effects: SharedFlow<MainUiEffect> = _effects.asSharedFlow()

    private val gameItemsMap = mutableMapOf<String, GameItem>()

    init {
        onEvent(MainUiEvent.RefreshRequested)
    }

    fun onEvent(event: MainUiEvent) {
        when (event) {
            is MainUiEvent.RefreshRequested -> refreshGames()
            is MainUiEvent.GameSelected -> selectGame(event.game.id)
            is MainUiEvent.GameEdited -> updateGame(event.game)
            is MainUiEvent.LaunchRequested -> launchSelectedGame()
            is MainUiEvent.DeleteRequested -> requestDeleteSelectedGame()
            is MainUiEvent.DeleteDialogDismissed -> dismissDeleteDialog()
            is MainUiEvent.DeleteConfirmed -> confirmDelete()
            is MainUiEvent.ImportCompleted -> onGameImportComplete(event.game)
            is MainUiEvent.AppResumed -> _uiState.update { it.copy(isVideoPlaying = true) }
            is MainUiEvent.AppPaused -> _uiState.update { it.copy(isVideoPlaying = false) }
        }
    }

    private fun refreshGames(selectedId: String? = _uiState.value.selectedGame?.id) {
        viewModelScope.launch(Dispatchers.IO) {
            val games = loadGamesUseCase().distinctBy { it.id }
            gameItemsMap.clear()
            games.forEach { game ->
                gameItemsMap[game.id] = game
            }
            val uiGames = games.toUiModels()
            val selectedGame = selectedId?.let { id -> uiGames.find { it.id == id } }
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        games = uiGames,
                        selectedGame = selectedGame,
                        gamePendingDeletion = null,
                        deletePosition = -1,
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun selectGame(gameId: String) {
        val selected = _uiState.value.games.find { it.id == gameId } ?: return
        _uiState.update { it.copy(selectedGame = selected) }
    }

    private fun updateGame(updatedGameUi: com.app.ralaunch.shared.core.model.ui.GameItemUi) {
        viewModelScope.launch(Dispatchers.IO) {
            val game = gameItemsMap[updatedGameUi.id] ?: return@launch
            game.applyFromUiModel(updatedGameUi)
            updateGameUseCase(game)
            refreshGames(selectedId = updatedGameUi.id)
        }
    }

    private fun requestDeleteSelectedGame() {
        val selectedGame = _uiState.value.selectedGame
        if (selectedGame == null) {
            emitEffect(MainUiEffect.ShowToast(appContext.getString(R.string.main_select_game_first)))
            return
        }
        val deletePosition = _uiState.value.games.indexOfFirst { it.id == selectedGame.id }
        _uiState.update {
            it.copy(
                gamePendingDeletion = selectedGame,
                deletePosition = deletePosition
            )
        }
    }

    private fun dismissDeleteDialog() {
        _uiState.update {
            it.copy(
                gamePendingDeletion = null,
                deletePosition = -1
            )
        }
    }

    private fun confirmDelete() {
        val pendingGame = _uiState.value.gamePendingDeletion ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val game = gameItemsMap[pendingGame.id] ?: return@launch
            val filesDeleted = deleteGameFilesUseCase(game)
            deleteGameUseCase(game.id)
            refreshGames(selectedId = null)
            if (filesDeleted) {
                emitEffect(MainUiEffect.ShowSuccess(appContext.getString(R.string.main_game_deleted)))
            } else {
                emitEffect(MainUiEffect.ShowToast(appContext.getString(R.string.main_game_deleted_partial)))
            }
        }
    }

    private fun launchSelectedGame() {
        val selectedGame = _uiState.value.selectedGame
        if (selectedGame == null) {
            emitEffect(MainUiEffect.ShowToast(appContext.getString(R.string.main_select_game_first)))
            return
        }

        val game = gameItemsMap[selectedGame.id]
        if (game == null) {
            emitEffect(MainUiEffect.ShowToast(appContext.getString(R.string.main_select_game_first)))
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val success = launchGameUseCase(game)
            if (!success) {
                emitEffect(MainUiEffect.ShowToast(appContext.getString(R.string.game_launch_failed)))
                return@launch
            }
            if (SettingsAccess.isKillLauncherUIAfterLaunch) {
                emitEffect(MainUiEffect.ExitLauncher)
            }
        }
    }

    private fun onGameImportComplete(game: GameItem) {
        viewModelScope.launch(Dispatchers.IO) {
            addGameUseCase(game, 0)
            refreshGames(selectedId = null)
            emitEffect(MainUiEffect.ShowSuccess(appContext.getString(R.string.game_added_success)))
        }
    }

    private fun emitEffect(effect: MainUiEffect) {
        viewModelScope.launch {
            _effects.emit(effect)
        }
    }
}

class MainViewModelFactory(
    private val appContext: Context
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(MainViewModel::class.java)) {
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
        val gameRepository: GameRepositoryV2 = KoinJavaComponent.get(GameRepositoryV2::class.java)
        val loadGamesUseCase = LoadGamesUseCase(gameRepository)
        val addGameUseCase = AddGameUseCase(gameRepository)
        val updateGameUseCase = UpdateGameUseCase(gameRepository)
        val deleteGameUseCase = DeleteGameUseCase(gameRepository)
        val launchGameUseCase = LaunchGameUseCase(GameLaunchManager(appContext))
        val deleteGameFilesUseCase = DeleteGameFilesUseCase(GameDeletionManager(appContext))

        return MainViewModel(
            appContext = appContext.applicationContext,
            loadGamesUseCase = loadGamesUseCase,
            addGameUseCase = addGameUseCase,
            updateGameUseCase = updateGameUseCase,
            deleteGameUseCase = deleteGameUseCase,
            launchGameUseCase = launchGameUseCase,
            deleteGameFilesUseCase = deleteGameFilesUseCase
        ) as T
    }
}
