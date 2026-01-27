package com.app.ralaunch.utils

import android.app.Dialog
import android.content.Context

/**
 * 支持多语言的 Dialog 基类
 */
open class LocalizedDialog @JvmOverloads constructor(
    context: Context,
    themeResId: Int = 0
) : Dialog(context, themeResId) {

    val localizedContext: Context = LocaleManager.applyLanguage(context) ?: context
}
