package com.app.ralaunch.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;

/**
 * UI工具类
 * 
 * 提供常用的UI相关辅助功能：
 * - Toast消息显示
 * - 图片加载和显示
 * - 支持本地文件路径和资源ID
 * 
 * 所有方法为静态方法，提供便捷的UI操作
 */
public final class UiUtils {
    private UiUtils() {}

    public static void toast(Context context, String msg) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
    }

    public static void setImage(ImageView view, String path, int fallbackResId) {
        if (path != null && !path.isEmpty()) {
            File f = new File(path);
            if (f.exists()) {
                Bitmap b = BitmapFactory.decodeFile(path);
                if (b != null) { view.setImageBitmap(b); return; }
            }
        }
        if (fallbackResId != 0) view.setImageResource(fallbackResId);
    }
}
