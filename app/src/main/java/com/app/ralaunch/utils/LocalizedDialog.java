package com.app.ralaunch.utils;

import android.app.Dialog;
import android.content.Context;

import androidx.annotation.NonNull;

/**
 * 支持多语言的Dialog基类
 * 自动处理语言设置，确保Dialog使用正确的语言资源
 * 
 * 使用方法：继承LocalizedDialog而不是Dialog
 * 在onCreate()中使用getLocalizedContext()来获取应用了语言设置的Context
 * 例如：LayoutInflater.from(getLocalizedContext()).inflate(...)
 */
public class LocalizedDialog extends Dialog {
    
    private Context mBaseContext;
    private Context mLocalizedContext;
    
    public LocalizedDialog(@NonNull Context context) {
        super(context);
        mBaseContext = context;
        // 预先创建应用了语言设置的Context
        mLocalizedContext = LocaleManager.applyLanguage(context);
    }
    
    public LocalizedDialog(@NonNull Context context, int themeResId) {
        super(context, themeResId);
        mBaseContext = context;
        // 预先创建应用了语言设置的Context
        mLocalizedContext = LocaleManager.applyLanguage(context);
    }
    
    /**
     * 获取应用了语言设置的Context
     * 用于加载布局和获取字符串资源
     * 
     * @return 应用了语言设置的Context
     */
    @NonNull
    public Context getLocalizedContext() {
        return mLocalizedContext;
    }
}

