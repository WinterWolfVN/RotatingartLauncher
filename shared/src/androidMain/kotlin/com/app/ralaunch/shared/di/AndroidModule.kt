package com.app.ralaunch.shared.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.app.ralaunch.shared.data.local.AndroidControlLayoutStorage
import com.app.ralaunch.shared.data.local.AndroidGameListStorage
import com.app.ralaunch.shared.data.local.DataStoreFactory
import com.app.ralaunch.shared.data.repository.ControlLayoutRepositoryImpl
import com.app.ralaunch.shared.data.repository.ControlLayoutStorage
import com.app.ralaunch.shared.data.repository.GameListStorage
import com.app.ralaunch.shared.data.service.AndroidControlLayoutService
import com.app.ralaunch.shared.data.service.AndroidGameLaunchService
import com.app.ralaunch.shared.data.service.AndroidPatchService
import com.app.ralaunch.shared.domain.repository.ControlLayoutRepository
import com.app.ralaunch.shared.domain.service.ControlLayoutService
import com.app.ralaunch.shared.domain.service.GameLaunchService
import com.app.ralaunch.shared.domain.service.PatchService
import com.app.ralaunch.shared.ui.screens.settings.AppInfo
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Android 平台特定 Koin 模块
 */
val androidModule = module {

    // ==================== 数据存储 ====================
    
    // DataStore
    single<DataStore<Preferences>> {
        DataStoreFactory(androidContext()).createPreferencesDataStore()
    }

    // GameListStorage
    single<GameListStorage> {
        AndroidGameListStorage(androidContext())
    }

    // ControlLayoutStorage
    single<ControlLayoutStorage> {
        AndroidControlLayoutStorage(androidContext())
    }

    // ==================== Repositories ====================

    single<ControlLayoutRepository> {
        ControlLayoutRepositoryImpl(storage = get())
    }

    // ==================== 服务 ====================
    
    // 游戏启动服务
    single<GameLaunchService> {
        AndroidGameLaunchService(androidContext())
    }
    
    // 控制布局服务
    single<ControlLayoutService> {
        AndroidControlLayoutService(androidContext())
    }
    
    // 补丁服务
    single<PatchService> {
        AndroidPatchService(androidContext())
    }

    // ==================== App Info ====================

    single {
        try {
            val context = androidContext()
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            AppInfo(
                versionName = packageInfo.versionName ?: "Unknown",
                versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode.toLong()
                }
            )
        } catch (e: Exception) {
            AppInfo()
        }
    }
}

/**
 * 获取所有 Android 模块（包含共享模块）
 */
fun getAndroidModules() = getSharedModules() + androidModule
