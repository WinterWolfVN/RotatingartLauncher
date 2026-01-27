package com.app.ralaunch.gog.model

/**
 * GOG 用户信息
 */
data class GogUserInfo(
    val username: String,
    val email: String,
    val avatarUrl: String,
    val userId: String = ""
) {
    companion object {
        val EMPTY = GogUserInfo("", "", "")
    }
    
    val isValid: Boolean get() = username.isNotEmpty()
}
