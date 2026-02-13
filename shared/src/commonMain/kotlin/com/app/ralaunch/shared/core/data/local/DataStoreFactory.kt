package com.app.ralaunch.shared.core.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

/**
 * DataStore 工厂接口
 * 由各平台实现
 */
expect class DataStoreFactory {
    fun createPreferencesDataStore(): DataStore<Preferences>
}

/**
 * DataStore 文件名常量
 */
object DataStoreConstants {
    const val SETTINGS_PREFERENCES = "app_settings"
    const val GAME_LIST_FILE = "game_list.json"
    const val CONTROL_LAYOUTS_DIR = "control_layouts"
}
