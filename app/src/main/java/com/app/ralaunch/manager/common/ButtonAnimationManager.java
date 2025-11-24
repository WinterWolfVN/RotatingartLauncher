package com.app.ralaunch.manager.common;

import android.view.View;
import com.daimajia.androidanimations.library.Techniques;
import com.daimajia.androidanimations.library.YoYo;

/**
 * 按钮动画管理器
 * 统一管理按钮点击动画，减少重复代码
 */
public class ButtonAnimationManager {
    
    /**
     * 为按钮设置点击监听器（带 BounceIn 动画）
     */
    public static void setClickListenerWithBounce(View button, View.OnClickListener listener) {
        if (button == null) return;
        
        button.setOnClickListener(v -> {
            YoYo.with(Techniques.BounceIn)
                    .duration(700)
                    .playOn(button);
            if (listener != null) {
                listener.onClick(v);
            }
        });
    }
    
    /**
     * 为按钮设置点击监听器（带 Pulse 动画）
     */
    public static void setClickListenerWithPulse(View button, View.OnClickListener listener) {
        if (button == null) return;
        
        button.setOnClickListener(v -> {
            YoYo.with(Techniques.Pulse)
                    .duration(200)
                    .playOn(v);
            if (listener != null) {
                listener.onClick(v);
            }
        });
    }
    
    /**
     * 为按钮设置点击监听器（无动画）
     */
    public static void setClickListener(View button, View.OnClickListener listener) {
        if (button != null && listener != null) {
            button.setOnClickListener(listener);
        }
    }
}

