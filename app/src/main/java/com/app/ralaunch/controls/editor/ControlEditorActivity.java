package com.app.ralaunch.controls.editor;

import android.app.AlertDialog;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.app.ralaunch.R;
import com.app.ralaunch.controls.*;
import com.app.ralaunch.controls.ControlDataConverter;
import com.app.ralaunch.controls.editor.ControlEditorOperations;

import java.io.File;
import java.io.InputStream;

public class ControlEditorActivity extends AppCompatActivity {
    private static final String TAG = "ControlEditorActivity";

    private FrameLayout mEditorContainer;
    private ControlLayout mPreviewLayout;
    private GridOverlayView mGridOverlay;
    private ControlEditDialogMD mEditDialog;
    private EditorSettingsDialog mSettingsDialog;

    private ControlConfig mCurrentConfig;
    private SDLInputBridge mDummyBridge;
    private DisplayMetrics mMetrics;
    private int mScreenWidth;
    private int mScreenHeight;

    // 布局管理
    private com.app.ralaunch.utils.ControlLayoutManager mLayoutManager;
    private String mCurrentLayoutName;

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

        // 初始化布局管理器
        mLayoutManager = new com.app.ralaunch.utils.ControlLayoutManager(this);

        // 获取要编辑的布局名称
        mCurrentLayoutName = getIntent().getStringExtra("layout_name");
        if (mCurrentLayoutName == null) {
            // 如果没有传入布局名称，使用当前默认布局
            mCurrentLayoutName = mLayoutManager.getCurrentLayoutName();
        }

        initUI();

