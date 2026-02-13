package com.app.ralaunch.core.common.util

import android.util.Log
import java.io.File
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.util.Comparator
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 文件操作工具类
 */
object FileUtils {
    private const val TAG = "FileUtils"
    private const val MAX_RETRY_ATTEMPTS = 3
    private const val RETRY_DELAY_MS = 100L

    /**
     * 递归删除目录及其内容
     * @param path 要删除的路径
     * @return 删除是否成功
     */
    @JvmStatic
    fun deleteDirectoryRecursively(path: Path?): Boolean {
        if (path == null) return false
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return true
        if (!Files.isReadable(path)) return false

        val allDeleted = AtomicBoolean(true)

        return try {
            Files.walk(path).use { walker ->
                walker.sorted(Comparator.reverseOrder())
                    .forEach { p ->
                        if (!deletePathWithRetry(p)) {
                            allDeleted.set(false)
                        }
                    }
            }
            allDeleted.get() && !Files.exists(path, LinkOption.NOFOLLOW_LINKS)
        } catch (e: NoSuchFileException) {
            true
        } catch (e: AccessDeniedException) {
            Log.w(TAG, "删除失败（权限）: $path")
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "删除失败（权限）: $path")
            false
        } catch (e: IOException) {
            Log.w(TAG, "删除失败: $path")
            false
        } catch (e: Exception) {
            Log.w(TAG, "删除失败: $path", e)
            false
        }
    }

    /**
     * 带重试机制的路径删除
     */
    private fun deletePathWithRetry(path: Path): Boolean {
        repeat(MAX_RETRY_ATTEMPTS) { attempt ->
            try {
                if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return true
                Files.delete(path)
                return true
            } catch (e: AccessDeniedException) {
                return false
            } catch (e: SecurityException) {
                return false
            } catch (e: IOException) {
                if (attempt < MAX_RETRY_ATTEMPTS - 1) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * (attempt + 1))
                    } catch (ie: InterruptedException) {
                        Thread.currentThread().interrupt()
                        return false
                    }
                }
            } catch (e: Exception) {
                if (attempt < MAX_RETRY_ATTEMPTS - 1) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * (attempt + 1))
                    } catch (ie: InterruptedException) {
                        Thread.currentThread().interrupt()
                        return false
                    }
                }
            }
        }
        return false
    }

    /**
     * 递归删除目录及其内容（File 参数版本）
     * @param directory 要删除的目录
     * @return 删除是否成功
     */
    @JvmStatic
    fun deleteDirectoryRecursively(directory: File?): Boolean {
        if (directory == null) return false
        if (!directory.exists()) return true
        if (!directory.canRead()) return false

        return try {
            val path = directory.toPath().toAbsolutePath().normalize()
            deleteDirectoryRecursively(path)
        } catch (e: Exception) {
            false
        }
    }
}
