package com.app.ralaunch.controls.editor.manager;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.app.ralaunch.R;
import com.app.ralaunch.controls.ControlData;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * 控件形状管理器
 * 统一管理控件形状的选择和显示逻辑
 */
public class ControlShapeManager {

    /**
     * 获取形状显示名称
     */
    public static String getShapeDisplayName(Context context, ControlData data) {
        if (data == null) {
            return context.getString(R.string.control_shape_rectangle);
        }
        
        if (data.shape == ControlData.SHAPE_CIRCLE) {
            return context.getString(R.string.control_shape_circle);
        } else {
            return context.getString(R.string.control_shape_rectangle);
        }
    }

    /**
     * 更新形状显示
     */
    public static void updateShapeDisplay(Context context, ControlData data, TextView textView, View shapeItemView) {
        if (data == null) {
            return;
        }
        
        // 按钮和文本控件都支持形状选择
        boolean supportsShape = (data.type == ControlData.TYPE_BUTTON || data.type == ControlData.TYPE_TEXT);
        
        if (supportsShape) {
            if (shapeItemView != null) {
                shapeItemView.setVisibility(View.VISIBLE);
            }
            if (textView != null) {
                String shapeName = getShapeDisplayName(context, data);
                textView.setText(shapeName);
            }
        } else {
            if (shapeItemView != null) {
                shapeItemView.setVisibility(View.GONE);
            }
        }
    }

    /**
     * 显示形状选择对话框
     */
    public static void showShapeSelectDialog(@NonNull Context context,
                                           ControlData data,
                                           OnShapeSelectedListener listener) {
        if (data == null) {
            return;
        }
        
        // 按钮和文本控件都支持形状选择
        boolean supportsShape = (data.type == ControlData.TYPE_BUTTON || data.type == ControlData.TYPE_TEXT);
        if (!supportsShape) {
            return;
        }

        String[] shapes = {
            context.getString(R.string.control_shape_rectangle),
            context.getString(R.string.control_shape_circle)
        };
        int currentIndex = (data.shape == ControlData.SHAPE_CIRCLE) ? 1 : 0;

        new MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.control_select_shape))
            .setSingleChoiceItems(shapes, currentIndex, (dialog, which) -> {
                data.shape = which;
                
                if (listener != null) {
                    listener.onShapeSelected(data);
                }
                
                dialog.dismiss();
            })
            .setNegativeButton(context.getString(R.string.cancel), null)
            .show();
    }

    /**
     * 形状选择监听器
     */
    public interface OnShapeSelectedListener {
        void onShapeSelected(ControlData data);
    }
}

