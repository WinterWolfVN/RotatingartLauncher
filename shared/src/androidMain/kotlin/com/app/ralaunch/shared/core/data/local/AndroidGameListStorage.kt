package com.app.ralaunch.shared.core.data.local

import com.app.ralaunch.shared.core.data.repository.GameListStorage

/**
 * Android 平台游戏列表存储实现。
 *
 * Android 仅提供目录路径，具体文件管理逻辑在 commonMain 的 [CommonGameListStorage]。
 */
class AndroidGameListStorage(
    pathsProvider: StoragePathsProvider
) : GameListStorage by CommonGameListStorage(pathsProvider)
