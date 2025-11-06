package com.app.ralib.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

/**
 * 动画辅助工具
 * 提供常用的UI动画效果
 */
public class AnimationHelper {
    
    /**
     * 淡入动画
     */
    public static void fadeIn(View view) {
        fadeIn(view, 300, null);
    }
    
    public static void fadeIn(View view, long duration) {
        fadeIn(view, duration, null);
    }
    
    public static void fadeIn(View view, long duration, Runnable onEnd) {
        view.setAlpha(0f);
        view.setVisibility(View.VISIBLE);
        view.animate()
            .alpha(1f)
            .setDuration(duration)
            .setListener(onEnd != null ? new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    onEnd.run();
                }
            } : null)
            .start();
    }
    
    /**
     * 淡出动画
     */
    public static void fadeOut(View view) {
        fadeOut(view, 300, null);
    }
    
    public static void fadeOut(View view, long duration) {
        fadeOut(view, duration, null);
    }
    
    public static void fadeOut(View view, long duration, Runnable onEnd) {
        view.animate()
            .alpha(0f)
            .setDuration(duration)
            .setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    view.setVisibility(View.GONE);
                    view.setAlpha(1f); // 重置
                    if (onEnd != null) {
                        onEnd.run();
                    }
                }
            })
            .start();
    }
    
    /**
     * 缩放进入动画
     */
    public static void scaleIn(View view) {
        scaleIn(view, 200, null);
    }
    
    public static void scaleIn(View view, long duration, Runnable onEnd) {
        view.setScaleX(0.8f);
        view.setScaleY(0.8f);
        view.setAlpha(0f);
        view.setVisibility(View.VISIBLE);
        
        view.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(duration)
            .setInterpolator(new DecelerateInterpolator())
            .setListener(onEnd != null ? new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    onEnd.run();
                }
            } : null)
            .start();
    }
    
    /**
     * 缩放退出动画
     */
    public static void scaleOut(View view) {
        scaleOut(view, 200, null);
    }
    
    public static void scaleOut(View view, long duration, Runnable onEnd) {
        view.animate()
            .scaleX(0.8f)
            .scaleY(0.8f)
            .alpha(0f)
            .setDuration(duration)
            .setInterpolator(new AccelerateInterpolator())
            .setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    view.setVisibility(View.GONE);
                    view.setScaleX(1f);
                    view.setScaleY(1f);
                    view.setAlpha(1f);
                    if (onEnd != null) {
                        onEnd.run();
                    }
                }
            })
            .start();
    }
    
    /**
     * 点击反馈动画
     */
    public static void clickFeedback(View view) {
        clickFeedback(view, null);
    }
    
    public static void clickFeedback(View view, Runnable onEnd) {
        view.animate()
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(100)
            .setInterpolator(new DecelerateInterpolator())
            .withEndAction(() -> {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .setInterpolator(new OvershootInterpolator())
                    .withEndAction(onEnd)
                    .start();
            })
            .start();
    }
    
    /**
     * 从左侧滑入
     */
    public static void slideInFromLeft(View view) {
        slideInFromLeft(view, 300, 0, null);
    }
    
    public static void slideInFromLeft(View view, long duration, long delay, Runnable onEnd) {
        view.setTranslationX(-view.getWidth());
        view.setAlpha(0f);
        view.setVisibility(View.VISIBLE);
        
        view.animate()
            .translationX(0)
            .alpha(1f)
            .setDuration(duration)
            .setStartDelay(delay)
            .setInterpolator(new DecelerateInterpolator())
            .setListener(onEnd != null ? new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    onEnd.run();
                }
            } : null)
            .start();
    }
    
    /**
     * 从右侧滑入
     */
    public static void slideInFromRight(View view) {
        slideInFromRight(view, 300, 0, null);
    }
    
    public static void slideInFromRight(View view, long duration, long delay, Runnable onEnd) {
        view.setTranslationX(view.getWidth());
        view.setAlpha(0f);
        view.setVisibility(View.VISIBLE);
        
        view.animate()
            .translationX(0)
            .alpha(1f)
            .setDuration(duration)
            .setStartDelay(delay)
            .setInterpolator(new DecelerateInterpolator())
            .setListener(onEnd != null ? new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    onEnd.run();
                }
            } : null)
            .start();
    }
    
    /**
     * 从下方滑入
     */
    public static void slideInFromBottom(View view) {
        slideInFromBottom(view, 300, 0, null);
    }
    
    public static void slideInFromBottom(View view, long duration, long delay, Runnable onEnd) {
        view.setTranslationY(200);
        view.setAlpha(0f);
        view.setVisibility(View.VISIBLE);
        
        view.animate()
            .translationY(0)
            .alpha(1f)
            .setDuration(duration)
            .setStartDelay(delay)
            .setInterpolator(new DecelerateInterpolator())
            .setListener(onEnd != null ? new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    onEnd.run();
                }
            } : null)
            .start();
    }
    
    /**
     * 列表项进入动画（滑入+淡入，带延迟）
     */
    public static void enterAnimation(View view, int position) {
        enterAnimation(view, position, 50);
    }
    
    public static void enterAnimation(View view, int position, int delayPerItem) {
        view.setAlpha(0f);
        view.setTranslationY(50);
        
        view.animate()
            .alpha(1f)
            .translationY(0)
            .setDuration(300)
            .setStartDelay(position * delayPerItem)
            .setInterpolator(new DecelerateInterpolator())
            .start();
    }
    
    /**
     * 抖动动画
     */
    public static void shake(View view) {
        view.animate()
            .translationX(-10f)
            .setDuration(50)
            .withEndAction(() -> {
                view.animate()
                    .translationX(10f)
                    .setDuration(50)
                    .withEndAction(() -> {
                        view.animate()
                            .translationX(-10f)
                            .setDuration(50)
                            .withEndAction(() -> {
                                view.animate()
                                    .translationX(0f)
                                    .setDuration(50)
                                    .start();
                            })
                            .start();
                    })
                    .start();
            })
            .start();
    }
    
    /**
     * 脉冲动画（放大缩小）
     */
    public static void pulse(View view) {
        pulse(view, 1);
    }
    
    public static void pulse(View view, int repeatCount) {
        pulseInternal(view, repeatCount, 0);
    }
    
    private static void pulseInternal(View view, int repeatCount, int currentCount) {
        if (currentCount >= repeatCount) {
            return;
        }
        
        view.animate()
            .scaleX(1.1f)
            .scaleY(1.1f)
            .setDuration(150)
            .setInterpolator(new DecelerateInterpolator())
            .withEndAction(() -> {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(150)
                    .setInterpolator(new AccelerateInterpolator())
                    .withEndAction(() -> {
                        pulseInternal(view, repeatCount, currentCount + 1);
                    })
                    .start();
            })
            .start();
    }
}

