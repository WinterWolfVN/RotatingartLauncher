package com.app.ralaunch.di

import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatActivity
import com.app.ralaunch.controls.packs.ControlPackManager
import com.app.ralaunch.data.migration.GameDataMigrator
import com.app.ralaunch.data.migration.SettingsDataMigrator
import com.app.ralaunch.manager.*
import com.app.ralaunch.shared.manager.IThemeManager
import com.app.ralaunch.shared.manager.IVibrationManager
import com.app.ralaunch.patch.PatchManager
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.bind
import org.koin.dsl.module
import com.app.ralaunch.shared.domain.repository.GameRepositoryV2 as SharedGameRepositoryV2
import com.app.ralaunch.shared.domain.repository.SettingsRepositoryV2

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
        SettingsDataMigrator(androidContext(), get<SettingsRepositoryV2>())
    }

    single {
        GameDataMigrator(androidContext(), get<SharedGameRepositoryV2>())
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
 * 获取所有 App 模块
 */
fun getAppModules() = listOf(appModule)
