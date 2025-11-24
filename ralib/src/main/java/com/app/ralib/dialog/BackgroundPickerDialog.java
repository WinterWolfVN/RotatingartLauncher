package com.app.ralib.dialog;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.app.ralib.R;
import com.google.android.material.card.MaterialCardView;

/**
 * 背景选择器对话框
 * 支持：默认背景、纯色背景、图片背景
 */
public class BackgroundPickerDialog extends DialogFragment {

    private static final int REQUEST_CODE_PICK_IMAGE = 1001;

    private MaterialCardView defaultCard, colorCard, imageCard;
    private View colorPreview;
    private ImageView imagePreview;
    private TextView tvColorValue, tvImagePath;
    private Button btnConfirm, btnCancel;

    private String currentType = "default";
    private int currentColor = 0xFFFFFFFF;
    private String currentImagePath = "";

    private OnBackgroundSelectedListener listener;

    public interface OnBackgroundSelectedListener {
        void onBackgroundSelected(String type, int color, String imagePath);
    }

    public static BackgroundPickerDialog newInstance(String type, int color, String imagePath) {
        BackgroundPickerDialog dialog = new BackgroundPickerDialog();
        Bundle args = new Bundle();
        args.putString("type", type);
        args.putInt("color", color);
        args.putString("imagePath", imagePath);
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NO_TITLE, android.R.style.Theme_Material_Light_Dialog);
        if (getArguments() != null) {
            currentType = getArguments().getString("type", "default");
            currentColor = getArguments().getInt("color", 0xFFFFFFFF);
            currentImagePath = getArguments().getString("imagePath", "");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.ralib_dialog_background_picker, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        defaultCard = view.findViewById(R.id.defaultCard);
        colorCard = view.findViewById(R.id.colorCard);
        imageCard = view.findViewById(R.id.imageCard);
        colorPreview = view.findViewById(R.id.colorPreview);
        imagePreview = view.findViewById(R.id.imagePreview);
        tvColorValue = view.findViewById(R.id.tvColorValue);
        tvImagePath = view.findViewById(R.id.tvImagePath);
        btnConfirm = view.findViewById(R.id.btnConfirm);
        btnCancel = view.findViewById(R.id.btnCancel);

        // 关闭按钮
        ImageButton btnClose = view.findViewById(R.id.btnClose);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dismiss());
        }

        // 默认背景选项
        if (defaultCard != null) {
            defaultCard.setOnClickListener(v -> selectType("default"));
        }

        // 纯色背景选项
        if (colorCard != null) {
            colorCard.setOnClickListener(v -> selectType("color"));
            
            // 颜色预览点击打开颜色选择器
            View colorPreviewClick = view.findViewById(R.id.colorPreviewClick);
            if (colorPreviewClick != null) {
                colorPreviewClick.setOnClickListener(v -> {
                    com.app.ralib.dialog.ColorPickerDialog dialog =
                        com.app.ralib.dialog.ColorPickerDialog.newInstance(currentColor);
                    dialog.setOnColorSelectedListener(color -> {
                        currentColor = color;
                        updateColorPreview();
                        selectType("color");
                    });
                    dialog.show(getParentFragmentManager(), "color_picker");
                });
            }
        }

        // 图片背景选项
        if (imageCard != null) {
            imageCard.setOnClickListener(v -> selectType("image"));
            
            // 选择图片按钮
            Button btnSelectImage = view.findViewById(R.id.btnSelectImage);
            if (btnSelectImage != null) {
                btnSelectImage.setOnClickListener(v -> pickImage());
            }
        }

        // 确定按钮
        if (btnConfirm != null) {
            btnConfirm.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onBackgroundSelected(currentType, currentColor, currentImagePath);
                }
                dismiss();
            });
        }

        // 取消按钮
        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> dismiss());
        }

        // 初始化显示
        updateColorPreview();
        updateImagePreview();
        selectType(currentType);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            android.util.DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
            int dialogWidth = (int) (displayMetrics.widthPixels * 0.9f);
            int maxWidth = (int) (480 * displayMetrics.density);
            dialogWidth = Math.min(dialogWidth, maxWidth);

            getDialog().getWindow().setLayout(
                dialogWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            getDialog().getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
    }

    private void selectType(String type) {
        currentType = type;
        updateSelection();
    }

    private void updateSelection() {
        // 重置所有卡片
        if (defaultCard != null) {
            defaultCard.setStrokeWidth(0);
        }
        if (colorCard != null) {
            colorCard.setStrokeWidth(0);
        }
        if (imageCard != null) {
            imageCard.setStrokeWidth(0);
        }

        // 选中当前类型
        int strokeWidth = (int) (2 * getResources().getDisplayMetrics().density);
        int strokeColor = android.graphics.Color.parseColor("#9C7AE8"); // 使用主题色
        
        switch (currentType) {
            case "default":
                if (defaultCard != null) {
                    defaultCard.setStrokeWidth(strokeWidth);
                    defaultCard.setStrokeColor(strokeColor);
                }
                break;
            case "color":
                if (colorCard != null) {
                    colorCard.setStrokeWidth(strokeWidth);
                    colorCard.setStrokeColor(strokeColor);
                }
                break;
            case "image":
                if (imageCard != null) {
                    imageCard.setStrokeWidth(strokeWidth);
                    imageCard.setStrokeColor(strokeColor);
                }
                break;
        }
    }

    private void updateColorPreview() {
        if (colorPreview != null) {
            if (colorPreview instanceof MaterialCardView) {
                ((MaterialCardView) colorPreview).setCardBackgroundColor(currentColor);
            } else {
                colorPreview.setBackgroundColor(currentColor);
            }
        }
        if (tvColorValue != null) {
            int rgbColor = currentColor & 0x00FFFFFF;
            tvColorValue.setText(String.format("#%06X", rgbColor));
        }
    }

    private void updateImagePreview() {
        if (imagePreview != null && tvImagePath != null) {
            if (currentImagePath != null && !currentImagePath.isEmpty()) {
                try {
                    Bitmap bitmap = BitmapFactory.decodeFile(currentImagePath);
                    if (bitmap != null) {
                        imagePreview.setImageBitmap(bitmap);
                        imagePreview.setVisibility(View.VISIBLE);
                        tvImagePath.setText(getFileName(currentImagePath));
                    } else {
                        imagePreview.setVisibility(View.GONE);
                        tvImagePath.setText("未选择图片");
                    }
                } catch (Exception e) {
                    imagePreview.setVisibility(View.GONE);
                    tvImagePath.setText("图片加载失败");
                }
            } else {
                imagePreview.setVisibility(View.GONE);
                tvImagePath.setText("未选择图片");
            }
        }
    }

    private String getFileName(String path) {
        if (path == null || path.isEmpty()) {
            return "未选择图片";
        }
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < path.length() - 1) {
            return path.substring(lastSlash + 1);
        }
        return path;
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "选择图片"), REQUEST_CODE_PICK_IMAGE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_PICK_IMAGE && resultCode == android.app.Activity.RESULT_OK) {
            if (data != null && data.getData() != null) {
                Uri uri = data.getData();
                try {
                    // 获取真实路径
                    String path = getRealPathFromURI(uri);
                    if (path != null && !path.isEmpty()) {
                        currentImagePath = path;
                        updateImagePreview();
                        selectType("image");
                    }
                } catch (Exception e) {
                    android.widget.Toast.makeText(requireContext(), "无法读取图片: " + e.getMessage(),
                        android.widget.Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private String getRealPathFromURI(Uri uri) {
        String result = null;
        try {
            android.content.ContentResolver resolver = requireContext().getContentResolver();
            android.database.Cursor cursor = resolver.query(uri, null, null, null, null);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        int idx = cursor.getColumnIndex(android.provider.MediaStore.Images.Media.DATA);
                        if (idx >= 0) {
                            result = cursor.getString(idx);
                        }
                    }
                } finally {
                    cursor.close();
                }
            }
            if (result == null) {
                // 如果无法获取路径，尝试复制文件到应用目录
                result = copyUriToFile(uri);
            }
        } catch (Exception e) {
            // 如果所有方法都失败，返回 URI 的字符串形式
            result = uri.toString();
        }
        return result;
    }

    private String copyUriToFile(Uri uri) {
        try {
            java.io.InputStream inputStream = requireContext().getContentResolver().openInputStream(uri);
            if (inputStream != null) {
                java.io.File outputDir = new java.io.File(requireContext().getFilesDir(), "backgrounds");
                if (!outputDir.exists()) {
                    outputDir.mkdirs();
                }
                String fileName = "bg_" + System.currentTimeMillis() + ".jpg";
                java.io.File outputFile = new java.io.File(outputDir, fileName);
                java.io.FileOutputStream outputStream = new java.io.FileOutputStream(outputFile);
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.close();
                inputStream.close();
                return outputFile.getAbsolutePath();
            }
        } catch (Exception e) {
            // 忽略错误
        }
        return null;
    }

    public void setOnBackgroundSelectedListener(OnBackgroundSelectedListener listener) {
        this.listener = listener;
    }
}

