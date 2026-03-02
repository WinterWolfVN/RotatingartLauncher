package com.app.ralaunch.feature.patch.data

import android.util.Log
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

class PatchManagerConfig {

    @SerializedName("enabled_patches")
    var enabledPatches: HashMap<String, ArrayList<String>> = hashMapOf()

    @SerializedName("disabled_patches")
    var disabledPatches: HashMap<String, ArrayList<String>> = hashMapOf()

    private fun getSafeKey(file: File): String {
        return try {
            file.canonicalPath
        } catch (e: Exception) {
            file.absolutePath
        }
    }

    fun getEnabledPatchIds(gameAsmFile: File): ArrayList<String> {
        return enabledPatches.getOrDefault(getSafeKey(gameAsmFile), arrayListOf())
    }

    fun setEnabledPatchIds(gameAsmFile: File, patchIds: ArrayList<String>) {
        enabledPatches[getSafeKey(gameAsmFile)] = patchIds
    }

    fun setPatchEnabled(gameAsmFile: File, patchId: String, enabled: Boolean) {
        val key = getSafeKey(gameAsmFile)
        if (enabled) {
            disabledPatches[key]?.remove(patchId)
            val patches = enabledPatches.getOrPut(key) { arrayListOf() }
            if (!patches.contains(patchId)) {
                patches.add(patchId)
            }
        } else {
            val disabled = disabledPatches.getOrPut(key) { arrayListOf() }
            if (!disabled.contains(patchId)) {
                disabled.add(patchId)
            }
            enabledPatches[key]?.remove(patchId)
        }
    }

    fun isPatchEnabled(gameAsmFile: File, patchId: String): Boolean {
        val key = getSafeKey(gameAsmFile)
        val disabled = disabledPatches[key]
        if (disabled != null && disabled.contains(patchId)) {
            return false
        }
        return true
    }

    fun saveToJson(jsonFile: File): Boolean {
        Log.i(TAG, "Save $CONFIG_FILE_NAME, path: ${jsonFile.absolutePath}")
        return try {
            jsonFile.parentFile?.mkdirs()

            FileOutputStream(jsonFile).use { stream ->
                OutputStreamWriter(stream, StandardCharsets.UTF_8).use { writer ->
                    gson.toJson(this, writer)
                    writer.flush()
                    Log.i(TAG, "Configuration file saved successfully")
                    true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Save configuration file failed: ${e.message}")
            false
        }
    }

    companion object {
        private const val TAG = "PatchManagerConfig"
        const val CONFIG_FILE_NAME = "patch_manager.json"

        private val gson = GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .setPrettyPrinting()
            .create()

        @JvmStatic
        fun fromJson(jsonFile: File): PatchManagerConfig? {
            Log.i(TAG, "Load $CONFIG_FILE_NAME, path: ${jsonFile.absolutePath}")

            if (!jsonFile.exists() || !jsonFile.isFile) {
                Log.w(TAG, "File $CONFIG_FILE_NAME does not exist")
                return null
            }

            return try {
                FileInputStream(jsonFile).use { stream ->
                    InputStreamReader(stream, StandardCharsets.UTF_8).use { reader ->
                        gson.fromJson(reader, PatchManagerConfig::class.java)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load configuration file: ${e.message}")
                null
            }
        }
    }
}
