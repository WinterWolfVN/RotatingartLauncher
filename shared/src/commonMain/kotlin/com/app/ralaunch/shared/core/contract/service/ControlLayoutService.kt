package com.app.ralaunch.shared.core.contract.service

import com.app.ralaunch.shared.core.model.domain.ControlLayout
import kotlinx.coroutines.flow.Flow

/**
 * 控制布局包信息
 */
data class ControlPackInfo(
    val id: String,
    val name: String,
    val author: String = "",
    val description: String = "",
    val version: String = "1.0.0",
    val iconPath: String? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)

/**
 * 控制布局服务接口
 * 
 * 管理控制布局包的安装、加载、保存等操作
 */
interface ControlLayoutService {
    
    /**
     * 已安装的控制布局包列表
     */
    val installedPacks: Flow<List<ControlPackInfo>>
    
    /**
     * 当前选中的布局包 ID
     */
    val selectedPackId: Flow<String?>
    
    /**
     * 获取所有已安装的布局包
     */
    suspend fun getInstalledPacks(): List<ControlPackInfo>
    
    /**
     * 获取布局包信息
     * @param packId 布局包 ID
     */
    suspend fun getPackInfo(packId: String): ControlPackInfo?
    
    /**
     * 获取布局包的控制布局
     * @param packId 布局包 ID
     */
    suspend fun getPackLayout(packId: String): ControlLayout?
    
    /**
     * 获取当前选中的布局
     */
    suspend fun getCurrentLayout(): ControlLayout?
    
    /**
     * 设置当前选中的布局包
     * @param packId 布局包 ID
     */
    suspend fun setSelectedPack(packId: String)
    
    /**
     * 创建新的布局包
     * @param name 名称
     * @param author 作者
     * @param description 描述
     * @return 新创建的布局包信息
     */
    suspend fun createPack(
        name: String,
        author: String = "",
        description: String = ""
    ): ControlPackInfo
    
    /**
     * 保存布局到指定包
     * @param packId 布局包 ID
     * @param layout 布局配置
     */
    suspend fun savePackLayout(packId: String, layout: ControlLayout)
    
    /**
     * 删除布局包
     * @param packId 布局包 ID
     * @return 是否删除成功
     */
    suspend fun deletePack(packId: String): Boolean
    
    /**
     * 复制布局包
     * @param packId 源布局包 ID
     * @param newName 新名称
     * @return 新布局包信息
     */
    suspend fun duplicatePack(packId: String, newName: String): ControlPackInfo?
    
    /**
     * 重命名布局包
     * @param packId 布局包 ID
     * @param newName 新名称
     */
    suspend fun renamePack(packId: String, newName: String): Boolean
    
    /**
     * 从文件导入布局包
     * @param filePath 文件路径
     * @return 导入的布局包信息
     */
    suspend fun importPack(filePath: String): Result<ControlPackInfo>
    
    /**
     * 导出布局包到文件
     * @param packId 布局包 ID
     * @param outputPath 输出路径
     * @return 导出的文件路径
     */
    suspend fun exportPack(packId: String, outputPath: String): Result<String>
    
    /**
     * 获取布局包的图标路径
     * @param packId 布局包 ID
     */
    suspend fun getPackIconPath(packId: String): String?
    
    /**
     * 获取布局包的纹理资源目录
     * @param packId 布局包 ID
     */
    suspend fun getPackAssetsDir(packId: String): String?
}
