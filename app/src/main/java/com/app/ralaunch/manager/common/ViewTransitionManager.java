package com.app.ralaunch.manager.common;

import android.view.View;
import android.view.ViewGroup;
import com.daimajia.androidanimations.library.Techniques;
import com.daimajia.androidanimations.library.YoYo;

/**
 * 视图切换管理器
 * 统一管理视图之间的切换动画
 */
public class ViewTransitionManager {
    
    /**
     * 切换视图（带淡入淡出动画）
     * @param hideView 要隐藏的视图
     * @param showView 要显示的视图
     * @param onComplete 切换完成回调
     */
    public static void transitionViews(View hideView, View showView, Runnable onComplete) {
        if (hideView == null || showView == null) {
            if (onComplete != null) onComplete.run();
            return;
        }
        
        YoYo.with(Techniques.FadeOut)
                .duration(200)
                .onEnd(animator -> {
                    hideView.setVisibility(View.GONE);
                    showView.setVisibility(View.VISIBLE);
                    
                    YoYo.with(Techniques.FadeIn)
                            .duration(200)
                            .playOn(showView);
                    
                    if (onComplete != null) {
                        onComplete.run();
                    }
                })
                .playOn(hideView);
    }
    
    /**
     * 切换视图（立即切换，无动画）
     */
    public static void transitionViewsImmediate(View hideView, View showView) {
        if (hideView != null) {
            hideView.setVisibility(View.GONE);
        }
        if (showView != null) {
            showView.setVisibility(View.VISIBLE);
        }
    }
    
    /**
     * 清除容器中的所有子视图
     */
    public static void clearContainer(ViewGroup container) {
        if (container != null) {
            container.removeAllViews();
        }
    }
    
    /**
     * 添加视图到容器
     */
    public static void addViewToContainer(ViewGroup container, View view) {
        if (container != null && view != null) {
            container.addView(view, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }
    }
}

