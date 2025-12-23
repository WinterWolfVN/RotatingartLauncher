package com.app.ralaunch.controls.editor;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.app.ralaunch.R;
import com.app.ralaunch.controls.ControlData;
import com.app.ralaunch.controls.KeyMapper;
import com.app.ralaunch.utils.LocalizedAlertDialog;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 摇杆键值映射设置对话框
 * 允许用户为摇杆的四个方向（上、右、下、左）分别设置键值
 */
public class JoystickKeyMappingDialog extends LocalizedAlertDialog {
    private ControlData mControlData;
    private OnSaveListener mSaveListener;
    
    private Spinner mUpSpinner;
    private Spinner mRightSpinner;
    private Spinner mDownSpinner;
    private Spinner mLeftSpinner;
    
    // 方向标签
    private static final String[] DIRECTION_LABELS = {"上", "右", "下", "左"};
    
    public interface OnSaveListener {
        void onSave(ControlData data);
    }
    
    public JoystickKeyMappingDialog(@NonNull Context context, ControlData controlData, OnSaveListener listener) {
        super(context);
        mControlData = new ControlData(controlData); // 深拷贝
        mSaveListener = listener;
        
        initDialog();
    }
    
    private void initDialog() {
        Context localizedContext = getLocalizedContext();
        
        // 创建布局
        View view = LayoutInflater.from(getContext()).inflate(
            android.R.layout.simple_list_item_1, null);
        
        ViewGroup layout = new android.widget.LinearLayout(getContext());
        ((android.widget.LinearLayout) layout).setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(40, 40, 40, 40);
        
        // 标题说明
        TextView titleDesc = new TextView(getContext());
        titleDesc.setText(localizedContext.getString(R.string.editor_joystick_key_mapping_desc));
        titleDesc.setTextSize(14);
        // 使用主题颜色，支持暗色模式
        android.util.TypedValue typedValue = new android.util.TypedValue();
        getContext().getTheme().resolveAttribute(android.R.attr.textColorSecondary, typedValue, true);
        titleDesc.setTextColor(typedValue.data);
        titleDesc.setPadding(0, 0, 0, 20);
        layout.addView(titleDesc);
        
        // 获取所有可用按键
        Map<String, Integer> allKeys = KeyMapper.getAllKeys();
        List<String> keyNames = new ArrayList<>(allKeys.keySet());
        List<Integer> keyCodes = new ArrayList<>(allKeys.values());
        
        // 创建适配器
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(),
            android.R.layout.simple_spinner_item, keyNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        
        // 上方向
        layout.addView(createDirectionLabel(localizedContext.getString(R.string.editor_joystick_key_up)));
        mUpSpinner = createSpinner(adapter, keyNames, keyCodes, 
            mControlData.joystickKeys != null && mControlData.joystickKeys.length > 0 
                ? mControlData.joystickKeys[0] : ControlData.SDL_SCANCODE_W);
        layout.addView(mUpSpinner);
        
        // 右方向
        layout.addView(createDirectionLabel(localizedContext.getString(R.string.editor_joystick_key_right)));
        mRightSpinner = createSpinner(adapter, keyNames, keyCodes,
            mControlData.joystickKeys != null && mControlData.joystickKeys.length > 1 
                ? mControlData.joystickKeys[1] : ControlData.SDL_SCANCODE_D);
        layout.addView(mRightSpinner);
        
        // 下方向
        layout.addView(createDirectionLabel(localizedContext.getString(R.string.editor_joystick_key_down)));
        mDownSpinner = createSpinner(adapter, keyNames, keyCodes,
            mControlData.joystickKeys != null && mControlData.joystickKeys.length > 2 
                ? mControlData.joystickKeys[2] : ControlData.SDL_SCANCODE_S);
        layout.addView(mDownSpinner);
        
        // 左方向
        layout.addView(createDirectionLabel(localizedContext.getString(R.string.editor_joystick_key_left)));
        mLeftSpinner = createSpinner(adapter, keyNames, keyCodes,
            mControlData.joystickKeys != null && mControlData.joystickKeys.length > 3 
                ? mControlData.joystickKeys[3] : ControlData.SDL_SCANCODE_A);
        layout.addView(mLeftSpinner);
        
        // 快速设置按钮（WASD）
        Button btnWASD = new Button(getContext());
        btnWASD.setText(localizedContext.getString(R.string.editor_joystick_key_reset_wasd));
        btnWASD.setOnClickListener(v -> {
            setKeyCode(mUpSpinner, keyNames, keyCodes, ControlData.SDL_SCANCODE_W);
            setKeyCode(mRightSpinner, keyNames, keyCodes, ControlData.SDL_SCANCODE_D);
            setKeyCode(mDownSpinner, keyNames, keyCodes, ControlData.SDL_SCANCODE_S);
            setKeyCode(mLeftSpinner, keyNames, keyCodes, ControlData.SDL_SCANCODE_A);
        });
        layout.addView(btnWASD);
        
        setView(layout);
        setTitle(localizedContext.getString(R.string.editor_joystick_key_mapping));
        
        setButton(BUTTON_POSITIVE, localizedContext.getString(R.string.editor_save_button_label), (dialog, which) -> {
            saveChanges();
        });
        setButton(BUTTON_NEGATIVE, localizedContext.getString(R.string.cancel), (dialog, which) -> {
            dismiss();
        });
    }
    
    private TextView createDirectionLabel(String text) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextSize(14);
        tv.setPadding(0, 20, 0, 5);
        return tv;
    }
    
    private Spinner createSpinner(ArrayAdapter<String> adapter, List<String> keyNames, 
                                  List<Integer> keyCodes, int currentKeyCode) {
        Spinner spinner = new Spinner(getContext());
        spinner.setAdapter(adapter);
        
        // 设置当前选中的键值
        int index = keyCodes.indexOf(currentKeyCode);
        if (index >= 0) {
            spinner.setSelection(index);
        }
        
        return spinner;
    }
    
    private void setKeyCode(Spinner spinner, List<String> keyNames, List<Integer> keyCodes, int keyCode) {
        int index = keyCodes.indexOf(keyCode);
        if (index >= 0) {
            spinner.setSelection(index);
        }
    }
    
    private void saveChanges() {
        try {
            Map<String, Integer> allKeys = KeyMapper.getAllKeys();
            List<String> keyNames = new ArrayList<>(allKeys.keySet());
            
            int upKey = allKeys.get(keyNames.get(mUpSpinner.getSelectedItemPosition()));
            int rightKey = allKeys.get(keyNames.get(mRightSpinner.getSelectedItemPosition()));
            int downKey = allKeys.get(keyNames.get(mDownSpinner.getSelectedItemPosition()));
            int leftKey = allKeys.get(keyNames.get(mLeftSpinner.getSelectedItemPosition()));
            
            mControlData.joystickKeys = new int[]{upKey, rightKey, downKey, leftKey};
            
            // 确保摇杆模式是键盘模式
            if (mControlData.joystickMode != ControlData.JOYSTICK_MODE_KEYBOARD) {
                mControlData.joystickMode = ControlData.JOYSTICK_MODE_KEYBOARD;
            }
            
            if (mSaveListener != null) {
                mSaveListener.onSave(mControlData);
            }
            
            dismiss();
        } catch (Exception e) {
            Toast.makeText(getLocalizedContext(), 
                getLocalizedContext().getString(R.string.editor_save_failed, e.getMessage()), 
                Toast.LENGTH_SHORT).show();
        }
    }
}

