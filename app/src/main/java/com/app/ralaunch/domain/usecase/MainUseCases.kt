package com.app.ralaunch.domain.usecase

import com.app.ralaunch.manager.GameDeletionManager
import com.app.ralaunch.manager.GameLaunchManager
import com.app.ralaunch.shared.domain.model.GameItem
import com.app.ralaunch.shared.domain.repository.GameRepository

class LoadGamesUseCase(
    private val gameRepository: GameRepository
) {
    suspend operator fun invoke(): List<GameItem> = gameRepository.getGameList()
}

class AddGameUseCase(
    private val gameRepository: GameRepository
) {
    suspend operator fun invoke(game: GameItem, position: Int = 0) {
        gameRepository.addGame(game, position)
    }
}

class UpdateGameUseCase(
    private val gameRepository: GameRepository
) {
    suspend operator fun invoke(game: GameItem) {
        gameRepository.updateGame(game)
    }
}

class DeleteGameUseCase(
    private val gameRepository: GameRepository
) {
    suspend operator fun invoke(gameId: String) {
        gameRepository.deleteGame(gameId)
    }
}

class LaunchGameUseCase(
    private val gameLaunchManager: GameLaunchManager
) {
    operator fun invoke(game: GameItem): Boolean = gameLaunchManager.launchGame(game)
}

class DeleteGameFilesUseCase(
    private val gameDeletionManager: GameDeletionManager
) {
    operator fun invoke(game: GameItem): Boolean = gameDeletionManager.deleteGameFiles(game)
}
