package com.app.ralib.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;

import java.io.File;

/**
 * UI 辅助工具
 * 提供常用的 UI 操作辅助方法
 */
public class UiHelper {
    
    /**
     * 从文件路径加载图片到 ImageView
     * 如果加载失败，使用默认资源
     */
    public static void loadImage(ImageView imageView, String path, int fallbackResId) {
        if (path != null && !path.isEmpty()) {
            File file = new File(path);
            if (file.exists()) {
                Bitmap bitmap = BitmapFactory.decodeFile(path);
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap);
                    return;
                }
            }
        }
        
        // 加载失败，使用默认资源
        if (fallbackResId != 0) {
            imageView.setImageResource(fallbackResId);
        }
    }
    
    /**
     * 从文件路径加载图片
     */
    public static Bitmap loadBitmap(String path) {
        if (path != null && !path.isEmpty()) {
            File file = new File(path);
            if (file.exists()) {
                return BitmapFactory.decodeFile(path);
            }
        }
        return null;
    }
    
    /**
     * 将 dp 转换为 px
     */
    public static int dpToPx(Context context, float dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
    
    /**
     * 将 px 转换为 dp
     */
    public static int pxToDp(Context context, float px) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(px / density);
    }
    
    /**
     * 获取屏幕宽度（px）
     */
    public static int getScreenWidth(Context context) {
        return context.getResources().getDisplayMetrics().widthPixels;
    }
    
    /**
     * 获取屏幕高度（px）
     */
    public static int getScreenHeight(Context context) {
        return context.getResources().getDisplayMetrics().heightPixels;
    }
}



