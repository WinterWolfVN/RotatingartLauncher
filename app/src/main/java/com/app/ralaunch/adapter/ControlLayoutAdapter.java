package com.app.ralaunch.adapter;

import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.RecyclerView;
import com.app.ralaunch.R;
import com.app.ralaunch.model.ControlLayout;
import com.app.ralaunch.utils.ControlLayoutManager;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import java.util.List;

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
public class ControlLayoutAdapter extends RecyclerView.Adapter<ControlLayoutAdapter.ViewHolder> {

    private List<ControlLayout> layouts;
    private OnLayoutClickListener listener;
    private ControlLayoutManager layoutManager;
    private String defaultLayoutId;

    public interface OnLayoutClickListener {
        void onLayoutClick(ControlLayout layout);
        void onLayoutEdit(ControlLayout layout);
        void onLayoutRename(ControlLayout layout);
        void onLayoutDuplicate(ControlLayout layout);
        void onLayoutSetDefault(ControlLayout layout);
        void onLayoutExport(ControlLayout layout);
        void onLayoutDelete(ControlLayout layout);
    }

    public ControlLayoutAdapter(List<ControlLayout> layouts, OnLayoutClickListener listener) {
        this.layouts = layouts;
        this.listener = listener;
    }

    public void setDefaultLayoutId(String defaultLayoutId) {
        this.defaultLayoutId = defaultLayoutId;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_control_layout, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ControlLayout layout = layouts.get(position);
        boolean isDefault = layout.getName().equals(defaultLayoutId);

        // 获取显示名称（如果是默认布局，显示本地化名称）
        String displayName = getDisplayName(layout.getName(), holder.itemView.getContext());
        holder.layoutName.setText(displayName);
        holder.layoutInfo.setText(holder.itemView.getContext().getString(R.string.control_count, layout.getElements().size()));

        // 显示或隐藏默认标识
        holder.defaultChip.setVisibility(isDefault ? View.VISIBLE : View.GONE);

        // 点击卡片进入编辑
        holder.layoutCard.setOnClickListener(v -> {
            if (listener != null) {
                listener.onLayoutEdit(layout);
            }
        });

        // 菜单按钮
        holder.btnMenu.setOnClickListener(v -> {
            showPopupMenu(holder.btnMenu, layout, isDefault);
        });
    }

    private void showPopupMenu(View anchor, ControlLayout layout, boolean isDefault) {
        PopupMenu popupMenu = new PopupMenu(anchor.getContext(), anchor);
        popupMenu.inflate(R.menu.menu_control_layout_item);

        // 如果已经是默认布局，隐藏"设为默认"选项
        MenuItem defaultItem = popupMenu.getMenu().findItem(R.id.action_set_default);
        if (defaultItem != null && isDefault) {
            defaultItem.setVisible(false);
        }

        popupMenu.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (listener == null) return false;

            if (itemId == R.id.action_edit) {
                listener.onLayoutEdit(layout);
                return true;
            } else if (itemId == R.id.action_rename) {
                listener.onLayoutRename(layout);
                return true;
            } else if (itemId == R.id.action_duplicate) {
                listener.onLayoutDuplicate(layout);
                return true;
            } else if (itemId == R.id.action_set_default) {
                listener.onLayoutSetDefault(layout);
                return true;
            } else if (itemId == R.id.action_export) {
                listener.onLayoutExport(layout);
                return true;
            } else if (itemId == R.id.action_delete) {
                listener.onLayoutDelete(layout);
                return true;
            }
            return false;
        });

        popupMenu.show();
    }

    @Override
    public int getItemCount() {
        return layouts.size();
    }

    public void updateLayouts(List<ControlLayout> newLayouts) {
        this.layouts = newLayouts;
        notifyDataSetChanged();
    }

    /**
     * 获取布局的显示名称（用于UI显示）
     * 如果是默认布局，返回本地化的名称；否则返回原始名称
     */
    private String getDisplayName(String layoutName, android.content.Context context) {
        if ("keyboard_mode".equals(layoutName)) {
            return context.getString(R.string.control_layout_keyboard_mode);
        } else if ("gamepad_mode".equals(layoutName)) {
            return context.getString(R.string.control_layout_gamepad_mode);
        } else {
            return layoutName;
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView layoutCard;
        TextView layoutName;
        TextView layoutInfo;
        Chip defaultChip;
        ImageButton btnMenu;

        ViewHolder(View itemView) {
            super(itemView);
            layoutCard = itemView.findViewById(R.id.layout_card);
            layoutName = itemView.findViewById(R.id.layout_name);
            layoutInfo = itemView.findViewById(R.id.layout_info);
            defaultChip = itemView.findViewById(R.id.default_chip);
            btnMenu = itemView.findViewById(R.id.btn_menu);
        }
    }
}