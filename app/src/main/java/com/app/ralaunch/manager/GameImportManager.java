package com.app.ralaunch.manager;

import android.content.Context;
import android.os.Bundle;
import androidx.fragment.app.FragmentManager;
import com.app.ralaunch.dialog.LocalImportDialog;
import com.app.ralaunch.fragment.FileBrowserFragment;
import com.app.ralaunch.fragment.LocalImportFragment;
import com.app.ralaunch.model.GameItem;

/**
 * 游戏导入管理器
 * 负责处理游戏导入流程
 */
public class GameImportManager {
    private final Context context;
    private final FragmentNavigator fragmentNavigator;
    private OnImportCompleteListener onImportCompleteListener;
    
    public interface OnImportCompleteListener {
        void onImportComplete(String gameType, GameItem newGame);
    }
    
    public GameImportManager(Context context, FragmentNavigator fragmentNavigator) {
        this.context = context;
        this.fragmentNavigator = fragmentNavigator;
    }
    
    /**
     * 显示添加游戏对话框
     */
    public void showAddGameDialog(FragmentManager fragmentManager) {
        LocalImportDialog localImportDialog = new LocalImportDialog();
        
        // 设置文件选择监听器
        localImportDialog.setOnFileSelectionListener(new LocalImportDialog.OnFileSelectionListener() {
            @Override
            public void onSelectGameFile(LocalImportDialog dialog) {
                showFileBrowserForSelection("game", new String[]{".sh"}, filePath -> {
                    dialog.setGameFile(filePath);
                    dialog.showDialog();
                });
            }
            
            @Override
            public void onSelectModLoaderFile(LocalImportDialog dialog) {
                showFileBrowserForSelection("modloader", new String[]{".zip"}, filePath -> {
                    dialog.setModLoaderFile(filePath);
                    dialog.showDialog();
                });
            }
        });
        
        // 设置导入开始监听器
        localImportDialog.setOnImportStartListener((gameFilePath, modLoaderFilePath, gameName, gameVersion) -> {
            startGameImport(gameFilePath, modLoaderFilePath, gameName, gameVersion, fragmentManager);
        });
        
        localImportDialog.show(fragmentManager, "local_import_dialog");
    }
    
    /**
     * 显示文件浏览器选择文件
     */
    private void showFileBrowserForSelection(String fileType, String[] extensions, FileSelectionCallback callback) {
        FileBrowserFragment fileBrowserFragment = new FileBrowserFragment();
        fileBrowserFragment.setFileType(fileType, extensions);
        fileBrowserFragment.setOnFileSelectedListener((filePath, selectedFileType) -> {
            callback.onFileSelected(filePath);
            fragmentNavigator.hideFragment("file_browser");
        });
        fileBrowserFragment.setOnBackListener(() -> fragmentNavigator.hideFragment("file_browser"));
        
        fragmentNavigator.showFragment(fileBrowserFragment, "file_browser");
    }
    
    /**
     * 开始游戏导入
     */
    private void startGameImport(String gameFilePath, String modLoaderFilePath, 
                                 String gameName, String gameVersion, FragmentManager fragmentManager) {
        LocalImportFragment localImportFragment = new LocalImportFragment();
        localImportFragment.setOnImportCompleteListener((gameType, newGame) -> {
            if (onImportCompleteListener != null) {
                onImportCompleteListener.onImportComplete(gameType, newGame);
            }
        });
        localImportFragment.setOnBackListener(() -> fragmentNavigator.goBack());
        
        // 传递文件路径给Fragment
        Bundle args = new Bundle();
        args.putString("gameFilePath", gameFilePath);
        args.putString("modLoaderFilePath", modLoaderFilePath);
        args.putString("gameName", gameName);
        args.putString("gameVersion", gameVersion);
        localImportFragment.setArguments(args);
        
        fragmentNavigator.showFragment(localImportFragment, "local_import");
    }
    
    /**
     * 设置导入完成监听器
     */
    public void setOnImportCompleteListener(OnImportCompleteListener listener) {
        this.onImportCompleteListener = listener;
    }
    
    /**
     * 文件选择回调接口
     */
    private interface FileSelectionCallback {
        void onFileSelected(String filePath);
    }
}

