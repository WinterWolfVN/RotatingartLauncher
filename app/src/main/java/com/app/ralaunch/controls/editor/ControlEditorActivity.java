package com.app.ralaunch.controls.editor;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;

import com.app.ralaunch.R;
import com.app.ralaunch.controls.*;

import java.io.File;
import java.io.InputStream;

public class ControlEditorActivity extends AppCompatActivity {
    private static final String TAG = "ControlEditorActivity";
    
    private DrawerLayout mDrawerLayout;
    private View mNavigationView; // 抽屉视图
    private ListView mMenuList;
    private FrameLayout mEditorContainer;
    private ControlLayout mPreviewLayout;
    private GridOverlayView mGridOverlay;
    private SideEditDialog mSideDialog;
    
    private ControlConfig mCurrentConfig;
    private SDLInputBridge mDummyBridge;
    private DisplayMetrics mMetrics;
    private int mScreenWidth;
    private int mScreenHeight;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 设置全屏沉浸模式并隐藏刘海屏
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
        
        // Android P+ 隐藏刘海屏
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode = 
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        
        setContentView(R.layout.activity_control_editor);
        
        // 获取屏幕尺寸
        mMetrics = getResources().getDisplayMetrics();
        mScreenWidth = mMetrics.widthPixels;
        mScreenHeight = mMetrics.heightPixels;
        
        initUI();
        
