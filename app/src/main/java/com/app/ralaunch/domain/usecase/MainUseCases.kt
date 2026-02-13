package com.app.ralaunch.domain.usecase

import com.app.ralaunch.manager.GameDeletionManager
import com.app.ralaunch.manager.GameLaunchManager
import com.app.ralaunch.shared.domain.model.GameItem
import com.app.ralaunch.shared.domain.repository.GameRepositoryV2

class LoadGamesUseCase(
    private val gameRepository: GameRepositoryV2
) {
    suspend operator fun invoke(): List<GameItem> = gameRepository.games.value
}

class AddGameUseCase(
    private val gameRepository: GameRepositoryV2
) {
    suspend operator fun invoke(game: GameItem, position: Int = 0) {
        gameRepository.upsert(game, position)
    }
}

class UpdateGameUseCase(
    private val gameRepository: GameRepositoryV2
) {
    suspend operator fun invoke(game: GameItem) {
        val index = gameRepository.games.value.indexOfFirst { it.id == game.id }
        if (index >= 0) {
            gameRepository.upsert(game, index)
        }
    }
}

class DeleteGameUseCase(
    private val gameRepository: GameRepositoryV2
) {
    suspend operator fun invoke(gameId: String) {
        gameRepository.removeById(gameId)
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
