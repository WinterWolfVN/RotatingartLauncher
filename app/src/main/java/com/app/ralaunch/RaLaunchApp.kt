package com.app.ralaunch

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
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
import java.util.Date
import kotlin.system.exitProcess

class RaLaunchApp : Application(), KoinComponent {

    companion object {
        private const val TAG = "RaLaunchApp"

        @Volatile
        private var instance: RaLaunchApp? = null

        // ... keep track of the last active screen for crash reporting ...
        var lastResumedActivityName: String = "None"

        @JvmStatic
        fun getInstance(): RaLaunchApp = instance
            ?: throw IllegalStateException("Application not initialized")

        @JvmStatic
        fun getAppContext(): Context = getInstance().applicationContext
    }

    // ... Koin injections ...
    private val _vibrationManager: VibrationManager by inject()
    private val _controlPackManager: ControlPackManager by inject()
    private val _patchManager: PatchManager? by inject()

    override fun onCreate() {
        // ... 1. catch fatal Java/Kotlin crashes ...
        setupGlobalCrashCatcher()
        
        // ... 2. catch UI, Lifecycle, and Background framework errors ...
        setupComponentErrorTrackers()

        super.onCreate()
        instance = this

        val startupLogFile = File(filesDir, "startup_log.txt")
        startupLogFile.delete()

        fun writeLog(msg: String) {
            Log.i(TAG, msg)
            try { startupLogFile.appendText("$msg\n") } catch (e: Exception) { }
        }

        fun step(name: String, block: () -> Unit) {
            writeLog("▶ $name...")
            try {
                block()
                writeLog("✅ $name OK")
            } catch (e: Throwable) {
                // ... safely catch initialization errors without killing the app ...
                writeLog("❌ $name FAILED: ${e.javaClass.name} - ${e.message}")
                Log.e(TAG, "Init step failed: $name", e)
            }
        }

        writeLog("=== App Start: Android ${Build.VERSION.SDK_INT} ===")
        
        step("DensityAdapter")  { DensityAdapter.init(this) }
        step("KoinInitializer") { KoinInitializer.init(this) }
        step("Theme")           { applyThemeFromSettings() }
        step("Fishnet")         { initCrashHandler() }
        step("Patches")         { installPatchesInBackground() }
        step("EnvVars")         { setupEnvironmentVariables() }

        writeLog("=== Init Complete ===")
    }

    // ... ULTIMATE COMPONENT ERROR TRACKER ...
    private fun setupComponentErrorTrackers() {
        // ... 1. Track Activity Lifecycle to know WHICH screen caused the crash ...
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {
                lastResumedActivityName = activity.javaClass.simpleName
                Log.d(TAG, "Current Active Screen: $lastResumedActivityName")
            }
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })

        // ... 2. Prevent RxJava UndeliverableExceptions from crashing the app (if RxJava is used) ...
        try {
            val rxPluginsClass = Class.forName("io.reactivex.plugins.RxJavaPlugins")
            val consumerClass = Class.forName("io.reactivex.functions.Consumer")
            // ... using reflection to avoid compile errors if RxJava is not in the project ...
            val method = rxPluginsClass.getMethod("setErrorHandler", consumerClass)
            
            // ... dummy proxy just to swallow or log the unhandled RxJava errors ...
            val handler = java.lang.reflect.Proxy.newProxyInstance(
                consumerClass.classLoader,
                arrayOf(consumerClass)
            ) { _, _, args ->
                val error = args[0] as? Throwable
                Log.e(TAG, "Unhandled RxJava Error Caught: ${error?.message}", error)
                null
            }
            method.invoke(null, handler)
            Log.i(TAG, "RxJava Error Handler attached successfully")
        } catch (e: Exception) {
            // ... RxJava not found or setup failed, safe to ignore ...
        }

        // ... 3. Main Thread (UI) lag/block warning (Simple ANR detection) ...
        Looper.getMainLooper().setMessageLogging { logMessage ->
            if (logMessage.startsWith(">>>>> Dispatching to")) {
                // ... you could start a timer here to measure if frame takes > 16ms or > 5s (ANR) ...
            }
        }
    }

    // ... robust crash catcher method ...
    private fun setupGlobalCrashCatcher() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            try {
                val appVersion = try {
                    packageManager.getPackageInfo(packageName, 0).versionName
                } catch (e: Exception) { "Unknown" }

                // ... build a highly detailed crash report ...
                val crashReport = buildString {
                    appendLine("\n=========================================")
                    appendLine("CRASH TIME: ${Date()}")
                    appendLine("DEVICE: ${Build.MANUFACTURER} ${Build.MODEL} (API ${Build.VERSION.SDK_INT})")
                    appendLine("APP VERSION: $appVersion")
                    appendLine("LAST ACTIVE SCREEN: $lastResumedActivityName") // ... added UI tracker here ...
                    appendLine("THREAD: ${thread.name}")
                    appendLine("ERROR TYPE: ${exception.javaClass.name}")
                    appendLine("MESSAGE: ${exception.message}")
                    appendLine("CAUSE: ${exception.cause?.message}")
                    appendLine("STACKTRACE:")
                    exception.stackTrace.forEach { appendLine("  at $it") }
                    
                    var cause = exception.cause
                    while (cause != null) {
                        appendLine("\nCAUSED BY: ${cause.javaClass.name}: ${cause.message}")
                        cause.stackTrace.forEach { appendLine("  at $it") }
                        cause = cause.cause
                    }
                    appendLine("=========================================\n")
                }

                // ... save to internal storage ...
                val internalLogFile = File(filesDir, "FATAL_CRASH.txt")
                internalLogFile.appendText(crashReport)

                // ... save to external storage ...
                val externalDir = getExternalFilesDir(null)
                if (externalDir != null) {
                    val externalLogFile = File(externalDir, "FATAL_CRASH_ACCESSIBLE.txt")
                    externalLogFile.appendText(crashReport)
                }

                Log.e(TAG, "FATAL CRASH INTERCEPTED:\n$crashReport")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to write crash log", e)
            } finally {
                // ... pass it to Fishnet or System default handler ...
                if (defaultHandler != null) {
                    defaultHandler.uncaughtException(thread, exception)
                } else {
                    android.os.Process.killProcess(android.os.Process.myPid())
                    exitProcess(1)
                }
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

    // ... existing initialization methods ...
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
            Log.e(TAG, "Failed to apply theme", e)
        }
    }

    private fun initCrashHandler() {
        try {
            val logDir = File(filesDir, "crash_logs").apply {
                if (!exists()) mkdirs()
            }
            Fishnet.init(applicationContext, logDir.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Fishnet init failed", e)
        }
    }

    private fun installPatchesInBackground() {
        _patchManager?.let { manager ->
            Thread({
                try {
                    com.app.ralaunch.core.common.util.PatchExtractor.extractPatchesIfNeeded(applicationContext)
                    PatchManager.installBuiltInPatches(manager, false)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to install patches", e)
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
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set env vars", e)
        }
    }

    // ... existing getters ...
    fun getVibrationManager(): VibrationManager = _vibrationManager
    fun getControlPackManager(): ControlPackManager = _controlPackManager
    fun getPatchManager(): PatchManager? = _patchManager
}
