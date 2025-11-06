package com.app.ralaunch.dialog;

import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.card.MaterialCardView;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.app.ralaunch.R;
import com.app.ralaunch.utils.RuntimeManager;

import java.util.List;

/**
 * 运行时版本选择对话框
 */
public class RuntimeSelectorDialog extends DialogFragment {

    private RecyclerView rvVersions;
    private TextView tvCurrentVersion;
    private Button btnConfirm;
    private Button btnCancel;
    private ImageButton btnClose;
    
    private List<String> versions;
    private String selectedVersion;
    private String currentVersion;
    private VersionAdapter adapter;
    
    private OnVersionSelectedListener listener;

    public interface OnVersionSelectedListener {
        void onVersionSelected(String version);
    }

    public void setOnVersionSelectedListener(OnVersionSelectedListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        return dialog;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, R.style.RuntimeDialogStyle);
    }

    @Override
    public void onStart() {
        super.onStart();
        // 设置对话框宽度为屏幕宽度的80%
        if (getDialog() != null && getDialog().getWindow() != null) {
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.8);
            getDialog().getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_runtime_selector, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 初始化视图
        rvVersions = view.findViewById(R.id.rvVersions);
        tvCurrentVersion = view.findViewById(R.id.tvCurrentVersion);
        btnConfirm = view.findViewById(R.id.btnConfirm);
        btnCancel = view.findViewById(R.id.btnCancel);
        btnClose = view.findViewById(R.id.btnClose);

        // 加载数据
        loadVersions();

        // 设置RecyclerView
        rvVersions.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new VersionAdapter(versions, currentVersion, this::onVersionItemClicked);
        rvVersions.setAdapter(adapter);

        // 显示当前版本
        tvCurrentVersion.setText(".NET " + currentVersion);
        android.util.Log.d("RuntimeDialog", "Dialog initialized with current version: " + currentVersion);

        // 设置按钮点击事件
        btnConfirm.setOnClickListener(v -> {
            android.util.Log.d("RuntimeDialog", "Confirm button clicked");
            onConfirmClicked();
        });
        btnCancel.setOnClickListener(v -> {
            android.util.Log.d("RuntimeDialog", "Cancel button clicked");
            dismiss();
        });
        btnClose.setOnClickListener(v -> {
            android.util.Log.d("RuntimeDialog", "Close button clicked");
            dismiss();
        });

        // 添加进入动画
        animateEnter(view);
    }

    private void loadVersions() {
        versions = RuntimeManager.listInstalledVersions(requireContext());
        currentVersion = RuntimeManager.getSelectedVersion(requireContext());
        selectedVersion = currentVersion;
        
        android.util.Log.d("RuntimeDialog", "Loaded versions: " + versions.size() + " total");
        android.util.Log.d("RuntimeDialog", "Current version: " + currentVersion);
        android.util.Log.d("RuntimeDialog", "Initially selected: " + selectedVersion);
        
        for (String v : versions) {
            android.util.Log.d("RuntimeDialog", "  Available: " + v);
        }
    }

    private void onVersionItemClicked(String version) {
        android.util.Log.d("RuntimeDialog", "Version clicked: " + version);
        selectedVersion = version;
        adapter.setSelectedVersion(version);
        
        // 立即应用更改
        if (!selectedVersion.equals(currentVersion)) {
            android.util.Log.d("RuntimeDialog", "Immediately switching to version: " + selectedVersion);
            RuntimeManager.setSelectedVersion(requireContext(), selectedVersion);
            
            // 验证是否保存成功
            String savedVersion = RuntimeManager.getSelectedVersion(requireContext());
            android.util.Log.d("RuntimeDialog", "Saved version: " + savedVersion);
            
            Toast.makeText(getContext(), "已切换到 .NET " + selectedVersion, Toast.LENGTH_SHORT).show();
            
            if (listener != null) {
                listener.onVersionSelected(selectedVersion);
            }
            
            // 延迟关闭对话框，让用户看到选中效果
            new android.os.Handler().postDelayed(() -> {
                if (isAdded() && getDialog() != null && getDialog().isShowing()) {
                    dismiss();
                }
            }, 300);
        } else {
            android.util.Log.d("RuntimeDialog", "Same version clicked, no change needed");
        }
    }

    private void onConfirmClicked() {
        android.util.Log.d("RuntimeDialog", "Confirm clicked - Selected: " + selectedVersion + ", Current: " + currentVersion);
        
        if (!selectedVersion.equals(currentVersion)) {
            android.util.Log.d("RuntimeDialog", "Switching to version: " + selectedVersion);
            RuntimeManager.setSelectedVersion(requireContext(), selectedVersion);
            
            // 验证是否保存成功
            String savedVersion = RuntimeManager.getSelectedVersion(requireContext());
            android.util.Log.d("RuntimeDialog", "Saved version: " + savedVersion);
            
            Toast.makeText(getContext(), "已切换到 .NET " + selectedVersion, Toast.LENGTH_SHORT).show();
            
            if (listener != null) {
                listener.onVersionSelected(selectedVersion);
            }
        } else {
            android.util.Log.d("RuntimeDialog", "No version change needed");
            Toast.makeText(getContext(), "当前已是 .NET " + selectedVersion, Toast.LENGTH_SHORT).show();
        }
        
        dismiss();
    }

    private void animateEnter(View view) {
        view.setAlpha(0f);
        view.setScaleX(0.8f);
        view.setScaleY(0.8f);
        
        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(300)
            .setInterpolator(new AccelerateDecelerateInterpolator())
            .start();
    }

    private void animateSelection() {
        // 添加微妙的脉冲动画
        ValueAnimator animator = ValueAnimator.ofFloat(1f, 1.05f, 1f);
        animator.setDuration(200);
        animator.addUpdateListener(animation -> {
            float scale = (float) animation.getAnimatedValue();
            rvVersions.setScaleX(scale);
            rvVersions.setScaleY(scale);
        });
        animator.start();
    }

    /**
     * 版本列表适配器
     */
    private static class VersionAdapter extends RecyclerView.Adapter<VersionAdapter.ViewHolder> {
        
        private final List<String> versions;
        private String selectedVersion;
        private final OnItemClickListener listener;

        interface OnItemClickListener {
            void onItemClick(String version);
        }

        VersionAdapter(List<String> versions, String selectedVersion, OnItemClickListener listener) {
            this.versions = versions;
            this.selectedVersion = selectedVersion;
            this.listener = listener;
        }

        void setSelectedVersion(String version) {
            this.selectedVersion = version;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_runtime_version, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String version = versions.get(position);
            holder.bind(version, version.equals(selectedVersion), listener);
        }

        @Override
        public int getItemCount() {
            return versions.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            private final MaterialCardView cardVersion;
            private final TextView tvVersionName;
            private final TextView tvVersionInfo;
            private final View ivSelected;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                cardVersion = itemView.findViewById(R.id.cardVersion);
                tvVersionName = itemView.findViewById(R.id.tvVersionName);
                tvVersionInfo = itemView.findViewById(R.id.tvVersionInfo);
                ivSelected = itemView.findViewById(R.id.ivSelected);
            }

            void bind(String version, boolean isSelected, OnItemClickListener listener) {
                android.util.Log.d("RuntimeDialog", "Binding version: " + version + ", isSelected: " + isSelected);
                
                tvVersionName.setText(".NET " + version);
                
                // 设置版本信息
                String info = getVersionInfo(version);
                tvVersionInfo.setText(info);
                
                // 设置选中状态
                ivSelected.setVisibility(isSelected ? View.VISIBLE : View.GONE);
                
                // 设置卡片样式
                Context context = itemView.getContext();
                if (isSelected) {
                    cardVersion.setStrokeColor(context.getColor(R.color.accent_primary));
                    cardVersion.setStrokeWidth(4);
                } else {
                    cardVersion.setStrokeColor(android.graphics.Color.TRANSPARENT);
                    cardVersion.setStrokeWidth(0);
                }

                // 点击事件
                itemView.setOnClickListener(v -> {
                    android.util.Log.d("RuntimeDialog", "Item clicked: " + version);
                    
                    // 添加点击动画
                    v.animate()
                        .scaleX(0.95f)
                        .scaleY(0.95f)
                        .setDuration(100)
                        .withEndAction(() -> {
                            v.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(100)
                                .start();
                        })
                        .start();
                    
                    android.util.Log.d("RuntimeDialog", "Calling listener.onItemClick for: " + version);
                    listener.onItemClick(version);
                });
            }

            private String getVersionInfo(String version) {
                // 根据版本号返回描述
                if (version.startsWith("10.")) {
                    return "最新版本 - 推荐使用";
                } else if (version.startsWith("9.")) {
                    return "稳定版本 - 推荐使用";
                } else if (version.startsWith("8.")) {
                    return "长期支持版本 (LTS)";
                } else if (version.startsWith("7.")) {
                    return "旧版本 - 兼容性好";
                } else {
                    return "兼容版本";
                }
            }
        }
    }
}

