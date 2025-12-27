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

    // Permanent cache for ManagerState (no time limit)
    private var cachedManagerState: ManagerState? = null

    // Permanent cache for ControlConfig objects (no size limit)
    private val configCache = mutableMapOf<String, ControlConfig>()

    // Permanent cache for all configs list
    private var cachedAllConfigs: List<ControlConfig>? = null

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
     * Load ManagerState with permanent caching
     */
    private fun loadManagerState(): ManagerState? {
        // Return cached state if available
        if (cachedManagerState != null) {
            return cachedManagerState
        }

        // Load from disk
        checkAndCreateDirectory()
        val statePath = Path(managerStateFilePath)
        if (!statePath.exists()) {
            return null
        }

        return try {
            val content = statePath.readText()
            val state = json.decodeFromString<ManagerState>(content)
            cachedManagerState = state
            state
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Save ManagerState and update cache
     */
    private fun saveManagerState(state: ManagerState) {
        checkAndCreateDirectory()
        val statePath = Path(managerStateFilePath)
        val content = json.encodeToString(state)
        statePath.writeText(content)

        // Update cache
        cachedManagerState = state
    }

    /**
     * Get the currently selected control config ID
     * Automatically sets to first available config if the selected ID no longer exists
     */
    fun getSelectedConfigId(): String? {
        val selectedId = loadManagerState()?.selectedConfigId

        // Get list of available config IDs
        val configIds = listConfigIds()

        // If no selected ID or selected ID doesn't exist, set to first available
        if (selectedId == null || selectedId !in configIds) {
            val firstConfigId = configIds.firstOrNull()
            setSelectedConfigId(firstConfigId)
            return firstConfigId
        }

        return selectedId
    }

    /**
     * Set the currently selected control config ID
     */
    fun setSelectedConfigId(configId: String?) {
        val state = ManagerState(
            selectedConfigId = configId,
            lastModified = System.currentTimeMillis()
        )
        saveManagerState(state)
    }

    /**
     * Load the currently selected control config
     */
    fun loadCurrentConfig(): ControlConfig? {
        val currentId = getSelectedConfigId() ?: return null
        return loadConfig(currentId)
    }

    fun loadAllConfigs(): List<ControlConfig> {
        // Return cached list if available
        cachedAllConfigs?.let { return it }

        // Load from disk
        checkAndCreateDirectory()
        val dir = Path(configsDirectory)
        val files = dir.listDirectoryEntries("*.json")
        val configs = files
            .map { Pair(it.nameWithoutExtension, ControlConfig.loadFrom(it.toString())) }
            .filter { it.second != null }
            .map {
                it.second!!.id = it.first
                it.second!!
            }

        // Cache the list
        cachedAllConfigs = configs

        // Also cache individual configs
        configs.forEach { config ->
            configCache[config.id] = config
        }

        return configs
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

        // Invalidate cache for this config
        configCache.remove(config.id)

        // Invalidate all configs list cache
        cachedAllConfigs = null
    }

    fun loadConfig(id: String): ControlConfig? {
        // Check cache first
        configCache[id]?.let { return it }

        // Load from disk
        checkAndCreateDirectory()
        val path = "$configsDirectory/$id.json"
        val config = ControlConfig.loadFrom(path)
        config?.id = id

        // Cache the loaded config
        if (config != null) {
            configCache[id] = config
        }

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

                // Invalidate cache for deleted config
                configCache.remove(id)

                // Invalidate all configs list cache
                cachedAllConfigs = null

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