package com.app.ralib.patch;

import android.util.Log;

import androidx.annotation.Nullable;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;

public class PatchManagerConfig {
    private static final String TAG = "PatchManagerConfig";
    public static final String CONFIG_FILE_NAME = "patch_manager.json";

    private static final Gson gson = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .setPrettyPrinting()
            .create();

    @SerializedName("enabled_patches")
    public HashMap<String, ArrayList<String>> enabledPatches = new HashMap<>();

    /**
     * Load config from JSON file
     * @param pathToJson Path to the config JSON file
     * @return PatchManagerConfig instance, or null if loading fails
     */
    public static @Nullable PatchManagerConfig fromJson(Path pathToJson) {
        Log.i(TAG, "加载 " + CONFIG_FILE_NAME + ", pathToJson: " + pathToJson);

        if (!Files.exists(pathToJson) || !Files.isRegularFile(pathToJson)) {
            Log.w(TAG, "路径不存在 " + CONFIG_FILE_NAME + " 文件");
            return null;
        }

        try (var stream = new FileInputStream(pathToJson.toFile());
             var ir = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return gson.fromJson(ir, PatchManagerConfig.class);
        } catch (Exception e) {
            Log.w(TAG, "加载配置文件失败: " + Log.getStackTraceString(e));
        }
        return null;
    }

    /**
     * Save config to JSON file
     * @param pathToJson Path to save the config JSON file
     * @return true if save succeeds, false otherwise
     */
    public boolean saveToJson(Path pathToJson) {
        Log.i(TAG, "保存 " + CONFIG_FILE_NAME + ", pathToJson: " + pathToJson);

        try {
            // Ensure parent directory exists
            if (pathToJson.getParent() != null) {
                Files.createDirectories(pathToJson.getParent());
            }

            try (var stream = new FileOutputStream(pathToJson.toFile());
                 var writer = new OutputStreamWriter(stream, StandardCharsets.UTF_8)) {
                gson.toJson(this, writer);
                writer.flush();
                Log.i(TAG, "配置文件保存成功");
                return true;
            }
        } catch (Exception e) {
            Log.w(TAG, "保存配置文件失败: " + Log.getStackTraceString(e));
        }
        return false;
    }

    /**
     * Get enabled patch IDs for a specific game
     * @param gameAsmPath The game assembly file path
     * @return List of enabled patch IDs, or empty list if none
     */
    public ArrayList<String> getEnabledPatchIds(Path gameAsmPath) {
        return enabledPatches.getOrDefault(gameAsmPath.toAbsolutePath().normalize().toString(), new ArrayList<>());
    }

    /**
     * Set enabled patch IDs for a specific game
     * @param gameAsmPath The game assembly file path
     * @param patchIds List of patch IDs to enable
     */
    public void setEnabledPatchIds(Path gameAsmPath, ArrayList<String> patchIds) {
        enabledPatches.put(gameAsmPath.toAbsolutePath().normalize().toString(), patchIds);
    }

    /**
     * Set whether a patch is enabled for a specific game
     * @param gameAsmPath The game assembly file path
     * @param patchId The patch ID
     * @param enabled true to enable the patch, false to disable it
     */
    public void setPatchEnabled(Path gameAsmPath, String patchId, boolean enabled) {
        if (enabled) {
            ArrayList<String> patches = enabledPatches.computeIfAbsent(gameAsmPath.toAbsolutePath().normalize().toString(), k -> new ArrayList<>());
            if (!patches.contains(patchId)) {
                patches.add(patchId);
            }
        } else {
            ArrayList<String> patches = enabledPatches.get(gameAsmPath.toAbsolutePath().normalize().toString());
            if (patches != null) {
                patches.remove(patchId);
            }
        }
    }

    /**
     * Check if a patch is enabled for a specific game
     * @param gameAsmPath The game assembly file path
     * @param patchId The patch ID
     * @return true if the patch is enabled, false otherwise
     */
    public boolean isPatchEnabled(Path gameAsmPath, String patchId) {
        ArrayList<String> patches = enabledPatches.get(gameAsmPath.toAbsolutePath().normalize().toString());
        return patches != null && patches.contains(patchId);
    }
}

