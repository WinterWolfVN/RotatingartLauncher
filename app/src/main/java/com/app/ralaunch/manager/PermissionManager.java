package com.app.ralaunch.manager;

import android.content.Intent;
import android.os.Build;
import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import com.app.ralaunch.utils.PermissionHelper;

/**
 * 权限管理器
 * 负责处理权限请求和管理
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
     * 检查是否具有必要的权限
     */
    public boolean hasRequiredPermissions() {
        return PermissionHelper.hasStorageAccess(activity);
    }
    
    /**
     * 请求必要的权限
     */
    public void requestRequiredPermissions(PermissionCallback callback) {
        this.currentPermissionCallback = callback;
        PermissionHelper.requestStorage(activity, requestPermissionLauncher, manageAllFilesLauncher);
    }
}

