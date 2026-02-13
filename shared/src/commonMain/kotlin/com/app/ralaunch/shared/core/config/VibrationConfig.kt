package com.app.ralaunch.shared.core.config

/**
 * 震动类型
 */
enum class VibrationType {
    CLICK,      // 点击反馈
    LONG_PRESS, // 长按反馈
    HEAVY,      // 重反馈
    LIGHT,      // 轻反馈
    TICK,       // 刻度反馈
    CONFIRM,    // 确认反馈
    REJECT,     // 拒绝反馈
    CUSTOM      // 自定义
}

/**
 * 震动配置
 */
data class VibrationConfig(
    val enabled: Boolean = true,
    val intensity: Float = 1.0f, // 0.0 - 1.0
    val duration: Long = 50L     // 毫秒
) {
    companion object {
        val Default = VibrationConfig()
        val Disabled = VibrationConfig(enabled = false)
    }
}

/**
 * 震动管理接口 - 跨平台
 */
interface IVibrationManager {
    /**
     * 是否支持震动
     */
    fun isSupported(): Boolean

    /**
     * 是否启用震动
     */
    fun isEnabled(): Boolean

    /**
     * 设置是否启用震动
     */
    fun setEnabled(enabled: Boolean)

    /**
     * 获取震动强度 (0.0 - 1.0)
     */
    fun getIntensity(): Float

    /**
     * 设置震动强度
     */
    fun setIntensity(intensity: Float)

    /**
     * 执行震动
     */
    fun vibrate(type: VibrationType = VibrationType.CLICK)

    /**
     * 执行自定义时长震动
     */
    fun vibrate(durationMs: Long)

    /**
     * 执行模式震动
     */
    fun vibratePattern(pattern: LongArray, repeat: Int = -1)

    /**
     * 取消震动
     */
    fun cancel()
}
