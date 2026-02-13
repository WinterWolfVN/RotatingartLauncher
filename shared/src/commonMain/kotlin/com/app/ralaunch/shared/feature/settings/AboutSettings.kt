package com.app.ralaunch.shared.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 关于页面状态
 */
data class AboutState(
    val appVersion: String = "Unknown",
    val buildInfo: String = "",
    val updateAvailable: Boolean = false
)

/**
 * 社区链接
 */
data class CommunityLink(
    val name: String,
    val icon: ImageVector,
    val url: String
)

/**
 * 贡献者信息
 */
data class Contributor(
    val name: String,
    val role: String,
    val githubUrl: String
)

/**
 * 关于设置内容 - 跨平台
 */
@Composable
fun AboutSettingsContent(
    state: AboutState,
    onCheckUpdateClick: () -> Unit,
    onLicenseClick: () -> Unit,
    onSponsorsClick: () -> Unit,
    onCommunityLinkClick: (String) -> Unit,
    onContributorClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val communityLinks = remember {
        listOf(
            CommunityLink("Discord", Icons.Default.Forum, "https://discord.gg/cVkrRdffGp"),
            CommunityLink("QQ 群", Icons.Default.Group, "https://qm.qq.com/q/BWiPSj6wWQ"),
            CommunityLink("GitHub", Icons.Default.Code, "https://github.com/FireworkSky/RotatingartLauncher")
        )
    }

    val sponsorLinks = remember {
        listOf(
            CommunityLink("爱发电", Icons.Default.Favorite, "https://afdian.com/a/RotatingartLauncher"),
            CommunityLink("Patreon", Icons.Default.Star, "https://www.patreon.com/c/RotatingArtLauncher")
        )
    }

    val contributors = remember {
        listOf(
            Contributor("FireworkSky", "项目作者", "https://github.com/FireworkSky"),
            Contributor("LaoSparrow", "核心开发者", "https://github.com/LaoSparrow")
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 应用信息
        AppInfoSection(
            appVersion = state.appVersion,
            buildInfo = state.buildInfo,
            onCheckUpdateClick = onCheckUpdateClick
        )

        // 社区链接
        CommunitySection(
            communityLinks = communityLinks,
            onLinkClick = onCommunityLinkClick
        )

        // 赞助支持
        SponsorSection(
            sponsorLinks = sponsorLinks,
            onSponsorsClick = onSponsorsClick,
            onLinkClick = onCommunityLinkClick
        )

        // 贡献者
        ContributorsSection(
            contributors = contributors,
            onContributorClick = onContributorClick
        )

        // 开源信息
        OpenSourceSection(
            onLicenseClick = onLicenseClick
        )
    }
}

@Composable
private fun AppInfoSection(
    appVersion: String,
    buildInfo: String,
    onCheckUpdateClick: () -> Unit
) {
    SettingsSection(title = "应用信息") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "RotatingArt Launcher",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "版本 $appVersion",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (buildInfo.isNotEmpty()) {
                    Text(
                        text = buildInfo,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }

        SettingsDivider()

        ClickableSettingItem(
            title = "检查更新",
            subtitle = "检查是否有新版本可用",
            icon = Icons.Default.Update,
            onClick = onCheckUpdateClick
        )
    }
}

@Composable
private fun CommunitySection(
    communityLinks: List<CommunityLink>,
    onLinkClick: (String) -> Unit
) {
    SettingsSection(title = "社区") {
        communityLinks.forEachIndexed { index, link ->
            if (index > 0) {
                SettingsDivider()
            }
            ClickableSettingItem(
                title = link.name,
                subtitle = "加入社区获取帮助",
                icon = link.icon,
                onClick = { onLinkClick(link.url) }
            )
        }
    }
}

@Composable
private fun SponsorSection(
    sponsorLinks: List<CommunityLink>,
    onSponsorsClick: () -> Unit,
    onLinkClick: (String) -> Unit
) {
    SettingsSection(title = "支持我们") {
        ClickableSettingItem(
            title = "赞助商墙",
            subtitle = "感谢所有支持者",
            icon = Icons.Default.People,
            onClick = onSponsorsClick
        )

        sponsorLinks.forEach { link ->
            SettingsDivider()
            ClickableSettingItem(
                title = link.name,
                subtitle = "成为赞助者",
                icon = link.icon,
                onClick = { onLinkClick(link.url) }
            )
        }
    }
}

@Composable
private fun ContributorsSection(
    contributors: List<Contributor>,
    onContributorClick: (String) -> Unit
) {
    SettingsSection(title = "贡献者") {
        contributors.forEachIndexed { index, contributor ->
            if (index > 0) {
                SettingsDivider()
            }
            ClickableSettingItem(
                title = contributor.name,
                subtitle = contributor.role,
                icon = Icons.Default.Person,
                onClick = { onContributorClick(contributor.githubUrl) }
            )
        }
    }
}

@Composable
private fun OpenSourceSection(
    onLicenseClick: () -> Unit
) {
    SettingsSection(title = "开源") {
        ClickableSettingItem(
            title = "开源许可",
            subtitle = "查看使用的开源库",
            icon = Icons.Default.Description,
            onClick = onLicenseClick
        )
    }
}
