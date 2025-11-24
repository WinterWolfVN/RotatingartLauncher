package com.app.ralaunch.manager;

import android.app.AlertDialog;
import android.content.Context;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import androidx.drawerlayout.widget.DrawerLayout;

import com.app.ralaunch.R;
import com.app.ralaunch.utils.AppLogger;

/**
 * 游戏菜单管理器
 * 
 * 统一管理游戏内菜单功能，包括：
 * - 菜单初始化
 * - 菜单项点击处理
 * - 菜单显示/隐藏
 * 
 * 减少 GameActivity 的代码耦合
 */
public class GameMenuManager {
    private static final String TAG = "GameMenuManager";
    
    private Context mContext;
    private DrawerLayout mDrawerLayout;
    private ListView mGameMenu;
    private ArrayAdapter<String> mGameMenuAdapter;
    
    private OnMenuItemClickListener mMenuItemListener;
    
    /**
     * 菜单项点击监听器
     */
    public interface OnMenuItemClickListener {
        void onToggleControls();
        void onEditControls();
        void onQuickSettings();
        void onExitGame();
    }
    
    public GameMenuManager(Context context, DrawerLayout drawerLayout, ListView gameMenu) {
        mContext = context;
        mDrawerLayout = drawerLayout;
        mGameMenu = gameMenu;
    }
    
    /**
     * 设置菜单项点击监听器
     */
    public void setOnMenuItemClickListener(OnMenuItemClickListener listener) {
        mMenuItemListener = listener;
    }
    
    /**
     * 初始化菜单
     */
    public void setupMenu() {
        try {
            // 设置菜单项
            String[] menuItems = mContext.getResources().getStringArray(R.array.game_menu_items);
            mGameMenuAdapter = new ArrayAdapter<>(mContext, android.R.layout.simple_list_item_1, menuItems);
            mGameMenu.setAdapter(mGameMenuAdapter);
            
            // 设置菜单项点击事件
            mGameMenu.setOnItemClickListener((parent, view, position, id) -> {
                handleMenuClick(position);
                if (mDrawerLayout != null) {
                    mDrawerLayout.closeDrawers();
                }
            });
            
            AppLogger.info(TAG, "Game menu setup successfully");
        } catch (Exception e) {
            AppLogger.error(TAG, "Failed to setup game menu", e);
        }
    }
    
    /**
     * 处理菜单点击事件
     */
    private void handleMenuClick(int position) {
        if (mMenuItemListener == null) return;
        
        switch (position) {
            case 0: // 切换控制显示
                mMenuItemListener.onToggleControls();
                break;
            case 1: // 编辑控制布局
                mMenuItemListener.onEditControls();
                break;
            case 2: // 快速设置
                mMenuItemListener.onQuickSettings();
                break;
            case 3: // 退出游戏
                mMenuItemListener.onExitGame();
                break;
        }
    }
    
    /**
     * 打开菜单
     */
    public void openMenu() {
        if (mDrawerLayout != null && mGameMenu != null) {
            mDrawerLayout.openDrawer(mGameMenu);
        }
    }
    
    /**
     * 关闭菜单
     */
    public void closeMenu() {
        if (mDrawerLayout != null) {
            mDrawerLayout.closeDrawers();
        }
    }
    
    /**
     * 检查菜单是否打开
     */
    public boolean isMenuOpen() {
        return mDrawerLayout != null && mGameMenu != null && mDrawerLayout.isDrawerOpen(mGameMenu);
    }
    
    /**
     * 显示退出确认对话框
     */
    public void showExitConfirmDialog() {
        new AlertDialog.Builder(mContext)
            .setTitle(R.string.game_menu_exit_confirm)
            .setMessage(R.string.game_menu_exit_message)
            .setPositiveButton(R.string.game_menu_yes, (dialog, which) -> {
                if (mContext instanceof android.app.Activity) {
                    ((android.app.Activity) mContext).finish();
                }
            })
            .setNegativeButton(R.string.game_menu_no, null)
            .show();
    }
    
    /**
     * 显示快速设置（TODO: 实现快速设置对话框）
     */
    public void showQuickSettings() {
        Toast.makeText(mContext, "快速设置功能开发中...", Toast.LENGTH_SHORT).show();
    }
}

