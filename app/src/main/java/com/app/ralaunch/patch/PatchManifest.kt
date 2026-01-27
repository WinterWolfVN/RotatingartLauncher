package com.app.ralaunch.patch

import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

/**
 * 补丁清单
 */
data class PatchManifest(
    @SerializedName("id")
    var id: String = "",

    @SerializedName("name")
    var name: String = "",

    @SerializedName("description")
    var description: String = "",

    @SerializedName("version")
    var version: String = "",

    @SerializedName("author")
    var author: String = "",

    @SerializedName("targetGames")
    var targetGames: List<String>? = null,

    @SerializedName("dllFileName")
    var dllFileName: String = "",

    @SerializedName("entryPoint")
    var entryPoint: EntryPoint? = null,

    @SerializedName("priority")
    var priority: Int = 0,

    @SerializedName("enabled")
    var enabled: Boolean = true,

    @SerializedName("dependencies")
    var dependencies: Dependencies? = null
) {
    /**
     * 补丁依赖配置
     */
    data class Dependencies(
        /**
         * 补丁特定的库文件列表（相对于补丁目录）
         */
        @SerializedName("libs")
        var libs: List<String>? = null
    )

    // 为了向后兼容，entryAssemblyFile 指向 dllFileName
    val entryAssemblyFile: String
        get() = if (!dllFileName.isNullOrEmpty()) dllFileName else ""

    data class EntryPoint(
        @SerializedName("typeName")
        var typeName: String = "",

        @SerializedName("methodName")
        var methodName: String = ""
    )

    companion object {
        private const val TAG = "PatchManifest"
        const val MANIFEST_FILE_NAME = "patch.json"

        @JvmStatic
        val gson: Gson = GsonBuilder()
            .setPrettyPrinting()
            .create()

        @JvmStatic
        fun fromZip(pathToZip: Path): PatchManifest? {
            Log.i(TAG, "加载 Patch 压缩包, pathToZip: $pathToZip")
            return fromZip(pathToZip.toFile())
        }

        @JvmStatic
        fun fromZip(file: File): PatchManifest? {
            Log.i(TAG, "加载 Patch 压缩包, file: ${file.absolutePath}")
            return try {
                ZipFile(file).use { zip ->
                    val manifestEntry = zip.getEntry(MANIFEST_FILE_NAME)
                    if (manifestEntry == null) {
                        Log.w(TAG, "未在压缩包中找到 $MANIFEST_FILE_NAME")
                        return null
                    }
                    zip.getInputStream(manifestEntry).use { stream ->
                        InputStreamReader(stream, StandardCharsets.UTF_8).use { reader ->
                            gson.fromJson(reader, PatchManifest::class.java)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, Log.getStackTraceString(e))
                null
            }
        }

        @JvmStatic
        fun fromJson(pathToJson: Path): PatchManifest? {
            Log.i(TAG, "加载 $MANIFEST_FILE_NAME, pathToJson: $pathToJson")

            if (!Files.exists(pathToJson) || !Files.isRegularFile(pathToJson)) {
                Log.w(TAG, "路径不存在 $MANIFEST_FILE_NAME 文件")
                return null
            }

            return try {
                FileInputStream(pathToJson.toFile()).use { stream ->
                    InputStreamReader(stream, StandardCharsets.UTF_8).use { reader ->
                        gson.fromJson(reader, PatchManifest::class.java)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, Log.getStackTraceString(e))
                null
            }
        }
    }
}
