package com.app.ralaunch.shared.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.ralaunch.shared.ui.navigation.NavDestination
import com.app.ralaunch.shared.ui.theme.AppThemeState

/**
 * 导航目的地 (兼容别名)
 */
@Deprecated(
    message = "请使用 NavDestination",
    replaceWith = ReplaceWith(
        "NavDestination",
        "com.app.ralaunch.shared.ui.navigation.NavDestination"
    )
)
typealias NavigationDestination = NavDestination

/**
 * 应用导航栏 - 现代化手柄风格定制版
 */
@Composable
fun AppNavigationRail(
    currentDestination: NavDestination?,
    onNavigate: (NavDestination) -> Unit,
    modifier: Modifier = Modifier,
    logo: (@Composable () -> Unit)? = null
) {
    val themeMode by AppThemeState.themeMode.collectAsState()
    val themeColor by AppThemeState.themeColor.collectAsState()
    
    key(themeMode, themeColor) {
        Surface(
            modifier = modifier
                .fillMaxHeight()
                .width(88.dp),
            color = Color.Transparent,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 上部导航项
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    // 导航项列表 (排除设置)
                    NavDestination.entries.filter { it != NavDestination.SETTINGS }.forEach { destination ->
                        val isSelected = currentDestination == destination
                        
                        CustomRailItem(
                            selected = isSelected,
                            icon = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                            label = destination.label,
                            onClick = { onNavigate(destination) }
                        )
                    }
                }

                // 设置项固定在底部
                val isSettingsSelected = currentDestination == NavDestination.SETTINGS
                CustomRailItem(
                    selected = isSettingsSelected,
                    icon = if (isSettingsSelected) NavDestination.SETTINGS.selectedIcon else NavDestination.SETTINGS.unselectedIcon,
                    label = NavDestination.SETTINGS.label,
                    onClick = { onNavigate(NavDestination.SETTINGS) }
                )
                
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

/**
 * 定制化导航项 - 增加平滑动画和手柄感反馈
 */
@Composable
private fun CustomRailItem(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)
    )

    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary 
                      else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        animationSpec = tween(250)
    )
    
    val indicatorHeight by animateDpAsState(
        targetValue = if (selected) 36.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = false, radius = 32.dp),
                onClick = onClick
            )
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        // 左侧选中指示条 - 弹性高度
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(4.dp)
                .height(indicatorHeight)
                .clip(RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                    )
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 11.sp,
                letterSpacing = 0.5.sp
            )
        }
    }
}

/**
 * 底部导航栏 - 现代化定制版
 */
@Composable
fun AppNavigationBar(
    currentDestination: NavDestination?,
    onNavigate: (NavDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val themeMode by AppThemeState.themeMode.collectAsState()
    val themeColor by AppThemeState.themeColor.collectAsState()
    
    key(themeMode, themeColor) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .height(80.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
            tonalElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavDestination.entries.forEach { destination ->
                    val isSelected = currentDestination == destination
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(onClick = { onNavigate(destination) }),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                                contentDescription = destination.label,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = destination.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
