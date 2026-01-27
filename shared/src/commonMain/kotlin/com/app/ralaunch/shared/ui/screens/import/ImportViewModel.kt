package com.app.ralaunch.shared.ui.screens.`import`

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.ralaunch.shared.ui.model.DetectedGame
import com.app.ralaunch.shared.ui.model.ImportMethod
import com.app.ralaunch.shared.ui.model.ImportUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 导入事件 - 跨平台
 */
sealed class ImportEvent {
    data class SelectMethod(val method: ImportMethod) : ImportEvent()
    object SelectFolder : ImportEvent()
    object SelectDefinitionFile : ImportEvent()
    data class UpdatePath(val path: String) : ImportEvent()
    object StartScan : ImportEvent()
    object CancelScan : ImportEvent()
    data class ToggleGameSelection(val game: DetectedGame) : ImportEvent()
    object SelectAllGames : ImportEvent()
    object DeselectAllGames : ImportEvent()
    object ImportSelectedGames : ImportEvent()
    object CreateShortcut : ImportEvent()
    object ClearError : ImportEvent()
    object GoBack : ImportEvent()
}

/**
 * 导入副作用 - 跨平台
 */
sealed class ImportEffect {
    object OpenFolderPicker : ImportEffect()
    object OpenFilePicker : ImportEffect()
    data class ShowToast(val message: String) : ImportEffect()
    data class ImportComplete(val count: Int) : ImportEffect()
    object NavigateBack : ImportEffect()
    data class ShowError(val message: String) : ImportEffect()
}

/**
 * 导入 ViewModel - 跨平台
 */
class ImportViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    private val _effect = MutableStateFlow<ImportEffect?>(null)
    val effect: StateFlow<ImportEffect?> = _effect.asStateFlow()

    /**
     * 处理事件
     */
    fun onEvent(event: ImportEvent) {
        when (event) {
            is ImportEvent.SelectMethod -> selectMethod(event.method)
            is ImportEvent.SelectFolder -> sendEffect(ImportEffect.OpenFolderPicker)
            is ImportEvent.SelectDefinitionFile -> sendEffect(ImportEffect.OpenFilePicker)
            is ImportEvent.UpdatePath -> updatePath(event.path)
            is ImportEvent.StartScan -> startScan()
            is ImportEvent.CancelScan -> cancelScan()
            is ImportEvent.ToggleGameSelection -> toggleGameSelection(event.game)
            is ImportEvent.SelectAllGames -> selectAllGames()
            is ImportEvent.DeselectAllGames -> deselectAllGames()
            is ImportEvent.ImportSelectedGames -> importSelectedGames()
            is ImportEvent.CreateShortcut -> createShortcut()
            is ImportEvent.ClearError -> clearError()
            is ImportEvent.GoBack -> sendEffect(ImportEffect.NavigateBack)
        }
    }

    fun clearEffect() {
        _effect.value = null
    }

    private fun sendEffect(effect: ImportEffect) {
        _effect.value = effect
    }

    // ==================== 方法选择 ====================

    private fun selectMethod(method: ImportMethod) {
        _uiState.update {
            it.copy(
                currentMethod = method,
                detectedGames = emptyList(),
                currentPath = "",
                errorMessage = null
            )
        }
    }

    // ==================== 路径管理 ====================

    private fun updatePath(path: String) {
        _uiState.update { it.copy(currentPath = path) }
    }

    // ==================== 扫描控制 ====================

    private fun startScan() {
        val path = _uiState.value.currentPath
        if (path.isBlank()) {
            _uiState.update { it.copy(errorMessage = "请先选择文件夹路径") }
            return
        }

        _uiState.update {
            it.copy(
                isScanning = true,
                scanProgress = 0f,
                detectedGames = emptyList(),
                errorMessage = null
            )
        }

        // 扫描逻辑由平台实现
        viewModelScope.launch {
            // 模拟扫描进度
            simulateScanProgress()
        }
    }

    private suspend fun simulateScanProgress() {
        // 这里应该调用平台特定的扫描服务
        // 暂时模拟
        for (i in 1..10) {
            kotlinx.coroutines.delay(100)
            _uiState.update { it.copy(scanProgress = i / 10f) }
        }
        _uiState.update { it.copy(isScanning = false, scanProgress = 1f) }
    }

    private fun cancelScan() {
        _uiState.update {
            it.copy(
                isScanning = false,
                scanProgress = 0f
            )
        }
    }

    // ==================== 游戏选择 ====================

    private fun toggleGameSelection(game: DetectedGame) {
        _uiState.update { state ->
            state.copy(
                detectedGames = state.detectedGames.map {
                    if (it.path == game.path) {
                        it.copy(isSelected = !it.isSelected)
                    } else {
                        it
                    }
                }
            )
        }
    }

    private fun selectAllGames() {
        _uiState.update { state ->
            state.copy(
                detectedGames = state.detectedGames.map {
                    it.copy(isSelected = true)
                }
            )
        }
    }

    private fun deselectAllGames() {
        _uiState.update { state ->
            state.copy(
                detectedGames = state.detectedGames.map {
                    it.copy(isSelected = false)
                }
            )
        }
    }

    // ==================== 导入操作 ====================

    private fun importSelectedGames() {
        val selectedGames = _uiState.value.detectedGames.filter { it.isSelected }
        if (selectedGames.isEmpty()) {
            sendEffect(ImportEffect.ShowToast("请先选择要导入的游戏"))
            return
        }

        viewModelScope.launch {
            // 导入逻辑由平台实现
            sendEffect(ImportEffect.ImportComplete(selectedGames.size))
        }
    }

    private fun createShortcut() {
        val path = _uiState.value.currentPath
        if (path.isBlank()) {
            sendEffect(ImportEffect.ShowToast("请先选择应用"))
            return
        }

        // 快捷方式创建逻辑由平台实现
        sendEffect(ImportEffect.ShowToast("快捷方式已创建"))
    }

    // ==================== 错误处理 ====================

    private fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    // ==================== 扩展点（供平台实现调用） ====================

    /**
     * 更新检测到的游戏列表（供平台层调用）
     */
    fun updateDetectedGames(games: List<DetectedGame>) {
        _uiState.update {
            it.copy(
                detectedGames = games,
                isScanning = false,
                scanProgress = 1f
            )
        }
    }

    /**
     * 设置扫描进度（供平台层调用）
     */
    fun setScanProgress(progress: Float) {
        _uiState.update { it.copy(scanProgress = progress) }
    }

    /**
     * 设置错误信息（供平台层调用）
     */
    fun setError(message: String) {
        _uiState.update {
            it.copy(
                errorMessage = message,
                isScanning = false
            )
        }
    }
}
