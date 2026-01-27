package com.app.ralaunch.utils

/**
 * 原生方法封装
 */
object NativeMethods {
    
    @JvmStatic
    fun chdir(path: String): Int = nativeChdir(path)

    @JvmStatic
    private external fun nativeChdir(path: String): Int
}
