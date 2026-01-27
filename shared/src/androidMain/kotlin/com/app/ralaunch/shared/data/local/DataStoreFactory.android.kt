package com.app.ralaunch.shared.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile

/**
 * Android 平台 DataStore 工厂实现
 */
actual class DataStoreFactory(private val context: Context) {

    actual fun createPreferencesDataStore(): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create {
            context.preferencesDataStoreFile(DataStoreConstants.SETTINGS_PREFERENCES)
        }
    }
}
