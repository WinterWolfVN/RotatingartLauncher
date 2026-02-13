package com.app.ralaunch.shared.core.config

/**
 * 权限类型 - 跨平台抽象
 */
enum class PermissionType {
    STORAGE_READ,
    STORAGE_WRITE,
    MANAGE_EXTERNAL_STORAGE,
    VIBRATE,
    INTERNET,
    NOTIFICATION,
    CAMERA,
    MICROPHONE,
    LOCATION
}

/**
 * 权限状态
 */
enum class PermissionStatus {
    GRANTED,        // 已授权
    DENIED,         // 拒绝
    DENIED_FOREVER, // 永久拒绝（需要跳转设置）
    NOT_REQUESTED   // 未请求
}

/**
 * 权限请求结果
 */
data class PermissionResult(
    val permission: PermissionType,
    val status: PermissionStatus
)

/**
 * 权限管理接口 - 跨平台
 */
interface IPermissionManager {
    /**
     * 检查权限状态
     */
    fun checkPermission(permission: PermissionType): PermissionStatus

    /**
     * 检查多个权限状态
     */
    fun checkPermissions(permissions: List<PermissionType>): List<PermissionResult>

    /**
     * 是否拥有权限
     */
    fun hasPermission(permission: PermissionType): Boolean {
        return checkPermission(permission) == PermissionStatus.GRANTED
    }

    /**
     * 是否拥有所有权限
     */
    fun hasAllPermissions(permissions: List<PermissionType>): Boolean {
        return permissions.all { hasPermission(it) }
    }

    /**
     * 请求权限（平台特定实现）
     */
    fun requestPermission(permission: PermissionType, callback: (PermissionResult) -> Unit)

    /**
     * 请求多个权限
     */
    fun requestPermissions(permissions: List<PermissionType>, callback: (List<PermissionResult>) -> Unit)

    /**
     * 打开应用设置页面
     */
    fun openAppSettings()

    /**
     * 是否应该显示权限说明
     */
    fun shouldShowRationale(permission: PermissionType): Boolean
}

/**
 * 所需权限组
 */
object RequiredPermissions {
    /**
     * 基本存储权限
     */
    val STORAGE = listOf(
        PermissionType.STORAGE_READ,
        PermissionType.STORAGE_WRITE
    )

    /**
     * 完整存储权限（Android 11+）
     */
    val FULL_STORAGE = listOf(
        PermissionType.MANAGE_EXTERNAL_STORAGE
    )

    /**
     * 运行时必需权限
     */
    val RUNTIME = listOf(
        PermissionType.VIBRATE
    )

    /**
     * 可选权限
     */
    val OPTIONAL = listOf(
        PermissionType.NOTIFICATION
    )
}
