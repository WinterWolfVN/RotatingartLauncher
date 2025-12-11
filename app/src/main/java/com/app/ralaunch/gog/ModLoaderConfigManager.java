package com.app.ralaunch.gog;

import android.content.Context;
import com.app.ralaunch.utils.AppLogger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * GOG ModLoader 配置管理器
 * 从 assets/gog_modloader_rules.json 读取规则，便于按游戏ID维护 ModLoader 下载信息
 */
public class ModLoaderConfigManager {
    private static final String TAG = "ModLoaderConfigManager";
    private static final String CONFIG_NAME = "gog_modloader_rules.json";

    private final List<ModLoaderRule> rules = new ArrayList<>();

    public ModLoaderConfigManager(Context context) {
        loadConfig(context);
    }

    public ModLoaderRule getRule(long gameId) {
        for (ModLoaderRule rule : rules) {
            if (rule.gameId == gameId) {
                return rule;
            }
        }
        return null;
    }

    private void loadConfig(Context context) {
        try (InputStream is = context.getAssets().open(CONFIG_NAME);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }

            JSONArray array = new JSONArray(sb.toString());
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                ModLoaderRule rule = new ModLoaderRule();
                rule.gameId = obj.optLong("gameId");
                rule.name = obj.optString("name", "");
                
                // 解析版本列表
                JSONArray versionsArray = obj.optJSONArray("versions");
                if (versionsArray != null) {
                    for (int j = 0; j < versionsArray.length(); j++) {
                        JSONObject versionObj = versionsArray.getJSONObject(j);
                        ModLoaderVersion version = new ModLoaderVersion();
                        version.version = versionObj.optString("version", "");
                        version.url = versionObj.optString("url", "");
                        version.fileName = versionObj.optString("fileName", "modloader.zip");
                        version.stable = versionObj.optBoolean("stable", false);
                        rule.versions.add(version);
                    }
                }
                
                // 兼容旧格式（单个版本）
                if (obj.has("modLoaderUrl")) {
                    ModLoaderVersion version = new ModLoaderVersion();
                    version.version = "default";
                    version.url = obj.optString("modLoaderUrl", "");
                    version.fileName = obj.optString("fileName", "modloader.zip");
                    version.stable = true;
                    rule.versions.add(version);
                }
                
                rules.add(rule);
            }
            AppLogger.info(TAG, "已加载 ModLoader 规则 " + rules.size() + " 条");
        } catch (Exception e) {
            AppLogger.error(TAG, "读取 ModLoader 配置失败: " + e.getMessage(), e);
        }
    }

    public static class ModLoaderRule {
        public long gameId;
        public String name;
        public List<ModLoaderVersion> versions = new ArrayList<>();
    }
    
    public static class ModLoaderVersion {
        public String version;
        public String url;
        public String fileName;
        public boolean stable;
        
        @Override
        public String toString() {
            return version + (stable ? " (稳定版)" : "");
        }
    }
}

