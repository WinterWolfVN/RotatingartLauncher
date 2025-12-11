package com.app.ralaunch.gog;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.app.ralaunch.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

/**
 * GOG 下载进度对话框 - MD3 横屏设计
 * 展示游戏本体和 ModLoader 的下载进度
 */
public class GogDownloadProgressDialog extends DialogFragment {
    private static final String ARG_GAME_FILE_NAME = "game_file_name";
    private static final String ARG_MODLOADER_NAME = "modloader_name";

    private TextView tvTitle;
    private TextView tvGameLabel;
    private TextView tvGameStatus;
    private TextView tvGameProgress;
    private LinearProgressIndicator progressGame;
    private TextView tvModLoaderLabel;
    private TextView tvModLoaderStatus;
    private TextView tvModLoaderProgress;
    private LinearProgressIndicator progressModLoader;
    private MaterialButton btnCancel;
    private MaterialButton btnHide;

    private OnCancelListener cancelListener;

    public interface OnCancelListener {
        void onCancel();
    }

    public static GogDownloadProgressDialog newInstance(String gameFileName, String modLoaderName) {
        GogDownloadProgressDialog dialog = new GogDownloadProgressDialog();
        Bundle args = new Bundle();
        args.putString(ARG_GAME_FILE_NAME, gameFileName);
        args.putString(ARG_MODLOADER_NAME, modLoaderName);
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 使用自定义的 GOG 对话框样式（透明背景）
        setStyle(DialogFragment.STYLE_NORMAL, R.style.GogDialogStyle);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        // 创建对话框时禁用返回键和外部点击
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.setCanceledOnTouchOutside(false);
        return dialog;
    }
    
    @Override
    public void onStart() {
        super.onStart();
        // 只移除窗口装饰和内边距，不设置窗口大小（由 XML 控制）
        if (getDialog() != null && getDialog().getWindow() != null) {
            android.view.Window window = getDialog().getWindow();
            // 移除窗口装饰视图的所有内边距
            android.view.View decorView = window.getDecorView();
            decorView.setPadding(0, 0, 0, 0);
            // 设置窗口背景为完全透明
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
    }
 
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_gog_download_progress, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 初始化视图
        tvTitle = view.findViewById(R.id.tvTitle);
        tvGameLabel = view.findViewById(R.id.tvGameLabel);
        tvGameStatus = view.findViewById(R.id.tvGameStatus);
        tvGameProgress = view.findViewById(R.id.tvGameProgress);
        progressGame = view.findViewById(R.id.progressGame);
        tvModLoaderLabel = view.findViewById(R.id.tvModLoaderLabel);
        tvModLoaderStatus = view.findViewById(R.id.tvModLoaderStatus);
        tvModLoaderProgress = view.findViewById(R.id.tvModLoaderProgress);
        progressModLoader = view.findViewById(R.id.progressModLoader);
        btnCancel = view.findViewById(R.id.btnCancel);
        btnHide = view.findViewById(R.id.btnHide);

        // 设置标题和标签
        if (getArguments() != null) {
            String gameFileName = getArguments().getString(ARG_GAME_FILE_NAME);
            String modLoaderName = getArguments().getString(ARG_MODLOADER_NAME);
            
            tvTitle.setText("下载游戏");
            if (tvGameLabel != null && gameFileName != null) {
                tvGameLabel.setText(gameFileName);
            }
            if (tvModLoaderLabel != null && modLoaderName != null) {
                tvModLoaderLabel.setText(modLoaderName);
            }
        }

        // 设置取消按钮
        btnCancel.setOnClickListener(v -> {
            if (cancelListener != null) {
                cancelListener.onCancel();
            }
            dismiss();
        });

        // 后台运行按钮（暂时隐藏）
        btnHide.setOnClickListener(v -> dismiss());
    }

    public void setOnCancelListener(OnCancelListener listener) {
        this.cancelListener = listener;
    }

    /**
     * 更新游戏下载进度
     */
    public void updateGameDownloadProgress(long downloaded, long total, String status) {
        if (getActivity() == null || !isAdded()) return;
        getActivity().runOnUiThread(() -> {
            if (total > 0) {
                int progress = (int) (downloaded * 100 / total);
                progressGame.setProgressCompat(progress, true);
                tvGameProgress.setText(progress + "%");
            } else {
                tvGameProgress.setText(formatBytes(downloaded));
            }
            tvGameStatus.setText(status);
        });
    }

    /**
     * 设置游戏下载状态
     */
    public void setGameDownloadStatus(String status) {
        if (getActivity() == null || !isAdded()) return;
        getActivity().runOnUiThread(() -> tvGameStatus.setText(status));
    }

    /**
     * 更新 ModLoader 下载进度
     */
    public void updateModLoaderDownloadProgress(long downloaded, long total, String status) {
        if (getActivity() == null || !isAdded()) return;
        getActivity().runOnUiThread(() -> {
            if (total > 0) {
                int progress = (int) (downloaded * 100 / total);
                progressModLoader.setProgressCompat(progress, true);
                tvModLoaderProgress.setText(progress + "%");
            } else {
                tvModLoaderProgress.setText(formatBytes(downloaded));
            }
            tvModLoaderStatus.setText(status);
        });
    }

    /**
     * 设置 ModLoader 下载状态
     */
    public void setModLoaderDownloadStatus(String status) {
        if (getActivity() == null || !isAdded()) return;
        getActivity().runOnUiThread(() -> tvModLoaderStatus.setText(status));
    }

    /**
     * 格式化字节数
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}

