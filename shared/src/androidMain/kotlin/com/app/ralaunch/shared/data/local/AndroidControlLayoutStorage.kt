package com.app.ralaunch.shared.data.local

import com.app.ralaunch.shared.data.repository.ControlLayoutStorage

/**
 * Android 平台控制布局存储实现。
 *
 * Android 仅提供路径与偏好存储，核心逻辑在 commonMain 的 [CommonControlLayoutStorage]。
 */
class AndroidControlLayoutStorage(
    pathsProvider: StoragePathsProvider
) : ControlLayoutStorage by CommonControlLayoutStorage(pathsProvider)
