package com.app.ralaunch.di

import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatActivity
import com.app.ralaunch.controls.packs.ControlPackManager
import com.app.ralaunch.data.migration.GameDataMigrator
import com.app.ralaunch.data.migration.SettingsDataMigrator
import com.app.ralaunch.data.repository.GameRepository
import com.app.ralaunch.data.repository.LegacyGameRepositoryBridge
import com.app.ralaunch.manager.*
import com.app.ralaunch.shared.manager.IThemeManager
import com.app.ralaunch.shared.manager.IVibrationManager
import com.app.ralaunch.ui.compose.settings.SettingsViewModel as AppSettingsViewModel
import com.app.ralaunch.patch.PatchManager
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module
import com.app.ralaunch.shared.domain.repository.GameRepository as SharedGameRepository
import com.app.ralaunch.shared.domain.repository.SettingsRepository

/**
 * App 模块 Koin 依赖配置
 *
 * 包含 Android 平台特定的依赖
 */
val appModule = module {

    // ==================== Managers ====================

    single {
        VibrationManager(androidContext())
    } bind IVibrationManager::class

    single {
        ControlPackManager(androidContext())
    }

    // PatchManager 初始化（可空，延迟加载）
    single<PatchManager?> {
        try {
            PatchManager(null, false)
        } catch (e: Exception) {
            null
        }
    }

    // ==================== Migration ====================

    single {
        SettingsDataMigrator(androidContext(), get<SettingsRepository>())
    }

    single {
        GameDataMigrator(androidContext(), get<SharedGameRepository>())
    }

    // ==================== Legacy Bridge ====================

    // 旧版 GameRepository 兼容层
    single<GameRepository> {
        LegacyGameRepositoryBridge(get<SharedGameRepository>())
    }

    // ==================== UI Managers (参数化工厂) ====================

    factory<ThemeManager> { (activity: AppCompatActivity) ->
        ThemeManager(activity)
    }

    factory<IThemeManager> { (activity: AppCompatActivity) ->
        ThemeManager(activity)
    }

    factory<PermissionManager> { (activity: ComponentActivity) ->
        PermissionManager(activity)
    }

    factory<GameDeletionManager> { (activity: AppCompatActivity) ->
        GameDeletionManager(activity)
    }
}

/**
 * ViewModel 模块
 */
val viewModelModule = module {
    // App 层特定 ViewModel
    viewModel {
        AppSettingsViewModel(androidContext())
    }
}

/**
 * 获取所有 App 模块
 */
fun getAppModules() = listOf(appModule, viewModelModule)
