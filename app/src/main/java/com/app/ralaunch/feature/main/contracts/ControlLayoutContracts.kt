package com.app.ralaunch.feature.main.contracts

sealed interface ControlLayoutUiEvent {
    data object RefreshRequested : ControlLayoutUiEvent
}

sealed interface ControlLayoutUiEffect

data class ControlLayoutUiState(
    val isLoading: Boolean = false
)
