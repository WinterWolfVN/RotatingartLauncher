package com.app.ralaunch.activity;

import com.app.ralaunch.fragment.ControlLayoutFragment;
import com.app.ralaunch.manager.FragmentNavigator;
import com.app.ralaunch.manager.UIManager;

/**
 * 负责 MainActivity 的控制布局 Fragment 相关逻辑。
 * 包括显示/隐藏控制布局 Fragment、处理返回等。
 */
public class MainControlFragmentDelegate {

    private static final String TAG = "MainControlFragmentDelegate";

    private final MainActivity activity;
    private final FragmentNavigator fragmentNavigator;
    private final UIManager uiManager;

    public MainControlFragmentDelegate(MainActivity activity, FragmentNavigator fragmentNavigator,
                                      UIManager uiManager) {
        this.activity = activity;
        this.fragmentNavigator = fragmentNavigator;
        this.uiManager = uiManager;
    }

    /**
     * 显示控制布局 Fragment（通过 FragmentNavigator）
     */
    public void showControlLayoutFragment() {
        // 确保主布局显示
        if (uiManager != null) {
            uiManager.showMainLayout();
        }
        
        if (fragmentNavigator != null) {
            ControlLayoutFragment controlFragment = new ControlLayoutFragment();
            controlFragment.setOnControlLayoutBackListener(this::hideControlLayoutFragment);
            fragmentNavigator.showPage(controlFragment, "control_layout");
        }
    }

    /**
     * 隐藏控制布局 Fragment
     */
    public void hideControlLayoutFragment() {
        if (fragmentNavigator != null) {
            fragmentNavigator.hideFragmentWithCleanup("control_layout");
        }
    }
}

