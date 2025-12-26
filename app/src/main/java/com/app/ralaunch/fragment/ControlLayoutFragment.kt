package com.app.ralaunch.fragment

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.app.ralaunch.R
import com.app.ralaunch.RaLaunchApplication
import com.app.ralaunch.adapter.ControlLayoutAdapter
import com.app.ralaunch.adapter.ControlLayoutAdapter.OnLayoutClickListener
import com.app.ralaunch.controls.configs.ControlConfig
import com.app.ralaunch.controls.configs.ControlConfig.Companion.loadFromJson
import com.app.ralaunch.controls.configs.ControlConfigManager
import com.app.ralaunch.controls.editors.ControlEditorActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * 控制布局管理Fragment
 *
 * 提供控制布局的管理界面（Material 3 风格）：
 * - 显示所有保存的控制布局
 * - 创建新的控制布局
 * - 编辑、重命名、复制布局
 * - 设置默认布局
 * - 导出和删除布局
 * - 跳转到布局编辑器
 *
 * 使用 ControlConfigManager 管理布局数据
 */
class ControlLayoutFragment : Fragment(), OnLayoutClickListener {
    private val configManager: ControlConfigManager
        get() = RaLaunchApplication.getControlConfigManager()
    private var layouts: List<ControlConfig> = configManager.loadAllConfigs()
    private var adapter: ControlLayoutAdapter? = null
    private var recyclerView: RecyclerView? = null
    private var emptyState: LinearLayout? = null
    private var mExportingLayout: ControlConfig? = null // 保存要导出的布局

    private var backListener: OnControlLayoutBackListener? = null

    interface OnControlLayoutBackListener {
        fun onControlLayoutBack()
    }

    fun setOnControlLayoutBackListener(listener: OnControlLayoutBackListener?) {
        this.backListener = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_control_layout, container, false)

        initUI(view)
        setupRecyclerView()

