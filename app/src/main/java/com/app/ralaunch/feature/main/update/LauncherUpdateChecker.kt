package com.app.ralaunch.feature.main.update

import com.app.ralaunch.core.common.JsonHttpRepositoryClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class LauncherUpdateChecker(
    private val repositoryOwner: String = DEFAULT_REPOSITORY_OWNER,
    private val repositoryName: String = DEFAULT_REPOSITORY_NAME
) {

    companion object {
        private const val GITHUB_API_BASE_URL = "https://api.github.com"
        private const val DEFAULT_REPOSITORY_OWNER = "FireworkSky"
        private const val DEFAULT_REPOSITORY_NAME = "RotatingartLauncher"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 15_000
        private const val USER_AGENT = "RotatingartLauncher-Android"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun checkForUpdate(currentVersionName: String): Result<LauncherUpdateInfo?> {
        val currentVersion = parseVersion(currentVersionName)
            ?: return Result.failure(
                IllegalArgumentException("Invalid current version: $currentVersionName")
            )

        val url = "$GITHUB_API_BASE_URL/repos/$repositoryOwner/$repositoryName/releases/latest"

        return JsonHttpRepositoryClient.getJson<GitHubReleaseResponse>(
            urlString = url,
            json = json,
            connectTimeoutMs = CONNECT_TIMEOUT_MS,
            readTimeoutMs = READ_TIMEOUT_MS,
            headers = mapOf(
                "Accept" to "application/vnd.github+json",
                "User-Agent" to USER_AGENT
            )
        ).mapCatching { release ->
            if (release.draft || release.prerelease || release.tagName.isBlank()) {
                return@mapCatching null
            }

            val latestVersion = parseVersion(release.tagName) ?: return@mapCatching null
            if (!isRemoteVersionNewer(currentVersion, latestVersion)) {
                return@mapCatching null
            }

            LauncherUpdateInfo(
                currentVersion = currentVersionName,
                latestVersion = release.tagName.trim(),
                releaseName = release.name.ifBlank { release.tagName.trim() },
                releaseNotes = release.body.trim(),
                releaseUrl = release.htmlUrl.ifBlank {
                    "https://github.com/$repositoryOwner/$repositoryName/releases/latest"
                }
            )
        }
    }

    private fun parseVersion(rawValue: String): ParsedVersion? {
        val cleanedValue = rawValue
            .trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore('-')
            .substringBefore('+')

        if (cleanedValue.isBlank()) return null

        val parts = cleanedValue.split('.')
            .mapNotNull { segment -> segment.toIntOrNull() }

        if (parts.isEmpty()) return null
        return ParsedVersion(parts)
    }

    private fun isRemoteVersionNewer(
        currentVersion: ParsedVersion,
        remoteVersion: ParsedVersion
    ): Boolean {
        val maxLength = maxOf(currentVersion.parts.size, remoteVersion.parts.size)
        for (index in 0 until maxLength) {
            val currentPart = currentVersion.parts.getOrElse(index) { 0 }
            val remotePart = remoteVersion.parts.getOrElse(index) { 0 }
            if (remotePart > currentPart) return true
            if (remotePart < currentPart) return false
        }
        return false
    }

    private data class ParsedVersion(
        val parts: List<Int>
    )

    @Serializable
    private data class GitHubReleaseResponse(
        @SerialName("tag_name")
        val tagName: String = "",
        val name: String = "",
        val body: String = "",
        @SerialName("html_url")
        val htmlUrl: String = "",
        val draft: Boolean = false,
        val prerelease: Boolean = false
    )
}

data class LauncherUpdateInfo(
    val currentVersion: String,
    val latestVersion: String,
    val releaseName: String,
    val releaseNotes: String,
    val releaseUrl: String
)
