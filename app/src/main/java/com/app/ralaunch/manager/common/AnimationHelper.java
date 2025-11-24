package com.app.ralaunch.manager.common;

import android.view.View;
import android.view.animation.Animation;
import com.daimajia.androidanimations.library.Techniques;
import com.daimajia.androidanimations.library.YoYo;

/**
 * 动画辅助类
 * 统一管理动画效果，减少代码重复
 */
public class AnimationHelper {
    
    /**
     * 按钮点击动画 - BounceIn
     */
    public static void animateButtonClick(View button, Runnable action) {
        if (button == null) {
            if (action != null) action.run();
            return;
        }
        
        YoYo.with(Techniques.BounceIn)
                .duration(700)
                .playOn(button);
        
        if (action != null) {
            action.run();
        }
    }
    
    /**
     * 按钮脉冲动画 - Pulse
     */
    public static void animateButtonPulse(View button, Runnable action) {
        if (button == null) {
            if (action != null) action.run();
            return;
        }
        
        YoYo.with(Techniques.Pulse)
                .duration(200)
                .playOn(button);
        
        if (action != null) {
            action.run();
        }
    }
    
    /**
     * 刷新动画 - Flash
     */
    public static void animateRefresh(View view) {
        if (view != null) {
            YoYo.with(Techniques.Flash)
                    .duration(600)
                    .playOn(view);
        }
    }
    
    /**
     * 选中动画 - Tada
     */
    public static void animateSelection(View view) {
        if (view != null) {
            YoYo.with(Techniques.Tada)
                    .duration(800)
                    .playOn(view);
        }
    }
    
    /**
     * 滑入动画 - SlideInRight
     */
    public static void animateSlideInRight(View view) {
        if (view != null) {
            YoYo.with(Techniques.SlideInRight)
                    .duration(300)
                    .playOn(view);
        }
    }
    
    /**
     * 淡出动画
     */
    public static void animateFadeOut(View view, Runnable onEnd) {
        if (view == null) {
            if (onEnd != null) onEnd.run();
            return;
        }
        
        YoYo.with(Techniques.FadeOut)
                .duration(200)
                .onEnd(animator -> {
                    if (onEnd != null) onEnd.run();
                })
                .playOn(view);
    }
    
    /**
     * 淡入动画
     */
    public static void animateFadeIn(View view) {
        if (view != null) {
            YoYo.with(Techniques.FadeIn)
                    .duration(200)
                    .playOn(view);
        }
    }
    
    /**
     * 缩放动画 - 用于更新提示
     */
    public static void animateScaleUpdate(View view) {
        if (view == null) return;
        
        view.animate()
                .scaleX(1.2f)
                .scaleY(1.2f)
                .setDuration(150)
                .withEndAction(() -> {
                    view.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(150)
                            .start();
                })
                .start();
    }
}

