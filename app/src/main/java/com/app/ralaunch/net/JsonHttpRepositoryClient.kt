package com.app.ralaunch.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/**
 * 共享 JSON HTTP 访问工具，统一超时和错误处理。
 */
object JsonHttpRepositoryClient {
    suspend fun getText(
        urlString: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = openConnection(urlString, connectTimeoutMs, readTimeoutMs)
            connection.useInputStream()
        }
    }

    suspend inline fun <reified T> getJson(
        urlString: String,
        json: Json,
        connectTimeoutMs: Int,
        readTimeoutMs: Int
    ): Result<T> {
        return getText(
            urlString = urlString,
            connectTimeoutMs = connectTimeoutMs,
            readTimeoutMs = readTimeoutMs
        ).mapCatching { content ->
            json.decodeFromString<T>(content)
        }
    }

    fun openConnection(
        urlString: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int
    ): HttpURLConnection {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        connection.connectTimeout = connectTimeoutMs
        connection.readTimeout = readTimeoutMs
        connection.requestMethod = "GET"
        return connection
    }

    private fun HttpURLConnection.useInputStream(): String {
        return try {
            val responseCode = responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("HTTP $responseCode")
            }
            inputStream.bufferedReader().use { it.readText() }
        } finally {
            disconnect()
        }
    }
}
