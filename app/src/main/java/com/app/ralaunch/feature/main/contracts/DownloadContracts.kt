package com.app.ralaunch.feature.main.contracts

sealed interface DownloadUiEvent {
    data object RefreshRequested : DownloadUiEvent
}

sealed interface DownloadUiEffect

data class DownloadUiState(
    val isLoading: Boolean = false
)
