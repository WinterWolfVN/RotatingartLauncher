package com.app.ralaunch.controls.editor;

import android.app.Dialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.annotation.NonNull;

import com.app.ralaunch.R;
import com.app.ralaunch.controls.ControlData;
import com.app.ralaunch.utils.LocalizedAlertDialog;

/**
 * 控件属性编辑对话框
 */
public class EditControlDialog extends LocalizedAlertDialog {
    private ControlData mControlData;
    private OnSaveListener mSaveListener;
    
    private EditText mNameEdit;
    private Spinner mTypeSpinner;
    private EditText mXEdit, mYEdit, mWidthEdit, mHeightEdit;
    private EditText mKeycodeEdit;
    private SeekBar mOpacitySeek;
    private CheckBox mToggleCheck;
    private EditText mJoystickKeysEdit;
    
    public interface OnSaveListener {
        void onSave(ControlData data);
    }
    
    public EditControlDialog(@NonNull Context context, ControlData controlData, OnSaveListener listener) {
        super(context);
        mControlData = new ControlData(controlData); // 深拷贝
        mSaveListener = listener;
        
        initDialog();
    }
    
    private void initDialog() {
        Context localizedContext = getLocalizedContext();
        // 布局创建使用原始Context（包含主题），字符串资源使用localizedContext
        View view = LayoutInflater.from(getContext()).inflate(
            android.R.layout.simple_list_item_1, null); // 临时使用简单布局
        
        // 创建布局
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 40, 40, 40);
        
        // 名称
        layout.addView(createLabel(localizedContext.getString(R.string.editor_control_name_label)));
        mNameEdit = createEditText(mControlData.name);
        layout.addView(mNameEdit);
        
        // 类型
        layout.addView(createLabel(localizedContext.getString(R.string.editor_control_type_label)));
        mTypeSpinner = new Spinner(getContext());
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(localizedContext,
            android.R.layout.simple_spinner_item,
            new String[]{
                localizedContext.getString(R.string.editor_control_type_button),
                localizedContext.getString(R.string.editor_control_type_joystick)
            });
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mTypeSpinner.setAdapter(typeAdapter);
        mTypeSpinner.setSelection(mControlData.type);
        layout.addView(mTypeSpinner);
        
        // 位置
        LinearLayout posLayout = new LinearLayout(getContext());
        posLayout.setOrientation(LinearLayout.HORIZONTAL);
        posLayout.addView(createLabel(localizedContext.getString(R.string.editor_position_x_label)));
        mXEdit = createNumberEdit(String.valueOf((int)mControlData.x));
        mXEdit.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        posLayout.addView(mXEdit);
        posLayout.addView(createLabel(" " + localizedContext.getString(R.string.editor_position_y_label)));
        mYEdit = createNumberEdit(String.valueOf((int)mControlData.y));
        mYEdit.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        posLayout.addView(mYEdit);
        layout.addView(posLayout);
        
        // 大小
        LinearLayout sizeLayout = new LinearLayout(getContext());
        sizeLayout.setOrientation(LinearLayout.HORIZONTAL);
        sizeLayout.addView(createLabel(localizedContext.getString(R.string.editor_size_width_label)));
        mWidthEdit = createNumberEdit(String.valueOf((int)mControlData.width));
        mWidthEdit.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        sizeLayout.addView(mWidthEdit);
        sizeLayout.addView(createLabel(" " + localizedContext.getString(R.string.editor_size_height_label)));
        mHeightEdit = createNumberEdit(String.valueOf((int)mControlData.height));
        mHeightEdit.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        sizeLayout.addView(mHeightEdit);
        layout.addView(sizeLayout);
        
        // 按键码
        layout.addView(createLabel(localizedContext.getString(R.string.editor_keycode_sdl)));
        mKeycodeEdit = createNumberEdit(String.valueOf(mControlData.keycode));
        layout.addView(mKeycodeEdit);
        
        // 透明度
        layout.addView(createLabel(localizedContext.getString(R.string.editor_opacity_label, (int)(mControlData.opacity * 100))));
        mOpacitySeek = new SeekBar(getContext());
        mOpacitySeek.setMax(100);
        mOpacitySeek.setProgress((int)(mControlData.opacity * 100));
        layout.addView(mOpacitySeek);
        
        // 切换按钮
        mToggleCheck = new CheckBox(getContext());
        mToggleCheck.setText(localizedContext.getString(R.string.editor_toggle_button_press_hold));
        mToggleCheck.setChecked(mControlData.isToggle);
        layout.addView(mToggleCheck);
        
        // 摇杆按键（仅摇杆）
        if (mControlData.type == ControlData.TYPE_JOYSTICK && mControlData.joystickKeys != null) {
            layout.addView(createLabel(localizedContext.getString(R.string.editor_joystick_keys)));
            String keys = String.format("%d,%d,%d,%d", 
                mControlData.joystickKeys[0],
                mControlData.joystickKeys[1],
                mControlData.joystickKeys[2],
                mControlData.joystickKeys[3]);
            mJoystickKeysEdit = createEditText(keys);
            layout.addView(mJoystickKeysEdit);
        }
        
        setView(layout);
        setTitle(localizedContext.getString(R.string.editor_edit_control_properties));
        
        setButton(BUTTON_POSITIVE, localizedContext.getString(R.string.editor_save_button_label), (dialog, which) -> {
            saveChanges();
        });
        setButton(BUTTON_NEGATIVE, localizedContext.getString(R.string.cancel), (dialog, which) -> {
            dismiss();
        });
    }
    
    private TextView createLabel(String text) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextSize(14);
        tv.setPadding(0, 20, 0, 5);
        return tv;
    }
    
    private EditText createEditText(String text) {
        EditText et = new EditText(getContext());
        et.setText(text);
        et.setSingleLine();
        return et;
    }
    
    private EditText createNumberEdit(String text) {
        EditText et = createEditText(text);
        et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        return et;
    }
    
    private void saveChanges() {
        try {
            mControlData.name = mNameEdit.getText().toString();
            mControlData.type = mTypeSpinner.getSelectedItemPosition();
            mControlData.x = Float.parseFloat(mXEdit.getText().toString());
            mControlData.y = Float.parseFloat(mYEdit.getText().toString());
            mControlData.width = Float.parseFloat(mWidthEdit.getText().toString());
            mControlData.height = Float.parseFloat(mHeightEdit.getText().toString());
            mControlData.keycode = Integer.parseInt(mKeycodeEdit.getText().toString());
            mControlData.opacity = mOpacitySeek.getProgress() / 100f;
            mControlData.isToggle = mToggleCheck.isChecked();
            
            if (mJoystickKeysEdit != null) {
                String[] keys = mJoystickKeysEdit.getText().toString().split(",");
                if (keys.length == 4) {
                    mControlData.joystickKeys = new int[]{
                        Integer.parseInt(keys[0].trim()),
                        Integer.parseInt(keys[1].trim()),
                        Integer.parseInt(keys[2].trim()),
                        Integer.parseInt(keys[3].trim())
                    };
                }
            }
            
            if (mSaveListener != null) {
                mSaveListener.onSave(mControlData);
            }
        } catch (Exception e) {
            Toast.makeText(getLocalizedContext(), getLocalizedContext().getString(R.string.editor_save_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }
}
