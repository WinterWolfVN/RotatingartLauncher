package com.app.ralaunch.shared.core.platform

/**
 * 平台信息
 */
expect class Platform() {
    val name: String
}

/**
 * 获取当前平台
 */
fun getPlatform(): Platform = Platform()