        // 延迟加载布局
        mEditorContainer.post(() -> loadOrCreateLayout());
    }
    
    private void initUI() {
        mDrawerLayout = findViewById(R.id.drawer_layout);
        mNavigationView = findViewById(R.id.navigation_view);
        mMenuList = findViewById(R.id.menu_list);
        mEditorContainer = findViewById(R.id.editor_container);
        
        // 锁定抽屉，只能通过按钮打开
        mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        
        // 抽屉按钮
        findViewById(R.id.drawer_button).setOnClickListener(v -> 
            mDrawerLayout.openDrawer(mNavigationView)
        );
        
        // 创建侧边编辑对话框
        mSideDialog = new SideEditDialog(this, mEditorContainer, mScreenWidth, mScreenHeight);
        
        // 设置菜单
        String[] menuItems = {
            "添加按键",
            "添加摇杆",
            "显示/隐藏网格",
            "保存布局",
            "加载布局",
            "重置为默认",
            "保存并退出"
        };
        
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            this, 
            android.R.layout.simple_list_item_1, 
            menuItems
        );
        mMenuList.setAdapter(adapter);
        
        mMenuList.setOnItemClickListener((parent, view, position, id) -> {
            mDrawerLayout.closeDrawers();
            handleMenuClick(position);
        });
    }
    
    private void handleMenuClick(int position) {
        switch (position) {
            case 0: // 添加按键
                addButton();
                break;
            case 1: // 添加摇杆
                addJoystick();
                break;
            case 2: // 显示/隐藏网格
                toggleGrid();
                break;
            case 3: // 保存布局
                saveLayout();
                break;
            case 4: // 加载布局
                loadLayout();
                break;
            case 5: // 重置为默认
                resetToDefault();
                break;
            case 6: // 保存并退出
                saveLayout();
                finish();
                break;
        }
    }
    
    private void loadOrCreateLayout() {
        File customFile = new File(getFilesDir(), "custom_layout.json");
        
        // 优先加载用户自定义布局
        if (customFile.exists()) {
            try {
                String json = new String(java.nio.file.Files.readAllBytes(customFile.toPath()));
                mCurrentConfig = new com.google.gson.Gson().fromJson(json, ControlConfig.class);
                Log.i(TAG, "Loaded custom layout: " + mCurrentConfig.name);
            } catch (Exception e) {
                Log.e(TAG, "Failed to load custom layout", e);
                loadDefaultLayout();
            }
        } else {
            // 加载assets中的默认布局
            try {
                InputStream is = getAssets().open("controls/default_layout.json");
                byte[] buffer = new byte[is.available()];
                is.read(buffer);
                is.close();
                String json = new String(buffer, "UTF-8");
                mCurrentConfig = new com.google.gson.Gson().fromJson(json, ControlConfig.class);
                Log.i(TAG, "Loaded default layout from assets: " + mCurrentConfig.name);
            } catch (Exception e) {
                Log.e(TAG, "Failed to load default layout from assets", e);
                loadDefaultLayout();
            }
        }
        
        displayLayout();
    }
    
    private void loadDefaultLayout() {
        mCurrentConfig = new ControlConfig();
        mCurrentConfig.name = "默认布局";
        mCurrentConfig.controls = new java.util.ArrayList<>();
        
        // 添加一个默认摇杆
        ControlData joystick = ControlData.createDefaultJoystick();
        // 调整位置为左下角
        joystick.y = mScreenHeight - joystick.height - 50;
        mCurrentConfig.controls.add(joystick);
        
        Log.i(TAG, "Loaded hardcoded default layout");
    }
    
    private void displayLayout() {
        // 清除现有视图
        mEditorContainer.removeAllViews();
        
        // 添加网格覆盖层
        mGridOverlay = new GridOverlayView(this);
        mEditorContainer.addView(mGridOverlay, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));
        
        // 创建预览布局
        mDummyBridge = new SDLInputBridge();
        mPreviewLayout = new ControlLayout(this);
        mPreviewLayout.setInputBridge(mDummyBridge);
        mPreviewLayout.loadLayout(mCurrentConfig);
        mPreviewLayout.setControlsVisible(true);
        mPreviewLayout.setClipChildren(false);
        mPreviewLayout.setClipToPadding(false);
        
        mEditorContainer.addView(mPreviewLayout, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));
        
        // 为每个控件设置交互
        setupControlInteractions();
        
        // 重新创建侧边对话框（因为父布局已更改）
        mSideDialog = new SideEditDialog(this, mEditorContainer, mScreenWidth, mScreenHeight);
        
        Log.i(TAG, "Displayed " + mCurrentConfig.controls.size() + " controls");
    }
    
    private void setupControlInteractions() {
        for (int i = 0; i < mPreviewLayout.getChildCount(); i++) {
            View child = mPreviewLayout.getChildAt(i);
            if (child instanceof ControlView) {
                ControlView controlView = (ControlView) child;
                setupControlViewInteraction(controlView);
            }
        }
    }
    
    private void setupControlViewInteraction(ControlView controlView) {
        if (!(controlView instanceof View)) return;
        
        View view = (View) controlView;
        final ControlData data = controlView.getData();
        final float[] lastPos = new float[2];
        final boolean[] isDragging = new boolean[1];
        
        view.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    lastPos[0] = event.getRawX();
                    lastPos[1] = event.getRawY();
                    isDragging[0] = false;
                    return true;
                    
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - lastPos[0];
                    float dy = event.getRawY() - lastPos[1];
                    
                    if (!isDragging[0] && (Math.abs(dx) > 10 || Math.abs(dy) > 10)) {
                        isDragging[0] = true;
                    }
                    
                    if (isDragging[0]) {
                        data.x += dx;
                        data.y += dy;
                        v.setX(data.x);
                        v.setY(data.y);
                        lastPos[0] = event.getRawX();
                        lastPos[1] = event.getRawY();
                    }
                    return true;
                    
                case MotionEvent.ACTION_UP:
                    if (!isDragging[0]) {
                        // 点击事件 - 显示侧边对话框
                        Log.i(TAG, "Control clicked: " + data.name);
                        mSideDialog.show(data);
                    }
                    return true;
            }
            return false;
        });
    }
    
    private void addButton() {
        ControlData button = new ControlData();
        button.name = "新按键";
        button.type = ControlData.TYPE_BUTTON;
        button.x = mScreenWidth / 2f;
        button.y = mScreenHeight / 2f;
        button.width = 100;
        button.height = 100;
        button.opacity = 0.7f;
        button.visible = true;
        button.keycode = 62; // Space
        
        mCurrentConfig.controls.add(button);
        displayLayout();
        
        Toast.makeText(this, "已添加按键", Toast.LENGTH_SHORT).show();
    }
    
    private void addJoystick() {
        ControlData joystick = ControlData.createDefaultJoystick();
        joystick.x = mScreenWidth / 2f;
        joystick.y = mScreenHeight / 2f;
        
        mCurrentConfig.controls.add(joystick);
        displayLayout();
        
        Toast.makeText(this, "已添加摇杆", Toast.LENGTH_SHORT).show();
    }
    
    private void toggleGrid() {
        if (mGridOverlay != null) {
            mGridOverlay.toggleGrid();
        }
    }
    
    private void saveLayout() {
        try {
            File file = new File(getFilesDir(), "custom_layout.json");
            String json = new com.google.gson.Gson().toJson(mCurrentConfig);
            java.nio.file.Files.write(file.toPath(), json.getBytes());
            Toast.makeText(this, "布局已保存", Toast.LENGTH_SHORT).show();
            Log.i(TAG, "Layout saved to: " + file.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Failed to save layout", e);
            Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void loadLayout() {
        // TODO: 实现从文件选择器加载布局
        Toast.makeText(this, "加载功能待实现", Toast.LENGTH_SHORT).show();
    }
    
    private void resetToDefault() {
        new AlertDialog.Builder(this)
            .setTitle("重置布局")
            .setMessage("确定要重置为默认布局吗？当前布局将丢失。")
            .setPositiveButton("确定", (dialog, which) -> {
                loadDefaultLayout();
                displayLayout();
                Toast.makeText(this, "已重置为默认布局", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    @Override
    public void onBackPressed() {
        if (mSideDialog != null && mSideDialog.isDisplaying()) {
            mSideDialog.hide();
            return;
        }
        
        if (mDrawerLayout.isDrawerOpen(mNavigationView)) {
            mDrawerLayout.closeDrawers();
            return;
        }
        
        new AlertDialog.Builder(this)
            .setTitle("退出编辑器")
            .setMessage("是否保存当前布局？")
            .setPositiveButton("保存并退出", (dialog, which) -> {
                saveLayout();
                finish();
            })
            .setNegativeButton("直接退出", (dialog, which) -> finish())
            .setNeutralButton("取消", null)
            .show();
    }
}
