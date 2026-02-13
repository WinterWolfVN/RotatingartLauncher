package com.app.ralaunch.ui.contracts

sealed interface SettingsUiEvent {
    data object RefreshRequested : SettingsUiEvent
}

sealed interface SettingsUiEffect

data class SettingsUiState(
    val isLoading: Boolean = false
)
