package com.app.ralaunch.data;

import android.content.Context;
import com.app.ralaunch.model.GameItem;
import com.app.ralaunch.utils.AppLogger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * 游戏数据管理器
 * 
 * 管理用户添加的游戏列表，提供：
 * - 从外部存储文件加载和保存游戏列表
 * - 添加、删除和更新游戏项
 * - 路径有效性验证
 * 
 * 游戏数据持久化存储，应用重启后保留
 */
public class GameDataManager {
    private static final String TAG = "GameDataManager";
    private static final String GAMES_DIR = "games";
    private static final String GAME_LIST_FILE = "game_list.json";

    private Context context;

    public GameDataManager(Context context) {
        this.context = context;
    }

    private File getGameListFile() {
        File gamesDir = new File(context.getExternalFilesDir(null), GAMES_DIR);
        if (!gamesDir.exists()) {
            gamesDir.mkdirs();
        }
        return new File(gamesDir, GAME_LIST_FILE);
    }

    // 保存和加载游戏列表
    public void saveGameList(List<GameItem> gameList) {
        try {
            File file = getGameListFile();
            if (!file.exists()) {
                file.createNewFile();
            }
            String gameListJson = new GsonBuilder().setPrettyPrinting().create().toJson(gameList);
            Files.write(file.toPath(), gameListJson.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            AppLogger.error(TAG, "保存游戏列表时发生错误: " + e.getMessage());
        }
    }

    public List<GameItem> loadGameList() {
        File file = getGameListFile();
        if (!file.exists()) return new ArrayList<>();

        try {
            String json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            Type listType = new TypeToken<ArrayList<GameItem>>(){}.getType();
            List<GameItem> result = new Gson().fromJson(json, listType);
            return result != null ? result : new ArrayList<>();
        } catch (IOException e) {
            AppLogger.error(TAG, "加载游戏列表时发生错误: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public void addGame(GameItem game) {
        List<GameItem> gameList = loadGameList();
        gameList.add(0, game);
        saveGameList(gameList);
    }

    public void removeGame(int position) {
        List<GameItem> gameList = loadGameList();
        if (position >= 0 && position < gameList.size()) {
            gameList.remove(position);
            saveGameList(gameList);
        }
    }

}