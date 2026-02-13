package com.app.ralaunch.shared.core.component.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 控制布局设置菜单项
 */
data class ControlLayoutMenuItem(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val icon: ImageVector
)

/**
 * 控制布局设置侧边栏
 * 用于替代原有的 ControlLayoutSettingsDialog
 */
@Composable
fun ControlLayoutSettingsSheet(
    menuItems: List<ControlLayoutMenuItem>,
    onItemClick: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .width(320.dp)
            .fillMaxHeight(),
        shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "布局设置",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭"
                    )
                }
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            
            // 菜单项
            menuItems.forEach { item ->
                SettingsMenuItem(
                    item = item,
                    onClick = { onItemClick(item.id) }
                )
            }
        }
    }
}

@Composable
private fun SettingsMenuItem(
    item: ControlLayoutMenuItem,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                item.subtitle?.let { subtitle ->
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    
    Spacer(modifier = Modifier.height(8.dp))
}

/**
 * 预定义的菜单项
 */
object ControlLayoutMenuItems {
    val IMPORT_LAYOUT = ControlLayoutMenuItem(
        id = "import_layout",
        title = "导入布局",
        subtitle = "从文件导入控制布局",
        icon = Icons.Default.FileUpload
    )
    
    val IMPORT_PRESET = ControlLayoutMenuItem(
        id = "import_preset",
        title = "导入预设",
        subtitle = "导入控制包预设",
        icon = Icons.Default.Inventory
    )
    
    val EXPORT_LAYOUT = ControlLayoutMenuItem(
        id = "export_layout",
        title = "导出布局",
        subtitle = "导出当前布局到文件",
        icon = Icons.Default.FileDownload
    )
    
    val RESET_LAYOUT = ControlLayoutMenuItem(
        id = "reset_layout",
        title = "重置布局",
        subtitle = "恢复默认控制布局",
        icon = Icons.Default.Refresh
    )
    
    fun defaultMenuItems() = listOf(
        IMPORT_LAYOUT,
        IMPORT_PRESET,
        EXPORT_LAYOUT,
        RESET_LAYOUT
    )
}
