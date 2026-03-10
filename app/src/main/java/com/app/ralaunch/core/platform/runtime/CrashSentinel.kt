package com.app.ralaunch.core.platform.runtime

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.Date
import kotlin.system.exitProcess

/**
 * The Ultimate Crash Sentinel 🗿🧬
 * 
 * A unified crash detection system that catches both high-level Java/Kotlin app crashes
 * AND deep-level native C++/OOM game crashes without bloating the main Application class.
 */
object CrashSentinel {

    private const val TAG = "CrashSentinel"
    private var isArmed = false

    // ===================================================================
    // ... ARM THE DEFENSES (Call this once when app starts) ...
    // ===================================================================
    fun armDefenses(context: Context) {
        if (isArmed) return
        isArmed = true

        val crashDir = File(context.getExternalFilesDir(null), "CrashReports")
        if (!crashDir.exists()) crashDir.mkdirs()

        Log.i(TAG, "🛡️ Arming the Ultimate Crash Sentinel...")

        // ... 1. Setup Java/Kotlin Exception Catcher (For App Crashes) ...
        setupJavaCrashCatcher(context, crashDir)

        // ... 2. Setup Native/Logcat Reader (For Game/C++/OOM Crashes) ...
        setupNativeCrashCatcher(crashDir)
    }

    // ===================================================================
    // ... LAYER 1: APP CRASHES (Kotlin/Java) ...
    // ===================================================================
    private fun setupJavaCrashCatcher(context: Context, crashDir: File) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            try {
                val appVersion = try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                } catch (e: Exception) { "Unknown" }

                val reportFile = File(crashDir, "APP_CRASH_${System.currentTimeMillis()}.txt")
                
                val crashReport = buildString {
                    appendLine("=========================================")
                    appendLine("🔥 JAVA/KOTLIN APP CRASH REPORT")
                    appendLine("CRASH TIME: ${Date()}")
                    appendLine("DEVICE: ${Build.MANUFACTURER} ${Build.MODEL} (API ${Build.VERSION.SDK_INT})")
                    appendLine("APP VERSION: $appVersion")
                    appendLine("THREAD: ${thread.name}")
                    appendLine("ERROR TYPE: ${exception.javaClass.name}")
                    appendLine("MESSAGE: ${exception.message}")
                    appendLine("=========================================")
                    appendLine("STACKTRACE:")
                    exception.stackTrace.forEach { appendLine("  at $it") }
                    
                    var cause = exception.cause
                    while (cause != null) {
                        appendLine("\nCAUSED BY: ${cause.javaClass.name}: ${cause.message}")
                        cause.stackTrace.forEach { appendLine("  at $it") }
                        cause = cause.cause
                    }
                }

                reportFile.writeText(crashReport)
                Log.e(TAG, "FATAL APP CRASH SAVED TO: ${reportFile.absolutePath}")

            } catch (e: Exception) {
                // Ignore failure during crash handling
            } finally {
                // ... Pass the crash to Android OS to close the app properly ...
                if (defaultHandler != null) {
                    defaultHandler.uncaughtException(thread, exception)
                } else {
                    android.os.Process.killProcess(android.os.Process.myPid())
                    exitProcess(1)
                }
            }
        }
    }

    // ===================================================================
    // ... LAYER 2: GAME CRASHES (Native C++ / SDL / OOM) ...
    // ===================================================================
    private fun setupNativeCrashCatcher(crashDir: File) {
        Thread {
            try {
                // ... Clear old logcat to prevent reading stale crashes ...
                Runtime.getRuntime().exec("logcat -c").waitFor()

                // ... Start reading logcat continuously in the background ...
                val process = Runtime.getRuntime().exec("logcat -v time")
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                
                val nativeReportFile = File(crashDir, "GAME_NATIVE_CRASH_LOG.txt")
                
                // ... Recreate the file for the new session ...
                nativeReportFile.writeText("=== GAME BACKGROUND LOGGING STARTED AT ${Date()} ===\n\n")

                while (true) {
                    val line = reader.readLine() ?: break
                    
                    // ... Filter only fatal signals, memory issues, or our app's specific logs ...
                    if (line.contains("F libc") ||      // Native C++ Segfaults
                        line.contains("DEBUG") ||       // Android Tombstone/Crash dumper
                        line.contains("Fatal") ||       // General fatal errors
                        line.contains("OOM") ||         // Out of memory
                        line.contains("LowMemory") ||   // OS killing processes
                        line.contains("SDL") ||         // SDL engine errors
                        line.contains("mono") ||        // .NET runtime errors
                        line.contains("ralaunch")) {    // Our app's logs
                        
                        nativeReportFile.appendText("$line\n")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Native Crash Catcher failed: ${e.message}")
            }
        }.start()
    }
}
