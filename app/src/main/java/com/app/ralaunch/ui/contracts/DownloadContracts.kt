package com.app.ralaunch.ui.contracts

sealed interface DownloadUiEvent {
    data object RefreshRequested : DownloadUiEvent
}

sealed interface DownloadUiEffect

data class DownloadUiState(
    val isLoading: Boolean = false
)
