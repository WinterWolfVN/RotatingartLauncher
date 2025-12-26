package com.app.ralaunch.controls.configs

import com.app.ralaunch.RaLaunchApplication
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.path.*

class ControlConfigManager(val storageDirectory: String? = null) {
    companion object {
        const val DEFAULT_STORAGE_DIRECTORY_NAME = "controls"
        const val CONTROL_CONFIG_DIRECTORY_NAME = "configs"
        const val MANAGER_STATE_FILE_NAME = "control-manager.json"

        private val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }

    /**
     * Manager state data class for storing currently selected config ID
     */
    @Serializable
    data class ManagerState(
        var selectedConfigId: String? = null,
        var lastModified: Long = System.currentTimeMillis()
    )

    private val fullStorageDirectory: String
        get() = storageDirectory ?:
            RaLaunchApplication
                .getAppContext()
                .getExternalFilesDir(null)!!
                .toPath()
                .resolve(DEFAULT_STORAGE_DIRECTORY_NAME)
                .pathString

    private val configsDirectory: String
        get() = Path(fullStorageDirectory).resolve(CONTROL_CONFIG_DIRECTORY_NAME).pathString

    private val managerStateFilePath: String
        get() = Path(fullStorageDirectory).resolve(MANAGER_STATE_FILE_NAME).pathString

    private fun checkAndCreateDirectory() {
        val controlsDir = Path(fullStorageDirectory)
        if (!controlsDir.exists()) {
            controlsDir.createDirectories()
        }

        // Also ensure the configs subdirectory exists
        val configsDir = Path(configsDirectory)
        if (!configsDir.exists()) {
            configsDir.createDirectories()
        }
    }

    /**
     * Get the currently selected control config ID
     */
    fun getSelectedConfigId(): String? {
        checkAndCreateDirectory()
        val statePath = Path(managerStateFilePath)
        if (!statePath.exists()) {
            return null
        }

        return try {
            val content = statePath.readText()
            val state = json.decodeFromString<ManagerState>(content)
            state.selectedConfigId
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Set the currently selected control config ID
     */
    fun setSelectedConfigId(configId: String?) {
        checkAndCreateDirectory()
        val state = ManagerState(
            selectedConfigId = configId,
            lastModified = System.currentTimeMillis()
        )

        val statePath = Path(managerStateFilePath)
        val content = json.encodeToString(state)
        statePath.writeText(content)
    }

    /**
     * Load the currently selected control config
     */
    fun loadCurrentConfig(): ControlConfig? {
        val currentId = getSelectedConfigId() ?: return null
        return loadConfig(currentId)
    }

    fun loadAllConfigs(): List<ControlConfig> {
        checkAndCreateDirectory()
        val dir = Path(configsDirectory)
        val files = dir.listDirectoryEntries("*.json")
        return files
            .map { Pair(it.nameWithoutExtension, ControlConfig.loadFrom(it.toString())) }
            .filter { it.second != null }
            .map {
                it.second!!.id = it.first
                it.second!!
            }
    }

    fun listConfigIds(): List<String> {
        checkAndCreateDirectory()
        val dir = Path(configsDirectory)
        val files = dir.listDirectoryEntries("*.json")
        return files.map { it.nameWithoutExtension }
    }

    fun saveConfig(config: ControlConfig) {
        checkAndCreateDirectory()
        val path = "$configsDirectory/${config.id}.json"
        config.saveTo(path)
    }

    fun loadConfig(id: String): ControlConfig? {
        checkAndCreateDirectory()
        val path = "$configsDirectory/$id.json"
        val config = ControlConfig.loadFrom(path)
        config?.id = id
        return config
    }

    /**
     * Delete a control config by ID
     * @param id The config ID to delete
     * @return true if the file was successfully deleted, false otherwise
     */
    fun deleteConfig(id: String): Boolean {
        checkAndCreateDirectory()
        val path = Path("$configsDirectory/$id.json")

        return try {
            if (path.exists()) {
                path.deleteExisting()

                // If this was the currently selected config, set to the first config, or else clear the selection
                if (getSelectedConfigId() == id) {
                    setSelectedConfigId(listConfigIds().firstOrNull())
                }

                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }
}