        return view
    }

    private fun initUI(view: View) {
        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        recyclerView = view.findViewById<RecyclerView>(R.id.recyclerView)
        val fabAddLayout = view.findViewById<ExtendedFloatingActionButton>(R.id.fabAddLayout)
        val btnImportLayout = view.findViewById<MaterialButton>(R.id.btnImportLayout)
        val btnImportPreset = view.findViewById<MaterialButton>(R.id.btnImportPreset)
        emptyState = view.findViewById<LinearLayout>(R.id.emptyState)

        toolbar.setNavigationOnClickListener { v: View? ->
            if (backListener != null) {
                backListener!!.onControlLayoutBack()
            }
        }

        fabAddLayout.setOnClickListener { v: View? -> showAddLayoutDialog() }


        // 导入布局按钮
        btnImportLayout.setOnClickListener { v: View? -> importLayoutFromFile() }


        // 导入预设按钮
        btnImportPreset.setOnClickListener { v: View? -> showImportPresetDialog() }


        updateEmptyState()
    }

    private fun setupRecyclerView() {
        adapter = ControlLayoutAdapter(layouts, this)
        adapter!!.setDefaultLayoutId(configManager.getSelectedConfigId())
        recyclerView!!.setLayoutManager(LinearLayoutManager(context))
        recyclerView!!.setAdapter(adapter)
    }

    private fun showAddLayoutDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_new_layout, null)
        val editText = dialogView.findViewById<EditText>(R.id.layout_name_edit)

        // 设置默认名称
        val defaultName = getString(R.string.control_new_layout)

        // 如果名称已存在，添加数字后缀
        var finalName = defaultName
        var counter = 1
        while (layoutExists(finalName)) {
            counter++
            finalName = "$defaultName $counter"
        }

        editText.setText(finalName)
        editText.selectAll()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.control_create_layout))
            .setView(dialogView)
            .setPositiveButton(
                getString(R.string.control_create)
            ) { dialog: DialogInterface?, which: Int ->
                val layoutName = editText.text.toString().trim()
                if (!layoutName.isEmpty()) {
                    createNewLayout(layoutName)
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun createNewLayout(name: String) {
        // 检查名称是否已存在
        for (layout in layouts) {
            if (layout.name == name) {
                Toast.makeText(
                    context,
                    getString(R.string.control_layout_name_exists),
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
        }

        val newConfig = ControlConfig()
        newConfig.name = name
        configManager.saveConfig(newConfig)

        // 如果当前没有选中的布局，将新布局设为默认
        if (configManager.getSelectedConfigId() == null) {
            configManager.setSelectedConfigId(newConfig.id)
        }

        layouts = configManager.loadAllConfigs()
        adapter!!.updateLayouts(layouts)
        adapter!!.setDefaultLayoutId(configManager.getSelectedConfigId())
        updateEmptyState()

        // 打开编辑界面
        openLayoutEditor(newConfig)
    }

    private fun openLayoutEditor(config: ControlConfig) {
        ControlEditorActivity.start(requireContext(), config.id)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE_EDIT_LAYOUT && resultCode == Activity.RESULT_OK) {
            if (data != null && data.getBooleanExtra("return_to_main", false)) {
                // 编辑器请求返回主界面，关闭当前Fragment
                if (backListener != null) {
                    backListener!!.onControlLayoutBack()
                }
            }
        } else if (requestCode == REQUEST_CODE_EXPORT_LAYOUT && resultCode == Activity.RESULT_OK) {
            // 处理导出布局
            if (data != null && data.data != null && mExportingLayout != null) {
                exportLayoutToFile(data.data!!, mExportingLayout!!)
                mExportingLayout = null // 清除引用
            }
        } else if (requestCode == REQUEST_CODE_IMPORT_LAYOUT && resultCode == Activity.RESULT_OK) {
            // 处理导入布局
            if (data != null && data.data != null) {
                importLayoutFromUri(data.data!!)
            }
        }
    }

    /**
     * 将布局导出到文件
     */
    private fun exportLayoutToFile(uri: Uri, layout: ControlConfig) {
        try {
            // Convert ControlConfig to JSON directly
            val json = layout.toJson()

            // 写入文件
            val outputStream = requireContext().contentResolver.openOutputStream(uri)
            if (outputStream != null) {
                outputStream.write(json.toByteArray(StandardCharsets.UTF_8))
                outputStream.close()
                Toast.makeText(
                    context,
                    getString(R.string.control_export_success),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    context,
                    getString(R.string.control_export_failed_write),
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            Toast.makeText(
                context,
                getString(R.string.control_export_failed, e.message),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onResume() {
        super.onResume()
        // 从编辑器返回时刷新列表
        layouts = configManager.loadAllConfigs()
        adapter?.updateLayouts(layouts)
        // Update the default layout ID to ensure isDefault flags are correct
        adapter?.setDefaultLayoutId(configManager.getSelectedConfigId())
    }

    private fun updateEmptyState() {
        if (layouts.isEmpty()) {
            emptyState!!.visibility = View.VISIBLE
            recyclerView!!.visibility = View.GONE
        } else {
            emptyState!!.visibility = View.GONE
            recyclerView!!.visibility = View.VISIBLE
        }
    }

    override fun onLayoutClick(layout: ControlConfig) {
        openLayoutEditor(layout)
    }

    override fun onLayoutEdit(layout: ControlConfig) {
        openLayoutEditor(layout)
    }

    override fun onLayoutRename(layout: ControlConfig) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_new_layout, null)
        val editText = dialogView.findViewById<EditText>(R.id.layout_name_edit)
        editText.setText(layout.name)
        editText.selectAll()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.control_rename_layout))
            .setView(dialogView)
            .setPositiveButton(
                getString(R.string.ok)
            ) { dialog: DialogInterface?, which: Int ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty() && newName != layout.name) {
                    // 检查名称是否已存在
                    for (l in layouts) {
                        if (l.name == newName) {
                            Toast.makeText(
                                context,
                                getString(R.string.control_layout_name_exists),
                                Toast.LENGTH_SHORT
                            ).show()
                            return@setPositiveButton
                        }
                    }
                    layout.name = newName
                    configManager.saveConfig(layout)
                    layouts = configManager.loadAllConfigs()
                    adapter!!.updateLayouts(layouts)
                    adapter!!.setDefaultLayoutId(configManager.getSelectedConfigId())
                    Toast.makeText(
                        context,
                        getString(R.string.control_layout_renamed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    override fun onLayoutDuplicate(layout: ControlConfig) {
        var newName = layout.name + " " + getString(R.string.control_layout_copy_suffix)
        var counter = 1
        while (layoutExists(newName)) {
            counter++
            newName =
                layout.name + " " + getString(R.string.control_layout_copy_suffix_numbered, counter)
        }

        val duplicate = layout.deepCopy()
        duplicate.name = newName

        configManager.saveConfig(duplicate)
        layouts = configManager.loadAllConfigs()
        adapter!!.updateLayouts(layouts)
        adapter!!.setDefaultLayoutId(configManager.getSelectedConfigId())
        updateEmptyState()
        Toast.makeText(
            context,
            getString(R.string.control_layout_duplicated),
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onLayoutSetDefault(layout: ControlConfig) {
        configManager.setSelectedConfigId(layout.id)
        adapter!!.setDefaultLayoutId(layout.id)
        Toast.makeText(context, getString(R.string.control_set_as_default), Toast.LENGTH_SHORT)
            .show()
    }

    override fun onLayoutExport(layout: ControlConfig) {
        try {
            // 保存要导出的布局
            mExportingLayout = layout

            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "application/json"
            intent.putExtra(Intent.EXTRA_TITLE, layout.name + ".json")
            startActivityForResult(intent, REQUEST_CODE_EXPORT_LAYOUT)
        } catch (e: Exception) {
            Toast.makeText(
                context,
                getString(R.string.control_export_failed, e.message),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onLayoutDelete(layout: ControlConfig) {
        val layoutName = layout.name


        // 检查是否是默认布局，给出警告但允许删除
        val isDefaultLayout = getString(R.string.control_layout_keyboard_mode) == layoutName ||
                getString(R.string.control_layout_gamepad_mode) == layoutName
        val message = if (isDefaultLayout)
            getString(R.string.control_delete_default_layout_confirm, layoutName)
        else
            getString(R.string.control_delete_layout_confirm, layoutName)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.control_delete_layout))
            .setMessage(message)
            .setPositiveButton(
                getString(R.string.control_delete)
            ) { _, _ ->
                // Delete the config file
                val deleted = configManager.deleteConfig(layout.id)

                if (deleted) {
                    Toast.makeText(
                        context,
                        getString(R.string.control_layout_deleted),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        context,
                        getString(R.string.control_export_failed_write),
                        Toast.LENGTH_SHORT
                    ).show()
                }

                // Reload layouts
                layouts = configManager.loadAllConfigs()
                adapter!!.updateLayouts(layouts)
                // Update the default layout ID in case it was cleared
                adapter!!.setDefaultLayoutId(configManager.getSelectedConfigId())
                updateEmptyState()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun layoutExists(name: String?): Boolean {
        for (layout in layouts) {
            if (layout.name == name) {
                return true
            }
        }
        return false
    }

    /**
     * 导入布局（从文件选择器）
     */
    private fun importLayoutFromFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "application/json"
        intent.putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "text/json"))
        startActivityForResult(intent, REQUEST_CODE_IMPORT_LAYOUT)
    }

    /**
     * 从URI导入布局
     */
    private fun importLayoutFromUri(uri: Uri) {
        try {
            // 读取文件内容
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Toast.makeText(
                    context,
                    getString(R.string.control_cannot_read_file),
                    Toast.LENGTH_SHORT
                ).show()
                return
            }

            val reader = BufferedReader(InputStreamReader(inputStream))
            val jsonBuilder = StringBuilder()
            var line: String?
            while ((reader.readLine().also { line = it }) != null) {
                jsonBuilder.append(line).append("\n")
            }
            reader.close()
            inputStream.close()

            val json = jsonBuilder.toString()

            // 解析 JSON 配置
            val config = loadFromJson(json)

            if (config == null || config.controls.isEmpty()) {
                Toast.makeText(
                    context,
                    getString(R.string.control_layout_file_invalid),
                    Toast.LENGTH_SHORT
                ).show()
                return
            }

            // 生成唯一的布局名称
            var layoutName = config.name
            var counter = 1
            while (layoutExists(layoutName)) {
                counter++
                layoutName = config.name + " " + counter
            }

            // 设置新的ID和名称
            config.id = System.currentTimeMillis().toString() + ""
            config.name = layoutName

            // 保存布局
            configManager.saveConfig(config)
            layouts = configManager.loadAllConfigs()
            adapter!!.updateLayouts(layouts)
            adapter!!.setDefaultLayoutId(configManager.getSelectedConfigId())
            updateEmptyState()

            Toast.makeText(
                context,
                getString(R.string.control_import_success, layoutName),
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            Toast.makeText(
                context,
                getString(R.string.control_import_failed, e.message),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * 显示导入预设配置对话框
     */
    private fun showImportPresetDialog() {
        val presetNames = arrayOf<String?>(
            getString(R.string.control_layout_preset_keyboard),
            getString(R.string.control_layout_preset_gamepad)
        )
        val presetFiles = arrayOf<String?>("default_layout.json", "gamepad_layout.json")

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.control_select_preset))
            .setItems(
                presetNames
            ) { dialog: DialogInterface?, which: Int ->
                importPresetLayout(
                    presetFiles[which],
                    presetNames[which]!!
                )
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    /**
     * 导入预设配置文件
     */
    private fun importPresetLayout(fileName: String?, presetName: String) {
        try {
            // 从 assets 读取配置文件
            val `is` = requireContext().assets.open("controls/$fileName")
            val buffer = ByteArray(`is`.available())
            val bytesRead = `is`.read(buffer)
            `is`.close()

            if (bytesRead <= 0) {
                Toast.makeText(
                    context,
                    getString(R.string.control_preset_file_invalid),
                    Toast.LENGTH_SHORT
                ).show()
                return
            }

            val json = String(buffer, StandardCharsets.UTF_8)

            // 解析 JSON 配置 - 直接使用 ControlConfig
            val config = loadFromJson(json)

            if (config == null || config.controls.isEmpty()) {
                Toast.makeText(
                    context,
                    getString(R.string.control_preset_file_invalid),
                    Toast.LENGTH_SHORT
                ).show()
                return
            }

            // 生成唯一的布局名称
            var layoutName = presetName
            var counter = 1
            while (layoutExists(layoutName)) {
                counter++
                layoutName = "$presetName $counter"
            }

            // 设置新的ID和名称
            config.id = System.currentTimeMillis().toString() + ""
            config.name = layoutName

            // 保存布局
            configManager.saveConfig(config)
            layouts = configManager.loadAllConfigs()
            adapter!!.updateLayouts(layouts)
            adapter!!.setDefaultLayoutId(configManager.getSelectedConfigId())
            updateEmptyState()

            Toast.makeText(
                context,
                getString(R.string.control_preset_imported, layoutName),
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            Toast.makeText(
                context,
                getString(R.string.control_import_failed, e.message),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    companion object {
        private const val REQUEST_CODE_EDIT_LAYOUT = 1001
        private const val REQUEST_CODE_EXPORT_LAYOUT = 1002
        private const val REQUEST_CODE_IMPORT_LAYOUT = 1003
    }
}