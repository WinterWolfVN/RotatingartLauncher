package com.app.ralaunch.utils;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentCallbacks;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.DisplayMetrics;
import android.util.Log;

import androidx.annotation.NonNull;

/**
 * 屏幕适配工具类
 */
public class DensityAdapter {
    private static final String TAG = "DensityAdapter";

    private static float sBaseWidth = 2560f;   // 基准宽度（物理像素）- JSON基准宽度
    private static float sBaseHeight = 1080f;  // 基准高度（物理像素）- JSON基准高度
    
    // 保存应用原始 density 相关值
    private static float sAppDensity;
    private static float sAppScaledDensity;
    private static DisplayMetrics sAppDisplayMetrics;
    
    // 当前设备的屏幕尺寸
    private static int sScreenWidth;
    private static int sScreenHeight;
    
    // 缩放比例
    private static float sScaleX;
    private static float sScaleY;
    
    /**
     * 在 Application 中初始化
     * 检测设备分辨率并计算缩放比例
     */
    public static void init(@NonNull final Application application) {
        sAppDisplayMetrics = application.getResources().getDisplayMetrics();
        
        if (sAppDensity == 0) {
            sAppDensity = sAppDisplayMetrics.density;
            sAppScaledDensity = sAppDisplayMetrics.scaledDensity;
            
            // 获取设备屏幕尺寸（横屏）
            // 注意：初始化时可能是竖屏，需要确保取较大值作为宽度
            sScreenWidth = Math.max(sAppDisplayMetrics.widthPixels, sAppDisplayMetrics.heightPixels);
            sScreenHeight = Math.min(sAppDisplayMetrics.widthPixels, sAppDisplayMetrics.heightPixels);
            
            // 计算缩放比例
            sScaleX = sScreenWidth / sBaseWidth;
            sScaleY = sScreenHeight / sBaseHeight;

            
            // 监听系统字体大小变化
            application.registerComponentCallbacks(new ComponentCallbacks() {
                @Override
                public void onConfigurationChanged(@NonNull Configuration newConfig) {
                    if (newConfig.fontScale > 0) {
                        sAppScaledDensity = application.getResources().getDisplayMetrics().scaledDensity;
                        Log.d(TAG, "系统字体大小变化，更新 scaledDensity: " + sAppScaledDensity);
                    }
                }
                
                @Override
                public void onLowMemory() {
                    // 不处理
                }
            });
        }
    }
    

    public static void adapt(@NonNull final Activity activity) {
        final DisplayMetrics displayMetrics = activity.getResources().getDisplayMetrics();
        
        // 更新屏幕尺寸（处理横竖屏切换）
        int currentWidth = Math.max(displayMetrics.widthPixels, displayMetrics.heightPixels);
        int currentHeight = Math.min(displayMetrics.widthPixels, displayMetrics.heightPixels);
        
        if (currentWidth != sScreenWidth || currentHeight != sScreenHeight) {
            sScreenWidth = currentWidth;
            sScreenHeight = currentHeight;
            sScaleX = sScreenWidth / sBaseWidth;
            sScaleY = sScreenHeight / sBaseHeight;
            

        }
        

    }
    
    /**
     * 兼容旧代码的重载方法
     */
    public static void adapt(@NonNull final Activity activity, boolean isBaseOnWidth) {
        adapt(activity);
    }
    
    /**
     * 取消适配（本方案中无操作，保持接口兼容性）
     */
    public static void cancelAdapt(@NonNull final Activity activity) {
        Log.d(TAG, "取消适配: " + activity.getClass().getSimpleName() + "（本方案无需取消）");
    }
    
    /**
     * 获取设计图宽度（返回当前设备的物理宽度）
     * 用于 ControlDataConverter 计算
     */
    public static float getDesignWidthDp() {
        return sBaseWidth;
    }
    
    /**
     * 获取设计图高度（返回当前设备的物理高度）
     */
    public static float getDesignHeightDp() {
        return sBaseHeight;
    }
    
    /**
     * 获取当前设备屏幕宽度（物理像素）
     */
    public static int getScreenWidth() {
        return sScreenWidth;
    }
    
    /**
     * 获取当前设备屏幕高度（物理像素）
     */
    public static int getScreenHeight() {
        return sScreenHeight;
    }
    
    /**
     * 获取 X 轴缩放比例
     */
    public static float getScaleX() {
        return sScaleX;
    }
    
    /**
     * 获取 Y 轴缩放比例
     */
    public static float getScaleY() {
        return sScaleY;
    }
    
    /**
     * px 转 dp（基于当前 density）
     */
    public static float px2dp(Context context, float px) {
        return px / context.getResources().getDisplayMetrics().density;
    }
    
    /**
     * dp 转 px（基于当前 density）
     */
    public static float dp2px(Context context, float dp) {
        return dp * context.getResources().getDisplayMetrics().density;
    }
    
    /**
     * 获取当前适配比例（返回 X 轴缩放比例）
     */
    public static float getAdaptScale(Context context) {
        return sScaleX;
    }
    
    /**
     * 根据基准分辨率缩放坐标/尺寸（X 轴）
     * @param baseValue 基于基准分辨率的值
     * @return 当前设备上的实际值
     */
    public static float scaleX(float baseValue) {
        return baseValue * sScaleX;
    }
    
    /**
     * 根据基准分辨率缩放坐标/尺寸（Y 轴）
     * @param baseValue 基于基准分辨率的值
     * @return 当前设备上的实际值
     */
    public static float scaleY(float baseValue) {
        return baseValue * sScaleY;
    }
}
