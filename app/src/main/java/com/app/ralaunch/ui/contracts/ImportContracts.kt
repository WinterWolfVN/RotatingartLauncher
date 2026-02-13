package com.app.ralaunch.ui.contracts

sealed interface ImportUiEvent {
    data object StartImport : ImportUiEvent
    data object CancelImport : ImportUiEvent
}

sealed interface ImportUiEffect

data class ImportUiState(
    val isImporting: Boolean = false,
    val progress: Int = 0,
    val status: String = ""
)
