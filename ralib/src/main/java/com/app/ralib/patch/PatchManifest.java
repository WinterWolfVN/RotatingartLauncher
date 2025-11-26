package com.app.ralib.patch;

import android.util.Log;

import androidx.annotation.Nullable;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;

public class PatchManifest {
    private static final String TAG = "PatchManifest";
    public static final String MANIFEST_FILE_NAME = "patch.json";

    public static Gson gson = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .setPrettyPrinting()
            .create();

    @SerializedName("id")
    public String id = "";

    @SerializedName("name")
    public String name = "";

    @SerializedName("description")
    public String description = "";

    @SerializedName("version")
    public String version = "";

    @SerializedName("author")
    public String author = "";

    @SerializedName("target_games")
    public List<String> targetGames = null;

    @SerializedName("entry_assembly_file")
    public String entryAssemblyFile = "";

    @SerializedName("priority")
    public int priority = 0;

    public static @Nullable PatchManifest fromZip(Path pathToZip) {
        Log.i(TAG, "加载 Patch 压缩包, pathToZip: " + pathToZip);

        return PatchManifest.fromZip(pathToZip.toFile());
    }

    public static @Nullable PatchManifest fromZip(File file) {
        Log.i(TAG, "加载 Patch 压缩包, file: " + file.getAbsolutePath());
        try (ZipFile zip = new ZipFile(file)) {
            @Nullable var manifestEntry = zip.getEntry(MANIFEST_FILE_NAME);
            if (manifestEntry == null) {
                Log.w(TAG, "未在压缩包中找到 " + MANIFEST_FILE_NAME);
                return null;
            }
            try (var stream = zip.getInputStream(manifestEntry);
                 var ir = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return PatchManifest.gson.fromJson(ir, PatchManifest.class);
            }
        } catch (Exception e) {
            Log.w(TAG, Log.getStackTraceString(e));
        }
        return null;
    }

    public static @Nullable PatchManifest fromJson(Path pathToJson) {
        Log.i(TAG, "加载 " + MANIFEST_FILE_NAME + ", pathToJson: " + pathToJson);

        if (!Files.exists(pathToJson) || !Files.isRegularFile(pathToJson)) {
            Log.w(TAG, "路径不存在 " + MANIFEST_FILE_NAME + " 文件");
            return null;
        }

        try (var stream = new FileInputStream(pathToJson.toFile());
             var ir = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return PatchManifest.gson.fromJson(ir, PatchManifest.class);
        } catch (Exception e) {
            Log.w(TAG, Log.getStackTraceString(e));
        }
        return null;
    }
}