package com.app.ralaunch.ui.compose.gog.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.app.ralaunch.gog.constants.GogConstants
import com.app.ralaunch.utils.AppLogger
import java.net.URLEncoder

/**
 * GOG WebView 内嵌登录界面
 * 直接使用 GOG 官方登录页面
 */
@Composable
fun GogEmbeddedWebLogin(
    onLoginSuccess: (authCode: String) -> Unit,
    onLoginFailed: (error: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isLoading by remember { mutableStateOf(true) }
    var currentUrl by remember { mutableStateOf("") }
    var webView by remember { mutableStateOf<WebView?>(null) }

    // 构建 OAuth 授权 URL
    val authUrl = remember {
        buildString {
            append("https://auth.gog.com/auth")
            append("?client_id=${GogConstants.CLIENT_ID}")
            append("&redirect_uri=${URLEncoder.encode(GogConstants.REDIRECT_URI, "UTF-8")}")
            append("&response_type=code")
            append("&layout=default")
            append("&brand=gog")
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E))
    ) {
        // 顶部工具栏
        Surface(
            color = Color(0xFF252542),
            tonalElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 标题和 URL
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "GOG 登录",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Text(
                        text = getDisplayUrl(currentUrl),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1
                    )
                }

                // 刷新按钮
                IconButton(
                    onClick = { webView?.reload() },
                    enabled = !isLoading
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "刷新",
                        tint = if (isLoading) Color.White.copy(alpha = 0.3f) else Color.White
                    )
                }
            }
        }

        // 加载指示器
        if (isLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF7B4BB9)
            )
        }

        // WebView
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp))
        ) {
            GogLoginWebView(
                authUrl = authUrl,
                onWebViewCreated = { webView = it },
                onPageStarted = { url ->
                    isLoading = true
                    currentUrl = url
                },
                onPageFinished = { url ->
                    isLoading = false
                    currentUrl = url
                },
                onAuthCodeReceived = { code ->
                    AppLogger.info("GogWebLogin", "获取到授权码")
                    onLoginSuccess(code)
                },
                onError = { error ->
                    AppLogger.error("GogWebLogin", "登录错误: $error")
                    onLoginFailed(error)
                }
            )
        }
    }
}

/**
 * 简化 URL 显示
 */
private fun getDisplayUrl(url: String): String {
    return when {
        url.contains("login.gog.com") -> "login.gog.com"
        url.contains("auth.gog.com") -> "auth.gog.com"
        url.contains("gog.com") -> "gog.com"
        url.isEmpty() -> "加载中..."
        else -> url.take(50)
    }
}

/**
 * GOG 登录 WebView
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun GogLoginWebView(
    authUrl: String,
    onWebViewCreated: (WebView) -> Unit,
    onPageStarted: (String) -> Unit,
    onPageFinished: (String) -> Unit,
    onAuthCodeReceived: (String) -> Unit,
    onError: (String) -> Unit
) {
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    builtInZoomControls = true
                    displayZoomControls = false
                    setSupportZoom(true)
                    userAgentString = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                }

                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(this, true)

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        url?.let { onPageStarted(it) }
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        url?.let { onPageFinished(it) }
                    }

                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val url = request?.url?.toString() ?: return false
                        
                        AppLogger.debug("GogWebLogin", "URL: $url")

                        if (url.startsWith(GogConstants.REDIRECT_URI) || 
                            url.contains("on_login_success") ||
                            url.contains("code=")) {
                            val code = extractAuthCode(url)
                            if (code != null) {
                                onAuthCodeReceived(code)
                                return true
                            }
                        }

                        if (url.contains("error=") || url.contains("login_failed")) {
                            val error = extractError(url)
                            onError(error ?: "Login failed")
                            return true
                        }

                        return false
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        super.onReceivedError(view, request, error)
                        if (request?.isForMainFrame == true) {
                            onError("网络错误: ${error?.description}")
                        }
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        AppLogger.debug("GogWebView", consoleMessage?.message() ?: "")
                        return true
                    }
                }

                onWebViewCreated(this)
                loadUrl(authUrl)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

private fun extractAuthCode(url: String): String? {
    return try {
        val codeStart = url.indexOf("code=")
        if (codeStart == -1) return null
        
        val valueStart = codeStart + 5
        var codeEnd = url.indexOf("&", valueStart)
        if (codeEnd == -1) codeEnd = url.indexOf("#", valueStart)
        if (codeEnd == -1) codeEnd = url.length
        
        url.substring(valueStart, codeEnd).takeIf { it.isNotEmpty() }
    } catch (e: Exception) {
        null
    }
}

private fun extractError(url: String): String? {
    return try {
        val errorStart = url.indexOf("error=")
        if (errorStart == -1) return null
        
        val valueStart = errorStart + 6
        var errorEnd = url.indexOf("&", valueStart)
        if (errorEnd == -1) errorEnd = url.length
        
        java.net.URLDecoder.decode(url.substring(valueStart, errorEnd), "UTF-8")
    } catch (e: Exception) {
        null
    }
}