        // 延迟加载布局
        mEditorContainer.post(() -> loadLayoutFromManager());
    }
    
    private void initUI() {
        mEditorContainer = findViewById(R.id.editor_container);

        // 设置按钮点击显示 MD3 设置弹窗
        findViewById(R.id.drawer_button).setOnClickListener(v -> {
            if (mSettingsDialog != null) {
                mSettingsDialog.show();
            }
        });

        // 初始化对话框
        setupDialogs();
    }

    /**
     * 初始化或重新初始化对话框
     * 由于 displayLayout() 会清除所有视图，需要重新创建对话框
     */
    private void setupDialogs() {
        // 创建MD风格编辑对话框
        mEditDialog = new ControlEditDialogMD(this, mScreenWidth, mScreenHeight);

        // 设置更新监听器
        mEditDialog.setOnControlUpdatedListener(control -> {
            // 实时更新视图
            if (mPreviewLayout != null) {
                for (int i = 0; i < mPreviewLayout.getChildCount(); i++) {
                    View child = mPreviewLayout.getChildAt(i);
                    if (child instanceof ControlView) {
                        ControlView controlView = (ControlView) child;
                        if (controlView.getData() == control) {
                            // 更新布局参数
                            ViewGroup.LayoutParams layoutParams = child.getLayoutParams();
                            if (layoutParams instanceof FrameLayout.LayoutParams) {
                                FrameLayout.LayoutParams frameParams = (FrameLayout.LayoutParams) layoutParams;
                                frameParams.width = (int) control.width;
                                frameParams.height = (int) control.height;
                                frameParams.leftMargin = (int) control.x;
                                frameParams.topMargin = (int) control.y;
                                child.setLayoutParams(frameParams);
                            }
                            // 更新视觉属性
                            child.setAlpha(control.opacity);
                            child.setVisibility(control.visible ? View.VISIBLE : View.INVISIBLE);
                            // 刷新控件绘制
                            controlView.updateData(control);
                            child.invalidate();
                            break;
                        }
                    }
                }
            }
        });

        // 设置删除监听器
        mEditDialog.setOnControlDeletedListener(control -> {
            if (mCurrentConfig != null && mCurrentConfig.controls != null) {
                mCurrentConfig.controls.remove(control);
                displayLayout();
            }
        });

        // 创建 MD3 设置弹窗
        mSettingsDialog = new EditorSettingsDialog(this, mEditorContainer, mScreenWidth);
        mSettingsDialog.setOnMenuItemClickListener(new EditorSettingsDialog.OnMenuItemClickListener() {
            @Override
            public void onAddButton() {
                addButton();
            }

            @Override
            public void onAddJoystick() {
                addJoystick();
            }

            @Override
            public void onJoystickModeSettings() {
                showJoystickModeDialog();
            }

            @Override
            public void onSaveLayout() {
                saveLayout();
            }

            @Override
            public void onLoadLayout() {
                loadLayout();
            }

            @Override
            public void onResetDefault() {
                resetToDefault();
            }

            @Override
            public void onSaveAndExit() {
                saveLayout();
                finish();
            }
        });
    }
    
    /**
     * 从 ControlLayoutManager 加载布局
     */
    private void loadLayoutFromManager() {
        // 根据布局名称查找布局
        com.app.ralaunch.model.ControlLayout layout = null;
        for (com.app.ralaunch.model.ControlLayout l : mLayoutManager.getLayouts()) {
            if (l.getName().equals(mCurrentLayoutName)) {
                layout = l;
                break;
            }
        }

        if (layout == null || layout.getElements().isEmpty()) {
            // 如果布局为空或不存在，加载默认布局
            loadDefaultLayout();
        } else {
            // 使用统一的转换器转换 ControlElement 列表为 ControlData 列表
            mCurrentConfig = new ControlConfig();
            mCurrentConfig.name = layout.getName();
            mCurrentConfig.controls = new java.util.ArrayList<>();

            for (com.app.ralaunch.model.ControlElement element : layout.getElements()) {
                ControlData control = ControlDataConverter.elementToData(element, mScreenWidth, mScreenHeight);
                if (control != null) {
                    mCurrentConfig.controls.add(control);
                }
            }
        }

        displayLayout();
    }

    private void loadOrCreateLayout() {
        File customFile = new File(getFilesDir(), "custom_layout.json");
        
        // 优先加载用户自定义布局
        if (customFile.exists()) {
            try {
                String json = new String(java.nio.file.Files.readAllBytes(customFile.toPath()));
                mCurrentConfig = new com.google.gson.Gson().fromJson(json, ControlConfig.class);
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

        // 统一禁用裁剪
        disableClippingRecursive(mPreviewLayout);

        mEditorContainer.addView(mPreviewLayout, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // 为每个控件设置交互
        setupControlInteractions();

        // 重新创建对话框（因为 removeAllViews 清除了它们的视图）
        setupDialogs();

    }

    /**
     * 递归禁用所有子视图的裁剪
     * 确保控件边框等绘制内容不会被父容器裁剪
     */
    private void disableClippingRecursive(View view) {
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            viewGroup.setClipChildren(false);
            viewGroup.setClipToPadding(false);

            // 递归处理所有子视图
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                disableClippingRecursive(viewGroup.getChildAt(i));
            }
        }

        // 对所有视图禁用裁剪边界和轮廓裁剪
        view.setClipToOutline(false);
        view.setClipBounds(null);
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
            float touchX = event.getX();
            float touchY = event.getY();

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    // 对于摇杆，检查触摸点是否在圆形区域内
                    if (data.type == ControlData.TYPE_JOYSTICK) {
                        float centerX = v.getWidth() / 2f;
                        float centerY = v.getHeight() / 2f;
                        float radius = Math.min(v.getWidth(), v.getHeight()) / 2f;
                        float dx = touchX - centerX;
                        float dy = touchY - centerY;
                        float distance = (float) Math.sqrt(dx * dx + dy * dy);

                        // 如果触摸点在圆形外部，不响应
                        if (distance > radius) {
                            return false;
                        }
                    }

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
                        // 更新数据
                        data.x += dx;
                        data.y += dy;

                        // 使用 LayoutParams 更新位置（与 ControlLayout 一致）
                        ViewGroup.LayoutParams layoutParams = v.getLayoutParams();
                        if (layoutParams instanceof FrameLayout.LayoutParams) {
                            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) layoutParams;
                            params.leftMargin = (int) data.x;
                            params.topMargin = (int) data.y;
                            v.setLayoutParams(params);
                        }

                        lastPos[0] = event.getRawX();
                        lastPos[1] = event.getRawY();
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    if (!isDragging[0]) {
                        // 点击事件 - 显示MD风格编辑对话框
                        mEditDialog.show(data);
                    }
                    return true;
            }
            return false;
        });
    }
    
    private void addButton() {
        if (mCurrentConfig == null) {
            mCurrentConfig = new ControlConfig();
            mCurrentConfig.controls = new java.util.ArrayList<>();
        }
        
        ControlData button = ControlEditorOperations.addButton(mCurrentConfig, mScreenWidth, mScreenHeight);
        if (button != null) {
            displayLayout();
            Toast.makeText(this, "已添加按键", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void addJoystick() {
        if (mCurrentConfig == null) {
            mCurrentConfig = new ControlConfig();
            mCurrentConfig.controls = new java.util.ArrayList<>();
        }
        
        ControlData joystick = ControlEditorOperations.addJoystick(mCurrentConfig, mScreenWidth, mScreenHeight);
        if (joystick != null) {
            displayLayout();
            Toast.makeText(this, "已添加摇杆", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveLayout() {
        ControlEditorOperations.saveLayout(this, mCurrentConfig, mCurrentLayoutName);
    }
    
    private void loadLayout() {
        // 显示选择对话框
        String[] options = {"键盘模式布局", "手柄模式布局"};
        new AlertDialog.Builder(this)
            .setTitle("选择布局")
            .setItems(options, (dialog, which) -> {
                String layoutFile = which == 0 ? "default_layout.json" : "gamepad_layout.json";
                try {
                    InputStream is = getAssets().open("controls/" + layoutFile);
                    byte[] buffer = new byte[is.available()];
                    is.read(buffer);
                    is.close();
                    String json = new String(buffer, "UTF-8");
                    mCurrentConfig = new com.google.gson.Gson().fromJson(json, ControlConfig.class);

                    // 重新显示布局
                    displayLayout();

                    Toast.makeText(this, "已加载" + options[which], Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Log.e(TAG, "Failed to load layout: " + layoutFile, e);
                    Toast.makeText(this, "加载失败", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    private void resetToDefault() {
        ControlEditorOperations.resetToDefaultLayout(this, mPreviewLayout, () -> {
            displayLayout();
        });
    }


    /**
     * 显示摇杆模式批量设置对话框
     */
    private void showJoystickModeDialog() {
        ControlEditorOperations.showJoystickModeDialog(this, mCurrentConfig, () -> {
            displayLayout();
        });
    }

    
    @Override
    public void onBackPressed() {
        // 先检查设置弹窗
        if (mSettingsDialog != null && mSettingsDialog.isDisplaying()) {
            mSettingsDialog.hide();
            return;
        }

        // 再检查控件编辑弹窗
        if (mEditDialog != null && mEditDialog.isShowing()) {
            mEditDialog.dismiss();
            return;
        }

        // 显示退出确认对话框
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
