package com.app.ralaunch.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.app.ralaunch.R;
import com.app.ralaunch.model.ComponentItem;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import java.util.List;

/**
 * 组件安装列表适配器
 * 
 * 用于在初始化界面显示需要安装的组件列表，包括：
 * - 组件名称和描述
 * - 安装进度条
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

        // 统一显示进度指示器
        holder.progressBar.setVisibility(View.VISIBLE);
        
        if (component.isInstalled() || component.getProgress() >= 100) {
            // 已完成，显示 100% 进度
            holder.progressBar.setIndeterminate(false);
            holder.progressBar.setProgress(100);
        } else if (component.getProgress() > 0) {
            // 正在安装，显示不确定进度（旋转动画）
            holder.progressBar.setIndeterminate(true);
        } else {
            // 未开始，显示 0% 进度
            holder.progressBar.setIndeterminate(false);
            holder.progressBar.setProgress(0);
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
        public CircularProgressIndicator progressBar;

        public ViewHolder(View view) {
            super(view);
            componentName = view.findViewById(R.id.componentName);
            componentDescription = view.findViewById(R.id.componentDescription);
            progressBar = view.findViewById(R.id.progressBar);
        }
    }
}