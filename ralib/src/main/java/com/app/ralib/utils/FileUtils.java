package com.app.ralib.utils;

import android.util.Log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public class FileUtils {
    private static final String TAG = "FileUtils";

    private FileUtils() {}

    /**
     * 递归删除目录及其内容
     * @return 删除是否成功
     */
    public static boolean deleteDirectoryRecursively(Path path) {
        try (var walker = Files.walk(path)) {
            walker
                    .sorted(Comparator.reverseOrder()) // reverse order to delete
                    .forEach(p -> {
                        try { Files.delete(p); } catch (IOException e) {
                            Log.w(TAG, "无法删除文件: " + p, e);
                        }
                    });
        } catch (IOException e) {
            Log.w(TAG, "无法删除目录: " + path, e);
            return false;
        }
        return true;
    }
}
