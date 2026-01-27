package com.app.ralaunch.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.app.ralaunch.R
import com.app.ralaunch.core.GameLauncher
import com.app.ralaunch.patch.PatchManager
import org.koin.java.KoinJavaComponent
import com.app.ralaunch.utils.AppLogger
import java.nio.file.Paths

/**
 * 通用进程启动服务 - 在独立进程中启动 .NET 程序集
 */
class ProcessLauncherService : Service() {

    companion object {
        private const val TAG = "ProcessLauncher"
        private const val CHANNEL_ID = "process_launcher_channel"
        private const val NOTIFICATION_ID = 9528

        const val EXTRA_ASSEMBLY_PATH = "assembly_path"
        const val EXTRA_ARGS = "args"
        const val EXTRA_TITLE = "title"
        const val EXTRA_GAME_ID = "game_id"

        @JvmStatic
        fun launch(assemblyPath: String, args: Array<String>?, title: String?, gameId: String?) {
            val context: Context = KoinJavaComponent.get(Context::class.java)
            val intent = Intent(context, ProcessLauncherService::class.java).apply {
                putExtra(EXTRA_ASSEMBLY_PATH, assemblyPath)
                putExtra(EXTRA_ARGS, args)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_GAME_ID, gameId)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        @JvmStatic
        fun stop(context: Context) {
            context.stopService(Intent(context, ProcessLauncherService::class.java))
        }
    }

    private var running = false
    private var launcherThread: Thread? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            AppLogger.error(TAG, "Intent is null")
            stopSelf()
            return START_NOT_STICKY
        }

        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        startForeground(NOTIFICATION_ID, createNotification("$title 正在启动..."))

        val assemblyPath = intent.getStringExtra(EXTRA_ASSEMBLY_PATH)
        val args = intent.getStringArrayExtra(EXTRA_ARGS)
        val gameId = intent.getStringExtra(EXTRA_GAME_ID)

        if (assemblyPath == null) {
            AppLogger.error(TAG, "Assembly path is null")
            stopSelf()
            return START_NOT_STICKY
        }

        launchAsync(assemblyPath, args, title, gameId)
        return START_STICKY
    }

    private fun launchAsync(assemblyPath: String, args: Array<String>?, title: String, gameId: String?) {
        if (running) return

        launcherThread = Thread({
            try {
                running = true
                updateNotification("$title 正在运行")
                doLaunch(assemblyPath, args, title, gameId)
            } catch (e: Exception) {
                AppLogger.error(TAG, "Launch error: ${e.message}", e)
            } finally {
                running = false
                stopSelf()
            }
        }, "ProcessLauncher").apply { start() }
    }

    private fun doLaunch(assemblyPath: String, args: Array<String>?, title: String, gameId: String?): Int {
        return try {
            val patchManager: PatchManager? = try {
                KoinJavaComponent.getOrNull(PatchManager::class.java)
            } catch (e: Exception) { null }
            val patches = if (gameId != null) patchManager?.getApplicableAndEnabledPatches(gameId, Paths.get(assemblyPath)) ?: emptyList() else emptyList()
            AppLogger.info(TAG, "Game: $gameId, Applicable patches: ${patches.size}")
            GameLauncher.launchDotNetAssembly(assemblyPath, args ?: emptyArray(), patches)
        } catch (e: Exception) {
            AppLogger.error(TAG, "Launch failed: ${e.message}", e)
            AppLogger.error(TAG, "Last Error Msg: ${GameLauncher.getLastErrorMessage()}")
            -1
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        running = false
        launcherThread?.takeIf { it.isAlive }?.interrupt()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "进程启动器", NotificationManager.IMPORTANCE_LOW).apply {
                description = "后台进程运行通知"
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("后台进程")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_ral)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, createNotification(text))
    }
}
