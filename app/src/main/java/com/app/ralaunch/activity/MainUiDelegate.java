package com.app.ralaunch.activity;

import android.view.View;

import com.app.ralaunch.manager.ThemeManager;
import com.app.ralaunch.utils.AppLogger;
import com.app.ralaunch.view.VideoBackgroundView;
import com.app.ralaunch.R;

/**
 * 负责主题背景和视频背景的应用与释放。
 */
public class MainUiDelegate {

    public void applyBackground(MainActivity activity, ThemeManager themeManager) {
        if (themeManager != null) {
            themeManager.applyBackgroundFromSettings();
        }
    }

    public void updateVideoBackground(MainActivity activity, ThemeManager themeManager) {
        try {
            VideoBackgroundView videoBackgroundView = activity.findViewById(R.id.videoBackgroundView);
            if (videoBackgroundView == null) {
                return;
            }
            if (themeManager != null && themeManager.isVideoBackground()) {
                String videoPath = themeManager.getVideoBackgroundPath();
                if (videoPath != null && !videoPath.isEmpty()) {
                    videoBackgroundView.setVisibility(View.VISIBLE);
                    videoBackgroundView.setVideoPath(videoPath);
                    videoBackgroundView.setOpacity(100);
                    videoBackgroundView.start();
                } else {
                    videoBackgroundView.setVisibility(View.GONE);
                    videoBackgroundView.release();
                }
            } else {
                videoBackgroundView.setVisibility(View.GONE);
                videoBackgroundView.release();
            }
        } catch (Exception e) {
            AppLogger.error("MainUiDelegate", "更新视频背景失败: " + e.getMessage());
        }
    }

    public void releaseVideoBackground(MainActivity activity) {
        VideoBackgroundView videoBackgroundView = activity.findViewById(R.id.videoBackgroundView);
        if (videoBackgroundView != null) {
            videoBackgroundView.release();
        }
    }
}

