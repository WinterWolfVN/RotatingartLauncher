package com.app.ralaunch.fragment;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.app.ralaunch.R;
import com.app.ralaunch.activity.MainActivity;
import com.app.ralaunch.utils.GameExtractor;
import com.app.ralaunch.utils.GameInfoParser;

import java.io.File;

/**
 * 本地导入Fragment
 * 
 * 处理从本地导入游戏的完整流程：
 * - 选择游戏文件和 ModLoader 文件
 * - 解压游戏压缩包
 * - 提取游戏信息
 * - 显示导入进度
 * - 处理导入错误
 * 
 * 使用 GameExtractor 执行实际的解压和导入操作
 */
public class LocalImportFragment extends Fragment {

    private OnImportCompleteListener importCompleteListener;
    private OnBackListener backListener;

    // 界面控件
    private Button selectGameFileButton;
    private Button selectModLoaderButton;
    private Button startImportButton;
    private LinearLayout importProgressContainer;
    private ProgressBar importProgress;
    private TextView importStatus;
    private TextView gameFileText;
    private TextView modLoaderFileText;

    // 文件路径
    private String gameFilePath;
    private String modLoaderFilePath;

    // 游戏信息 - 将从.sh文件中读取
    private String gameType = "modloader";
    private String gameName = null;  // 将从gameinfo读取
    private String gameVersion = null;  // 将从gameinfo读取
    private String gameIconPath = null;  // 将从gameinfo读取
    private String engineType = "FNA";
    public static File gameDir;

    public interface OnImportCompleteListener {
        void onImportComplete(String gameType, String gameName, String gamePath, String gameBodyPath, String engineType, String iconPath);
    }

    public interface OnBackListener {
        void onBack();
    }

    public void setOnImportCompleteListener(OnImportCompleteListener listener) {
        this.importCompleteListener = listener;
    }

