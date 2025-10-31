package com.app.ralaunch.utils;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

/**
 * 权限管理辅助类
 * 
 * 提供统一的权限请求和检查功能，支持：
 * - 存储权限检查和请求
 * - Android 11+ 的所有文件访问权限
 * - 回调式权限请求处理
 * 
 * 自动适配不同Android版本的权限机制
 */
public final class PermissionHelper {
    public interface Callback { void onGranted(); void onDenied(); }

    private PermissionHelper() {}

    public static boolean hasStorageAccess(ComponentActivity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    public static ActivityResultLauncher<String[]> registerStoragePermissions(ComponentActivity activity, Callback cb) {
        return activity.registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            boolean allGranted = true;
            for (Boolean g : result.values()) { if (!g) { allGranted = false; break; } }
            if (allGranted) cb.onGranted(); else cb.onDenied();
        });
    }

    public static ActivityResultLauncher<Intent> registerAllFilesAccess(ComponentActivity activity, Callback cb) {
        return activity.registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) cb.onGranted(); else cb.onDenied();
            }
        });
    }

    public static void requestStorage(ComponentActivity activity, ActivityResultLauncher<String[]> launcher, ActivityResultLauncher<Intent> manageAllFilesLauncher) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + activity.getPackageName()));
                manageAllFilesLauncher.launch(intent);
            } catch (Exception e) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                manageAllFilesLauncher.launch(intent);
            }
        } else {
            launcher.launch(new String[]{ Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE });
        }
    }
}


