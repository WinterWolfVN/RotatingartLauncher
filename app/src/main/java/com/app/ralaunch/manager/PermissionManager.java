package com.app.ralaunch.manager;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.core.content.ContextCompat;
import com.app.ralaunch.utils.PermissionHelper;

/**
 * 权限管理器
 * 负责处理权限请求和管理，包括：
 * - 存储权限（所有Android版本必需）
 * - 通知权限（Android 13+ 可选）
 */
public class PermissionManager {
    private final ComponentActivity activity;
    private ActivityResultLauncher<Intent> manageAllFilesLauncher;
    private ActivityResultLauncher<String[]> requestPermissionLauncher;
    private PermissionCallback currentPermissionCallback;
    
    public interface PermissionCallback {
        void onPermissionsGranted();
        void onPermissionsDenied();
    }
    
    public PermissionManager(ComponentActivity activity) {
        this.activity = activity;
    }
    
    /**
     * 初始化权限请求器
     */
    public void initialize() {
        requestPermissionLauncher = PermissionHelper.registerStoragePermissions(activity, new PermissionHelper.Callback() {
            @Override
            public void onGranted() {
                if (currentPermissionCallback != null) {
                    currentPermissionCallback.onPermissionsGranted();
                    currentPermissionCallback = null;
                }
            }
            
            @Override
            public void onDenied() {
                if (currentPermissionCallback != null) {
                    currentPermissionCallback.onPermissionsDenied();
                    currentPermissionCallback = null;
                }
            }
        });
        
        manageAllFilesLauncher = PermissionHelper.registerAllFilesAccess(activity, new PermissionHelper.Callback() {
            @Override
            public void onGranted() {
                if (currentPermissionCallback != null) {
                    currentPermissionCallback.onPermissionsGranted();
                    currentPermissionCallback = null;
                }
            }
            
            @Override
            public void onDenied() {
                if (currentPermissionCallback != null) {
                    currentPermissionCallback.onPermissionsDenied();
                    currentPermissionCallback = null;
                }
            }
        });
    }
    
    /**
     * 检查是否具有必要的权限（仅检查存储权限，通知权限为可选）
     */
    public boolean hasRequiredPermissions() {
        return PermissionHelper.hasStorageAccess(activity);
    }
    
    /**
     * 检查是否具有通知权限（Android 13+）
     */
    public boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(activity, 
                Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        // Android 13以下不需要通知权限
        return true;
    }
    
    /**
     * 请求必要的权限（存储权限 + 可选的通知权限）
     */
    public void requestRequiredPermissions(PermissionCallback callback) {
        this.currentPermissionCallback = callback;
        // 先请求存储权限，这是必需的
        PermissionHelper.requestStorage(activity, requestPermissionLauncher, manageAllFilesLauncher);
    }
    
    /**
     * 请求通知权限（Android 13+）
     * 这是可选权限，用户拒绝不影响应用核心功能
     */
    public void requestNotificationPermission(PermissionCallback callback) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            this.currentPermissionCallback = callback;
            requestPermissionLauncher.launch(new String[]{Manifest.permission.POST_NOTIFICATIONS});
        } else {
            // Android 13以下不需要请求，直接回调成功
            if (callback != null) {
                callback.onPermissionsGranted();
            }
        }
    }
}