    public void setOnBackListener(OnBackListener listener) {
        this.backListener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_local_import, container, false);
        setupUI(view);
        return view;
    }

    private void setupUI(View view) {
        // 返回按钮
        ImageButton backButton = view.findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> {
            if (backListener != null) {
                backListener.onBack();
            }
        });

        // 初始化控件
        selectGameFileButton = view.findViewById(R.id.selectGameFileButton);
        selectModLoaderButton = view.findViewById(R.id.selectModLoaderButton);
        startImportButton = view.findViewById(R.id.startImportButton);
        importProgressContainer = view.findViewById(R.id.importProgressContainer);
        importProgress = view.findViewById(R.id.importProgress);
        importStatus = view.findViewById(R.id.importStatus);
        gameFileText = view.findViewById(R.id.gameFileText);
        modLoaderFileText = view.findViewById(R.id.modLoaderFileText);

        // 设置按钮点击事件
        selectGameFileButton.setOnClickListener(v -> selectGameFile());
        selectModLoaderButton.setOnClickListener(v -> selectModLoaderFile());
        startImportButton.setOnClickListener(v -> startImport());

        // 初始状态
        updateImportButtonState();
    }

    private void selectGameFile() {
        openFileBrowser("game", new String[]{".sh"}, filePath -> {
            gameFilePath = filePath;
            File file = new File(gameFilePath);
            gameFileText.setText("已选择: " + file.getName());
            new Thread(() -> {
                GameInfoParser.GameInfo gameInfo = GameInfoParser.extractGameInfo(filePath);
                if (getActivity() != null && isAdded()) {
                    getActivity().runOnUiThread(() -> {
                        if (gameInfo != null) {
                            gameName = gameInfo.name;
                            gameVersion = gameInfo.version;
                            gameIconPath = gameInfo.iconPath;
                            if (getActivity() != null) {
                                ((MainActivity) getActivity()).showToast("检测到游戏: " + gameName + " " + gameVersion);
                            }
                            Log.d("LocalImportFragment", "Game info: " + gameInfo);
                            Log.d("LocalImportFragment", "Icon path: " + gameIconPath);
                        } else {
                            gameName = "未知游戏";
                            if (getActivity() != null) {
                                ((MainActivity) getActivity()).showToast("无法读取游戏信息，使用默认名称");
                            }
                        }
                        updateImportButtonState();
                    });
                }
            }).start();
        });
    }

    private void selectModLoaderFile() {
        openFileBrowser("modloader", new String[]{".zip"}, filePath -> {
            modLoaderFilePath = filePath;
            File file = new File(modLoaderFilePath);
            modLoaderFileText.setText("已选择: " + file.getName());
            updateImportButtonState();
        });
    }

    private interface FileChosen { void onChosen(String path); }

    private void openFileBrowser(String type, String[] exts, FileChosen cb) {
        FileBrowserFragment f = new FileBrowserFragment();
        f.setFileType(type, exts);
        f.setOnFileSelectedListener((filePath, fileType) -> {
            cb.onChosen(filePath);
            requireActivity().getSupportFragmentManager().popBackStack();
        });
        f.setOnBackListener(() -> requireActivity().getSupportFragmentManager().popBackStack());
        requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, f)
                .addToBackStack("file_browser")
                .commit();
    }

    private void updateImportButtonState() {
        boolean hasGameFile = gameFilePath != null && !gameFilePath.isEmpty();

        // 只需要游戏文件即可导入，ModLoader 是可选的
        startImportButton.setEnabled(hasGameFile);
        
        if (hasGameFile) {
            startImportButton.setAlpha(1.0f);
        } else {
            startImportButton.setAlpha(0.5f);
        }
    }

    private void startImport() {
        if (gameFilePath == null) {
            ((MainActivity) getActivity()).showToast("请先选择游戏文件");
            return;
        }

        // 根据是否选择了 ModLoader 来确定游戏类型
        boolean hasModLoader = modLoaderFilePath != null && !modLoaderFilePath.isEmpty();
        gameType = hasModLoader ? "modloader" : "game"; // 纯游戏类型为 "game"

        // 显示进度容器
        importProgressContainer.setVisibility(View.VISIBLE);
        startImportButton.setEnabled(false);

        // 添加日志检查gameName的值
        Log.d("LocalImportFragment", "startImport() - gameName: " + gameName);
        Log.d("LocalImportFragment", "startImport() - gameVersion: " + gameVersion);
        Log.d("LocalImportFragment", "startImport() - gameIconPath: " + gameIconPath);
        Log.d("LocalImportFragment", "startImport() - gameFilePath: " + gameFilePath);
        Log.d("LocalImportFragment", "startImport() - hasModLoader: " + hasModLoader);
        
        // 如果游戏信息丢失，重新解析
        if (gameName == null || gameVersion == null) {
            Log.w("LocalImportFragment", "Game info lost, re-parsing...");
            importStatus.setText("正在读取游戏信息...");
            
            new Thread(() -> {
                GameInfoParser.GameInfo gameInfo = GameInfoParser.extractGameInfo(gameFilePath);
                if (getActivity() != null && isAdded()) {
                    getActivity().runOnUiThread(() -> {
                        if (gameInfo != null) {
                            gameName = gameInfo.name;
                            gameVersion = gameInfo.version;
                            gameIconPath = gameInfo.iconPath;
                            Log.d("LocalImportFragment", "Re-parsed game info: " + gameName + " " + gameVersion);
                            Log.d("LocalImportFragment", "Re-parsed icon path: " + gameIconPath);
                            
                            // 继续导入
                            continueImport();
                        } else {
                            importStatus.setText("无法读取游戏信息");
                            startImportButton.setEnabled(true);
                            if (getActivity() != null) {
                                ((MainActivity) getActivity()).showToast("无法读取游戏信息，导入失败");
                            }
                        }
                    });
                }
            }).start();
            return;
        }
        
        continueImport();
    }
    
    private void continueImport() {
        // 创建游戏目录
        gameDir = createGameDirectory();
        String outputPath = gameDir.getAbsolutePath();
        Log.d("LocalImportFragment", "Created game directory: " + outputPath);
        
        // 复制图标到游戏目录
        if (gameIconPath != null && new File(gameIconPath).exists()) {
            try {
                File iconSource = new File(gameIconPath);
                File iconDest = new File(gameDir, "icon.png");
                
                java.io.FileInputStream fis = new java.io.FileInputStream(iconSource);
                java.io.FileOutputStream fos = new java.io.FileOutputStream(iconDest);
                byte[] buffer = new byte[8192];
                int read;
                while ((read = fis.read(buffer)) != -1) {
                    fos.write(buffer, 0, read);
                }
                fis.close();
                fos.close();
                
                // 更新图标路径为游戏目录中的路径
                gameIconPath = iconDest.getAbsolutePath();
                Log.d("LocalImportFragment", "Icon copied to: " + gameIconPath);
            } catch (Exception e) {
                Log.e("LocalImportFragment", "Failed to copy icon", e);
            }
        }

        // 检查是否有 ModLoader
        boolean hasModLoader = modLoaderFilePath != null && !modLoaderFilePath.isEmpty();
        
        if (hasModLoader) {
            // 有 ModLoader，使用完整导入逻辑
            GameExtractor.installCompleteGame(gameFilePath, modLoaderFilePath, outputPath,
                    new GameExtractor.ExtractionListener() {
                        @Override
                        public void onProgress(String message, int progress) {
                            if (getActivity() != null && isAdded()) {
                                getActivity().runOnUiThread(() -> {
                                    importStatus.setText(message);
                                    importProgress.setProgress(progress);
                                });
                            }
                        }

                        @Override
                        public void onComplete(String gamePath, String modLoaderPath) {
                            if (getActivity() != null && isAdded()) {
                                getActivity().runOnUiThread(() -> {
                                    importStatus.setText("导入完成！");
                                    importProgress.setProgress(100);

                                    // 构建 ModLoader.dll 的完整路径
                                File modLoaderDir = new File(modLoaderPath);
                                File assemblyFile = new File(modLoaderDir, "ModLoader.dll");
                                String finalGamePath = assemblyFile.getAbsolutePath();

                                // 验证程序集文件是否存在
                                if (!assemblyFile.exists()) {
                                    Log.w("LocalImportFragment", "ModLoader.dll not found at: " + finalGamePath);
                                    finalGamePath = modLoaderPath;
                                }
                                
                                // 保存游戏本体路径（Terraria.exe）
                                String gameBodyPath = null;
                                File gameBodyFile = new File(gamePath, "Terraria.exe");
                                if (gameBodyFile.exists()) {
                                    gameBodyPath = gameBodyFile.getAbsolutePath();
                                    Log.d("LocalImportFragment", "Game body path: " + gameBodyPath);
                                } else {
                                    Log.w("LocalImportFragment", "Terraria.exe not found at: " + gameBodyFile.getAbsolutePath());
                                }

                                Log.d("LocalImportFragment", "Final game path: " + finalGamePath);
                                Log.d("LocalImportFragment", "GameExtractor returned gamePath: " + gamePath);
                                Log.d("LocalImportFragment", "GameExtractor returned modLoaderPath: " + modLoaderPath);

                                // 导入完成，返回结果
                                if (importCompleteListener != null) {
                                    importCompleteListener.onImportComplete(gameType, gameName, finalGamePath, gameBodyPath, engineType, gameIconPath);
                                }
                            });
                            }
                        }

                        @Override
                        public void onError(String error) {
                            if (getActivity() != null && isAdded()) {
                                getActivity().runOnUiThread(() -> {
                                    importStatus.setText("导入失败: " + error);
                                    if (getActivity() != null) {
                                        ((MainActivity) getActivity()).showToast("导入失败: " + error);
                                    }
                                    startImportButton.setEnabled(true);
                                });
                            }
                        }
                    });
        } else {
            // 没有 ModLoader，只安装纯游戏
            GameExtractor.installGameOnly(gameFilePath, outputPath,
                    new GameExtractor.ExtractionListener() {
                        @Override
                        public void onProgress(String message, int progress) {
                            if (getActivity() != null && isAdded()) {
                                getActivity().runOnUiThread(() -> {
                                    importStatus.setText(message);
                                    importProgress.setProgress(progress);
                                });
                            }
                        }

                        @Override
                        public void onComplete(String gamePath, String modLoaderPath) {
                            if (getActivity() != null && isAdded()) {
                                getActivity().runOnUiThread(() -> {
                                    importStatus.setText("导入完成！");
                                    importProgress.setProgress(100);

                                    // 纯游戏，构建 Terraria.exe 的路径
                                File gameFile = new File(gamePath, "Terraria.exe");
                                String finalGamePath = gameFile.getAbsolutePath();
                                
                                if (!gameFile.exists()) {
                                    Log.w("LocalImportFragment", "Terraria.exe not found at: " + finalGamePath);
                                    finalGamePath = gamePath;
                                }

                                Log.d("LocalImportFragment", "Pure game path: " + finalGamePath);

                                // 导入完成，返回结果（纯游戏没有 gameBodyPath）
                                if (importCompleteListener != null) {
                                    importCompleteListener.onImportComplete(gameType, gameName, finalGamePath, null, engineType, gameIconPath);
                                }
                            });
                            }
                        }

                        @Override
                        public void onError(String error) {
                            if (getActivity() != null && isAdded()) {
                                getActivity().runOnUiThread(() -> {
                                    importStatus.setText("导入失败: " + error);
                                    if (getActivity() != null) {
                                        ((MainActivity) getActivity()).showToast("导入失败: " + error);
                                    }
                                    startImportButton.setEnabled(true);
                                });
                            }
                        }
                    });
        }
    }

    private File createGameDirectory() {
        File externalDir = MainActivity.mainActivity.getExternalFilesDir(null);

        // 创建更结构化的目录
        File gamesDir = new File(externalDir, "games");
        if (!gamesDir.exists()) {
            gamesDir.mkdirs();
        }

        // 使用游戏名称和版本号（如果有）
        String dirName = gameName != null ? gameName : "Unknown";
        if (gameVersion != null) {
            dirName += "_" + gameVersion;
        }
        dirName += "_" + System.currentTimeMillis();

        File gameDir = new File(gamesDir, dirName);
        if (!gameDir.exists()) {
            gameDir.mkdirs();
        }

        return gameDir;
    }
}
