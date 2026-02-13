package com.app.ralaunch.core.platform.network.easytier

import android.util.Log
import kotlinx.coroutines.*
import java.net.InetSocketAddress
import java.net.Socket

/**
 * 诊断步骤状态
 */
enum class DiagStepStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    SKIPPED
}

/**
 * 单个诊断步骤结果
 */
data class DiagStepResult(
    val name: String,
    val status: DiagStepStatus = DiagStepStatus.PENDING,
    val message: String = "",
    val detail: String? = null
)

/**
 * EasyTier 诊断工具
 * 逐步测试整个联机链路，帮助定位问题
 */
object EasyTierDiagnostics {

    private const val TAG = "EasyTierDiag"
    private const val TEST_INSTANCE_NAME = "diag_test"
    private const val TEST_NETWORK_NAME = "diag_net_${Long.MAX_VALUE}"
    private const val TEST_NETWORK_SECRET = "diag_secret_test"

    /**
     * 运行完整诊断
     * @param onStepUpdate 每步结果更新回调（步骤索引, 结果）
     * @return 所有步骤的结果列表
     */
    suspend fun runFullDiagnostics(
        onStepUpdate: (Int, DiagStepResult) -> Unit
    ): List<DiagStepResult> = withContext(Dispatchers.IO) {

        val results = mutableListOf(
            DiagStepResult("JNI 库加载检查"),
            DiagStepResult("配置生成"),
            DiagStepResult("配置解析测试"),
            DiagStepResult("网络实例启动"),
            DiagStepResult("网络信息收集"),
            DiagStepResult("端口监听检测 (7777)"),
            DiagStepResult("清理测试实例")
        )

        fun update(index: Int, result: DiagStepResult) {
            results[index] = result
            onStepUpdate(index, result)
        }

        // Step 0: JNI 库加载检查
        update(0, DiagStepResult("JNI 库加载检查", DiagStepStatus.RUNNING, "检查中..."))
        try {
            if (EasyTierJNI.isAvailable()) {
                val lastError = EasyTierJNI.getLastError()
                update(0, DiagStepResult(
                    "JNI 库加载检查",
                    DiagStepStatus.SUCCESS,
                    "JNI 库已加载",
                    "getLastError() = $lastError"
                ))
            } else {
                val error = EasyTierJNI.getLoadError() ?: "未知错误"
                update(0, DiagStepResult(
                    "JNI 库加载检查",
                    DiagStepStatus.FAILED,
                    "JNI 库加载失败",
                    error
                ))
                // 后续步骤全部跳过
                for (i in 1 until results.size) {
                    update(i, DiagStepResult(results[i].name, DiagStepStatus.SKIPPED, "跳过：JNI 库不可用"))
                }
                return@withContext results
            }
        } catch (e: Exception) {
            update(0, DiagStepResult("JNI 库加载检查", DiagStepStatus.FAILED, "异常: ${e.message}"))
            for (i in 1 until results.size) {
                update(i, DiagStepResult(results[i].name, DiagStepStatus.SKIPPED, "跳过"))
            }
            return@withContext results
        }

        // Step 1: 配置生成
        update(1, DiagStepResult("配置生成", DiagStepStatus.RUNNING, "生成 Host 和 Guest 配置..."))
        val hostConfig: String
        val guestConfig: String
        try {
            hostConfig = EasyTierConfigBuilder.buildConfig(
                instanceName = TEST_INSTANCE_NAME,
                networkName = TEST_NETWORK_NAME,
                networkSecret = TEST_NETWORK_SECRET,
                isHost = true,
                withPortForward = true,
                gamePorts = listOf(EasyTierManager.TERRARIA_PORT),
                publicServers = listOf("tcp://public.easytier.cn:11010")
            )
            guestConfig = EasyTierConfigBuilder.buildConfig(
                instanceName = "${TEST_INSTANCE_NAME}_guest",
                networkName = TEST_NETWORK_NAME,
                networkSecret = TEST_NETWORK_SECRET,
                isHost = false,
                withPortForward = true,
                gamePorts = listOf(EasyTierManager.TERRARIA_PORT),
                publicServers = listOf("tcp://public.easytier.cn:11010")
            )
            val configPreview = "Host 配置 (${hostConfig.length} 字符):\n${hostConfig.take(500)}...\n\nGuest 配置 (${guestConfig.length} 字符):\n${guestConfig.take(500)}..."
            update(1, DiagStepResult("配置生成", DiagStepStatus.SUCCESS, "配置生成成功", configPreview))
        } catch (e: Exception) {
            update(1, DiagStepResult("配置生成", DiagStepStatus.FAILED, "配置生成失败: ${e.message}"))
            for (i in 2 until results.size) {
                update(i, DiagStepResult(results[i].name, DiagStepStatus.SKIPPED, "跳过"))
            }
            return@withContext results
        }

        // Step 2: 配置解析测试
        update(2, DiagStepResult("配置解析测试", DiagStepStatus.RUNNING, "解析 Host 配置..."))
        try {
            val hostParseResult = EasyTierJNI.parseConfig(hostConfig)
            val hostParseError = EasyTierJNI.getLastError()

            val guestParseResult = EasyTierJNI.parseConfig(guestConfig)
            val guestParseError = EasyTierJNI.getLastError()

            if (hostParseResult == 0 && guestParseResult == 0) {
                update(2, DiagStepResult(
                    "配置解析测试",
                    DiagStepStatus.SUCCESS,
                    "Host 和 Guest 配置解析成功",
                    "hostParseResult=$hostParseResult, guestParseResult=$guestParseResult"
                ))
            } else {
                val detail = buildString {
                    append("Host: result=$hostParseResult")
                    if (hostParseError != null) append(", error=$hostParseError")
                    append("\nGuest: result=$guestParseResult")
                    if (guestParseError != null) append(", error=$guestParseError")
                }
                update(2, DiagStepResult("配置解析测试", DiagStepStatus.FAILED, "配置解析失败", detail))
                for (i in 3 until results.size) {
                    update(i, DiagStepResult(results[i].name, DiagStepStatus.SKIPPED, "跳过：配置解析失败"))
                }
                return@withContext results
            }
        } catch (e: Exception) {
            update(2, DiagStepResult("配置解析测试", DiagStepStatus.FAILED, "异常: ${e.message}"))
            for (i in 3 until results.size) {
                update(i, DiagStepResult(results[i].name, DiagStepStatus.SKIPPED, "跳过"))
            }
            return@withContext results
        }

        // Step 3: 网络实例启动（使用 guest config 来测试端口转发）
        update(3, DiagStepResult("网络实例启动", DiagStepStatus.RUNNING, "启动 Guest 实例（测试端口转发）..."))
        try {
            // 先确保清理旧实例
            EasyTierJNI.stopAllInstances()
            delay(500)

            val runResult = EasyTierJNI.runNetworkInstance(guestConfig)
            val runError = EasyTierJNI.getLastError()

            if (runResult == 0) {
                update(3, DiagStepResult(
                    "网络实例启动",
                    DiagStepStatus.SUCCESS,
                    "网络实例启动成功",
                    "runResult=$runResult"
                ))
            } else {
                update(3, DiagStepResult(
                    "网络实例启动",
                    DiagStepStatus.FAILED,
                    "网络实例启动失败",
                    "runResult=$runResult, error=$runError"
                ))
                for (i in 4 until results.size) {
                    update(i, DiagStepResult(results[i].name, DiagStepStatus.SKIPPED, "跳过：实例未启动"))
                }
                return@withContext results
            }
        } catch (e: Exception) {
            update(3, DiagStepResult("网络实例启动", DiagStepStatus.FAILED, "异常: ${e.message}"))
            for (i in 4 until results.size) {
                update(i, DiagStepResult(results[i].name, DiagStepStatus.SKIPPED, "跳过"))
            }
            return@withContext results
        }

        // Step 4: 网络信息收集
        update(4, DiagStepResult("网络信息收集", DiagStepStatus.RUNNING, "等待网络初始化..."))
        try {
            delay(2000) // 等待网络初始化
            val infosJson = EasyTierJNI.collectNetworkInfos()
            val infoLen = infosJson?.length ?: 0

            if (infosJson != null && infoLen > 50) {
                val parsed = EasyTierJsonParser.parseNetworkInfo(infosJson)
                val instanceCount = parsed?.size ?: 0
                val peerCount = parsed?.values?.sumOf { it.peers.size } ?: 0
                val runningCount = parsed?.values?.count { it.running } ?: 0

                update(4, DiagStepResult(
                    "网络信息收集",
                    DiagStepStatus.SUCCESS,
                    "收集到 $instanceCount 个实例, $runningCount 个运行中, $peerCount 个节点",
                    "JSON 长度: $infoLen\n${infosJson.take(800)}"
                ))
            } else {
                update(4, DiagStepResult(
                    "网络信息收集",
                    DiagStepStatus.FAILED,
                    "网络信息为空或太短",
                    "JSON 长度: $infoLen, 内容: ${infosJson ?: "null"}"
                ))
            }
        } catch (e: Exception) {
            update(4, DiagStepResult("网络信息收集", DiagStepStatus.FAILED, "异常: ${e.message}"))
        }

        // Step 5: 端口监听检测
        update(5, DiagStepResult("端口监听检测 (7777)", DiagStepStatus.RUNNING, "检查 127.0.0.1:7777 是否在监听..."))
        try {
            delay(1000) // 等待端口绑定
            val portListening = checkPortListening("127.0.0.1", EasyTierManager.TERRARIA_PORT)
            if (portListening) {
                update(5, DiagStepResult(
                    "端口监听检测 (7777)",
                    DiagStepStatus.SUCCESS,
                    "端口 7777 已在监听",
                    "EasyTier 端口转发绑定成功，127.0.0.1:7777 可连接"
                ))
            } else {
                update(5, DiagStepResult(
                    "端口监听检测 (7777)",
                    DiagStepStatus.FAILED,
                    "端口 7777 未在监听",
                    "EasyTier 端口转发可能未生效。\n可能原因：\n1. no_tun 模式下端口转发不支持\n2. 配置格式错误\n3. EasyTier 内部错误"
                ))
            }
        } catch (e: Exception) {
            update(5, DiagStepResult("端口监听检测 (7777)", DiagStepStatus.FAILED, "异常: ${e.message}"))
        }

        // Step 6: 清理测试实例
        update(6, DiagStepResult("清理测试实例", DiagStepStatus.RUNNING, "停止测试实例..."))
        try {
            EasyTierJNI.stopAllInstances()
            delay(500)
            update(6, DiagStepResult("清理测试实例", DiagStepStatus.SUCCESS, "清理完成"))
        } catch (e: Exception) {
            update(6, DiagStepResult("清理测试实例", DiagStepStatus.FAILED, "清理失败: ${e.message}"))
        }

        Log.i(TAG, "Diagnostics complete. Results:")
        results.forEachIndexed { i, r ->
            Log.i(TAG, "  [$i] ${r.name}: ${r.status} - ${r.message}")
        }

        results
    }

    /**
     * 检查端口是否在监听
     */
    private fun checkPortListening(host: String, port: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 2000)
                true
            }
        } catch (e: Exception) {
            Log.d(TAG, "Port $host:$port not listening: ${e.message}")
            false
        }
    }
}
