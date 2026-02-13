package com.app.ralaunch.ui.contracts

sealed interface ControlLayoutUiEvent {
    data object RefreshRequested : ControlLayoutUiEvent
}

sealed interface ControlLayoutUiEffect

data class ControlLayoutUiState(
    val isLoading: Boolean = false
)
