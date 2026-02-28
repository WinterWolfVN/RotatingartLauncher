package com.app.ralaunch

import android.app.Application
import android.app.AlertDialog
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.system.Os
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import com.app.ralaunch.feature.controls.packs.ControlPackManager
import com.app.ralaunch.core.common.SettingsAccess
import com.app.ralaunch.core.di.KoinInitializer
import com.app.ralaunch.core.common.VibrationManager
import com.app.ralaunch.core.common.util.DensityAdapter
import com.app.ralaunch.core.common.util.LocaleManager
import com.app.ralaunch.feature.patch.data.PatchManager
import com.kyant.fishnet.Fishnet
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinComponent
import java.io.File

class RaLaunchApp : Application(), KoinComponent {

    companion object {
        private const val TAG = "RaLaunchApp"

        @Volatile
        private var instance: RaLaunchApp? = null

        @JvmStatic
        fun getInstance(): RaLaunchApp = instance
            ?: throw IllegalStateException("Application not initialized")

        @JvmStatic
        fun getAppContext(): Context = getInstance().applicationContext
    }

    private val _vibrationManager: VibrationManager by inject()
    private val _controlPackManager: ControlPackManager by inject()
    private val _patchManager: PatchManager? by inject()

    override fun onCreate() {
        super.onCreate()
        instance = this

        // === THÊM: Log thông tin thiết bị để debug ===
        Log.i(TAG, "========================================")
        Log.i(TAG, "App starting on Android ${Build.VERSION.SDK_INT}")
        Log.i(TAG, "Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        Log.i(TAG, "========================================")

        // 1. DensityAdapter
        try {
            Log.i(TAG, "Step 1: DensityAdapter.init...")
            DensityAdapter.init(this)
            Log.i(TAG, "Step 1: OK")
        } catch (e: Exception) {
            Log.e(TAG, "Step 1 FAILED: ${e.javaClass.simpleName}: ${e.message}", e)
        }

        // 2. Koin DI
        try {
            Log.i(TAG, "Step 2: KoinInitializer.init...")
            KoinInitializer.init(this)
            Log.i(TAG, "Step 2: OK")
        } catch (e: Exception) {
            Log.e(TAG, "Step 2 FAILED: ${e.javaClass.simpleName}: ${e.message}", e)
            showFatalError("Koin DI Failed", e)
            return
        }

        // 3. Theme
        try {
            Log.i(TAG, "Step 3: applyThemeFromSettings...")
            applyThemeFromSettings()
            Log.i(TAG, "Step 3: OK")
        } catch (e: Exception) {
            Log.e(TAG, "Step 3 FAILED: ${e.javaClass.simpleName}: ${e.message}", e)
        }

        // 4. Crash Handler
        try {
            Log.i(TAG, "Step 4: Fishnet.init...")
            val logDir = File(filesDir, "crash_logs").apply {
                if (!exists()) mkdirs()
            }
            Fishnet.init(applicationContext, logDir.absolutePath)
            Log.i(TAG, "Step 4: OK")
        } catch (e: Exception) {
            // Fishnet không quan trọng, bỏ qua nếu lỗi
            Log.w(TAG, "Step 4 FAILED (non-fatal): ${e.javaClass.simpleName}: ${e.message}")
        }

        // 5. Patches
        try {
            Log.i(TAG, "Step 5: installPatchesInBackground...")
            installPatchesInBackground()
            Log.i(TAG, "Step 5: OK")
        } catch (e: Exception) {
            Log.e(TAG, "Step 5 FAILED: ${e.javaClass.simpleName}: ${e.message}", e)
        }

        // 6. Environment Variables
        try {
            Log.i(TAG, "Step 6: setupEnvironmentVariables...")
            setupEnvironmentVariables()
            Log.i(TAG, "Step 6: OK")
        } catch (e: Exception) {
            Log.e(TAG, "Step 6 FAILED: ${e.javaClass.simpleName}: ${e.message}", e)
        }

        Log.i(TAG, "App init complete!")
    }

    /**
     * Hiện dialog lỗi nghiêm trọng thay vì crash âm thầm
     */
    private fun showFatalError(title: String, e: Exception) {
        Handler(Looper.getMainLooper()).post {
            try {
                AlertDialog.Builder(this)
                    .setTitle("❌ $title")
                    .setMessage(
                        "Android API: ${Build.VERSION.SDK_INT}\n" +
                        "Device: ${Build.MANUFACTURER} ${Build.MODEL}\n\n" +
                        "Error: ${e.javaClass.simpleName}\n" +
                        "${e.message}\n\n" +
                        "Cause: ${e.cause?.message ?: "Unknown"}"
                    )
                    .setPositiveButton("OK") { _, _ -> }
                    .show()
            } catch (dialogError: Exception) {
                Log.e(TAG, "Cannot show dialog: ${dialogError.message}")
            }
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleManager.applyLanguage(base))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        LocaleManager.applyLanguage(this)
    }

    private fun applyThemeFromSettings() {
        try {
            val nightMode = when (SettingsAccess.themeMode) {
                0 -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                1 -> AppCompatDelegate.MODE_NIGHT_YES
                2 -> AppCompatDelegate.MODE_NIGHT_NO
                else -> AppCompatDelegate.MODE_NIGHT_NO
            }
            AppCompatDelegate.setDefaultNightMode(nightMode)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply theme: ${e.message}")
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    private fun initCrashHandler() {
        val logDir = File(filesDir, "crash_logs").apply {
            if (!exists()) mkdirs()
        }
        Fishnet.init(applicationContext, logDir.absolutePath)
    }

    private fun installPatchesInBackground() {
        _patchManager?.let { manager ->
            Thread({
                try {
                    com.app.ralaunch.core.common.util.PatchExtractor.extractPatchesIfNeeded(applicationContext)
                    PatchManager.installBuiltInPatches(manager, false)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to install patches: ${e.message}")
                }
            }, "PatchInstaller").start()
        }
    }

    private fun setupEnvironmentVariables() {
        try {
            Os.setenv("PACKAGE_NAME", packageName, true)
            val externalStorage = android.os.Environment.getExternalStorageDirectory()
            externalStorage?.let {
                Os.setenv("EXTERNAL_STORAGE_DIRECTORY", it.absolutePath, true)
                Log.d(TAG, "EXTERNAL_STORAGE_DIRECTORY: ${it.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set environment variables: ${e.message}")
        }
    }

    fun getVibrationManager(): VibrationManager = _vibrationManager
    fun getControlPackManager(): ControlPackManager = _controlPackManager
    fun getPatchManager(): PatchManager? = _patchManager
}
