package com.app.ralaunch.manager.common;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * 选中视图管理器
 * 统一管理选中信息的显示和隐藏状态
 */
public class SelectionViewManager {
    private View selectedInfoView;
    private ImageView selectedImageView;
    private TextView selectedNameView;
    private TextView selectedDescriptionView;
    private View emptyTextView;
    
    public SelectionViewManager(View infoView, ImageView imageView, 
                                TextView nameView, TextView descriptionView, 
                                View emptyView) {
        this.selectedInfoView = infoView;
        this.selectedImageView = imageView;
        this.selectedNameView = nameView;
        this.selectedDescriptionView = descriptionView;
        this.emptyTextView = emptyView;
    }
    
    /**
     * 显示选中信息
     */
    public void showSelection(String name, String description) {
        if (selectedInfoView != null) {
            selectedInfoView.setVisibility(View.VISIBLE);
        }
        if (emptyTextView != null) {
            emptyTextView.setVisibility(View.GONE);
        }
        if (selectedNameView != null) {
            selectedNameView.setText(name);
        }
        if (selectedDescriptionView != null) {
            selectedDescriptionView.setText(description);
        }
    }
    
    /**
     * 隐藏选中信息，显示空状态
     */
    public void showEmpty() {
        if (selectedInfoView != null) {
            selectedInfoView.setVisibility(View.GONE);
        }
        if (emptyTextView != null) {
            emptyTextView.setVisibility(View.VISIBLE);
        }
    }
    
    /**
     * 获取图标视图
     */
    public ImageView getImageView() {
        return selectedImageView;
    }
    
    /**
     * 获取名称视图
     */
    public TextView getNameView() {
        return selectedNameView;
    }
    
    /**
     * 获取描述视图
     */
    public TextView getDescriptionView() {
        return selectedDescriptionView;
    }
    
    /**
     * 获取信息视图
     */
    public View getInfoView() {
        return selectedInfoView;
    }
    
    /**
     * 检查是否显示选中信息
     */
    public boolean isSelectionVisible() {
        return selectedInfoView != null && selectedInfoView.getVisibility() == View.VISIBLE;
    }
}

