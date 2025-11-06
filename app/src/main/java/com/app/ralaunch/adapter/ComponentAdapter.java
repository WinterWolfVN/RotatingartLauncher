package com.app.ralaunch.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.app.ralaunch.R;
import com.app.ralaunch.model.ComponentItem;
import java.util.List;

/**
 * 组件安装列表适配器
 * 
 * 用于在初始化界面显示需要安装的组件列表，包括：
 * - 组件名称和描述
 * - 安装进度条
 * - 安装状态图标
 * 
 * 支持实时更新组件安装进度和状态
 */
public class ComponentAdapter extends RecyclerView.Adapter<ComponentAdapter.ViewHolder> {
    private List<ComponentItem> componentList;

    public ComponentAdapter(List<ComponentItem> componentList) {
        this.componentList = componentList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_component, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ComponentItem component = componentList.get(position);

        holder.componentName.setText(component.getName());
        holder.componentDescription.setText(component.getDescription());
        holder.progressBar.setProgress(component.getProgress());

        if (component.isInstalled()) {
            // 已安装状态 - 显示绿色勾选图标
            holder.statusIcon.setImageResource(R.drawable.ic_check_circle);
            holder.statusIcon.setColorFilter(holder.itemView.getContext()
                    .getResources().getColor(R.color.accent_primary));
            holder.progressText.setText("已完成");
            holder.progressText.setTextColor(holder.itemView.getContext()
                    .getResources().getColor(R.color.accent_primary));
        } else {
            // 未安装状态 - 显示灰色图标
            holder.statusIcon.setImageResource(R.drawable.ic_check_circle);
            holder.statusIcon.setColorFilter(holder.itemView.getContext()
                    .getResources().getColor(R.color.text_hint));
            holder.progressText.setText(component.getProgress() + "%");
            holder.progressText.setTextColor(holder.itemView.getContext()
                    .getResources().getColor(R.color.accent_primary));
        }
    }

    @Override
    public int getItemCount() {
        return componentList.size();
    }

    public void updateComponent(int position, ComponentItem component) {
        componentList.set(position, component);
        notifyItemChanged(position);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView componentName;
        public TextView componentDescription;
        public ProgressBar progressBar;
        public TextView progressText;
        public ImageView statusIcon;

        public ViewHolder(View view) {
            super(view);
            componentName = view.findViewById(R.id.componentName);
            componentDescription = view.findViewById(R.id.componentDescription);
            progressBar = view.findViewById(R.id.progressBar);
            progressText = view.findViewById(R.id.progressText);
            statusIcon = view.findViewById(R.id.statusIcon);
        }
    }
}