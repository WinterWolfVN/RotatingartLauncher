package com.app.ralaunch.utils;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

/**
 * 支持多语言的AlertDialog基类
 * 自动处理语言设置，确保AlertDialog使用正确的语言资源
 * 
 * 使用方法：
 * 1. 继承LocalizedAlertDialog而不是AlertDialog
 * 2. 在构造函数中调用super(context)或super(context, themeResId)
 * 3. 在需要获取字符串资源时使用getLocalizedContext()
 * 例如：getLocalizedContext().getString(...)
 */
public class LocalizedAlertDialog extends AlertDialog {
    
    private Context mBaseContext;
    private Context mLocalizedContext;
    
    protected LocalizedAlertDialog(@NonNull Context context) {
        super(context);
        mBaseContext = context;
        // 预先创建应用了语言设置的Context
        mLocalizedContext = LocaleManager.applyLanguage(context);
    }
    
    protected LocalizedAlertDialog(@NonNull Context context, int themeResId) {
        super(context, themeResId);
        mBaseContext = context;
        // 预先创建应用了语言设置的Context
        mLocalizedContext = LocaleManager.applyLanguage(context);
    }
    
    /**
     * 获取应用了语言设置的Context
     * 用于获取字符串资源
     * 
     * @return 应用了语言设置的Context
     */
    @NonNull
    public Context getLocalizedContext() {
        return mLocalizedContext;
    }
}

