package com.app.ralaunch.shared.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

/**
 * 导航状态
 */
@Stable
class NavState(
    initialScreen: Screen = Screen.Games
) {
    /** 当前屏幕 */
    var currentScreen by mutableStateOf(initialScreen)
        private set

    /** 导航方向 (用于切换动画) */
    var isForwardNavigation by mutableStateOf(true)
        private set

    /** 返回栈 */
    private val backStack = mutableListOf<Screen>()

    /** 是否可以返回 */
    val canGoBack: Boolean get() = backStack.isNotEmpty()

    /** 当前主导航目的地 */
    val currentDestination: NavDestination?
        get() = NavDestination.fromScreen(currentScreen)
            ?: NavDestination.fromRoute(currentScreen.route.split("/").first())

    /**
     * 导航到指定屏幕
     * @param screen 目标屏幕
     * @param addToBackStack 是否添加到返回栈
     */
    fun navigateTo(screen: Screen, addToBackStack: Boolean = true) {
        if (addToBackStack && currentScreen != screen) {
            backStack.add(currentScreen)
        }
        isForwardNavigation = true
        currentScreen = screen
    }

    /**
     * 导航到主导航目的地
     * 主导航之间切换时清空返回栈
     */
    fun navigateTo(destination: NavDestination) {
        if (currentDestination != destination) {
            val oldDest = currentDestination
            val newDest = destination
            
            // 计算导航方向
            if (oldDest != null) {
                isForwardNavigation = newDest.ordinal > oldDest.ordinal
            }
            
            backStack.clear()
            currentScreen = destination.screen
        }
    }

    /**
     * 返回上一个屏幕
     * @return 是否成功返回
     */
    fun goBack(): Boolean {
        return if (backStack.isNotEmpty()) {
            isForwardNavigation = false
            currentScreen = backStack.removeAt(backStack.lastIndex)
            true
        } else {
            false
        }
    }

    /**
     * 返回到主页面
     */
    fun popToRoot() {
        isForwardNavigation = false
        backStack.clear()
        currentScreen = Screen.Games
    }

    /**
     * 重置到指定屏幕
     */
    fun resetTo(screen: Screen) {
        isForwardNavigation = true
        backStack.clear()
        currentScreen = screen
    }
}

/**
 * 创建并记住 NavState
 */
@Composable
fun rememberNavState(
    initialScreen: Screen = Screen.Games
): NavState {
    return remember { NavState(initialScreen) }
}

/**
 * 应用导航宿主
 * 基于状态驱动的导航容器，无动画切换（更流畅）
 */
@Composable
fun AppNavHost(
    navState: NavState,
    modifier: Modifier = Modifier,
    content: @Composable (Screen) -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        content(navState.currentScreen)
    }
}


/**
 * 简化版导航宿主 - 无动画
 */
@Composable
fun SimpleNavHost(
    navState: NavState,
    modifier: Modifier = Modifier,
    content: @Composable (Screen) -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        content(navState.currentScreen)
    }
}
