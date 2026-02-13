package com.app.ralaunch.core.common

/**
 * 应用导航路由定义
 */
sealed class Screen(val route: String) {
    // 主页面
    data object GameList : Screen("game_list")
    data object Control : Screen("control")
    data object Download : Screen("download")
    data object Settings : Screen("settings")
    data object Import : Screen("import")
    data object ControlStore : Screen("control_store")
    
    // 子页面
    data object FileBrowser : Screen("file_browser")
    data object GameDetail : Screen("game_detail/{gameId}") {
        fun createRoute(gameId: String) = "game_detail/$gameId"
    }
}

/**
 * 导航 Tab 项
 */
enum class NavTab(
    val route: String,
    val labelResId: Int,
    val iconResId: Int
) {
    GAME_LIST(Screen.GameList.route, com.app.ralaunch.R.string.nav_game, com.app.ralaunch.R.drawable.ic_game_controller),
    CONTROL(Screen.Control.route, com.app.ralaunch.R.string.nav_control, com.app.ralaunch.R.drawable.ic_game_controller),
    DOWNLOAD(Screen.Download.route, com.app.ralaunch.R.string.nav_download, com.app.ralaunch.R.drawable.ic_download),
    SETTINGS(Screen.Settings.route, com.app.ralaunch.R.string.nav_settings, com.app.ralaunch.R.drawable.ic_settings),
    IMPORT(Screen.Import.route, com.app.ralaunch.R.string.nav_download, com.app.ralaunch.R.drawable.ic_add)
}
