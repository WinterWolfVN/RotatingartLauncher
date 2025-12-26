package com.app.ralaunch.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.app.ralaunch.R
import com.app.ralaunch.controls.configs.ControlConfig
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip

/**
 * 控制布局列表适配器
 *
 * 用于控制布局管理界面显示布局列表，提供：
 * - 布局名称显示
 * - 布局点击编辑
 * - 编辑、重命名、复制布局
 * - 设置默认布局
 * - 导出和删除布局
 *
 * 采用 Material 3 设计，使用 PopupMenu 替代显式按钮
 */
class ControlLayoutAdapter(
    private var layouts: List<ControlConfig>,
    private val listener: OnLayoutClickListener?
) : RecyclerView.Adapter<ControlLayoutAdapter.ViewHolder>() {

    private var defaultLayoutId: String? = null

    interface OnLayoutClickListener {
        fun onLayoutClick(layout: ControlConfig)
        fun onLayoutEdit(layout: ControlConfig)
        fun onLayoutRename(layout: ControlConfig)
        fun onLayoutDuplicate(layout: ControlConfig)
        fun onLayoutSetDefault(layout: ControlConfig)
        fun onLayoutExport(layout: ControlConfig)
        fun onLayoutDelete(layout: ControlConfig)
    }

    fun setDefaultLayoutId(defaultLayoutId: String?) {
        this.defaultLayoutId = defaultLayoutId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_control_layout, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val layout = layouts[position]
        val isDefault = layout.id == defaultLayoutId

        // 获取显示名称（如果是默认布局，显示本地化名称）
        val displayName = getDisplayName(layout.name, holder.itemView.context)
        holder.layoutName.text = displayName
        holder.layoutInfo.text = holder.itemView.context.getString(
            R.string.control_count,
            layout.controls.size
        )

        // 显示或隐藏默认标识
        holder.defaultChip.visibility = if (isDefault) View.VISIBLE else View.GONE

        // 点击卡片进入编辑
        holder.layoutCard.setOnClickListener {
            listener?.onLayoutEdit(layout)
        }

        // 菜单按钮
        holder.btnMenu.setOnClickListener {
            showPopupMenu(holder.btnMenu, layout, isDefault)
        }
    }

    private fun showPopupMenu(anchor: View, layout: ControlConfig, isDefault: Boolean) {
        val popupMenu = PopupMenu(anchor.context, anchor)
        popupMenu.inflate(R.menu.menu_control_layout_item)

        // 如果已经是默认布局，隐藏"设为默认"选项
        val defaultItem = popupMenu.menu.findItem(R.id.action_set_default)
        if (defaultItem != null && isDefault) {
            defaultItem.isVisible = false
        }

        popupMenu.setOnMenuItemClickListener { item: MenuItem ->
            listener?.let {
                when (item.itemId) {
                    R.id.action_edit -> {
                        it.onLayoutEdit(layout)
                        true
                    }
                    R.id.action_rename -> {
                        it.onLayoutRename(layout)
                        true
                    }
                    R.id.action_duplicate -> {
                        it.onLayoutDuplicate(layout)
                        true
                    }
                    R.id.action_set_default -> {
                        it.onLayoutSetDefault(layout)
                        true
                    }
                    R.id.action_export -> {
                        it.onLayoutExport(layout)
                        true
                    }
                    R.id.action_delete -> {
                        it.onLayoutDelete(layout)
                        true
                    }
                    else -> false
                }
            } ?: false
        }

        popupMenu.show()
    }

    override fun getItemCount(): Int = layouts.size

    fun updateLayouts(newLayouts: List<ControlConfig>) {
        this.layouts = newLayouts
        notifyDataSetChanged()
    }

    /**
     * 获取布局的显示名称（用于UI显示）
     * 如果是默认布局，返回本地化的名称；否则返回原始名称
     */
    private fun getDisplayName(layoutName: String, context: Context): String {
        return when (layoutName) {
            "keyboard_mode" -> context.getString(R.string.control_layout_keyboard_mode)
            "gamepad_mode" -> context.getString(R.string.control_layout_gamepad_mode)
            else -> layoutName
        }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val layoutCard: MaterialCardView = itemView.findViewById(R.id.layout_card)
        val layoutName: TextView = itemView.findViewById(R.id.layout_name)
        val layoutInfo: TextView = itemView.findViewById(R.id.layout_info)
        val defaultChip: Chip = itemView.findViewById(R.id.default_chip)
        val btnMenu: ImageButton = itemView.findViewById(R.id.btn_menu)
    }
}