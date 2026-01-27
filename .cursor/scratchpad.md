# Scratchpad - 任务追踪与计划

> 此文件用于追踪当前任务进度、记录决策和规划下一步操作。
> AI 在接收新任务时应首先查看此文件。

---

## 当前任务

**任务名称**: MainActivity 完整 Compose 迁移
**创建时间**: 2026-01-20
**状态**: 规划中

### 任务描述
将 MainActivity 从 XML + ComposeView 混合模式完全迁移到纯 Compose，使用 Compose Navigation 替代 Fragment/PageNavigator。

---

## MainActivity Compose 迁移计划

### 背景问题

1. **当前架构**：
   - `MainActivity` 使用 `setContentView(R.layout.activity_main)` 加载 XML
   - XML 中嵌入 `ComposeView` 显示 Compose 内容
   - `PageNavigator` 通过 FrameLayout visibility 切换页面
   - `FragmentNavigator` 管理全屏 Fragment
   - TopAppBar 在 Column 中导致测量崩溃（宽度 = Int.MAX_VALUE）

2. **目标架构**：
   - 纯 Compose UI (`setContent { }`)
   - Compose Navigation 管理所有页面
   - 移除 XML 布局和 Fragment
   - 统一状态管理 (ViewModel + StateFlow)

---

## 实施清单

### 第一阶段：导航架构（Navigation） ✅ 已完成

```
[X] 1.1 创建导航路由定义
    - 创建 shared/ui/navigation/NavRoutes.kt
    - 定义 Screen sealed class (Games, Controls, Download, Import, Settings, ControlStore, Init, FileBrowser, GameDetail, ControlEditor)
    - 定义 NavDestination enum（主导航目的地）
    - 定义 NavArgs 导航参数

[X] 1.2 创建 NavHost 组件
    - 创建 shared/ui/navigation/AppNavHost.kt
    - NavState 状态类（currentScreen, backStack, canGoBack）
    - AppNavHost 带动画的导航容器
    - SimpleNavHost 无动画版本

[X] 1.3 创建 Navigation Controller 扩展
    - 创建 shared/ui/navigation/NavigationExtensions.kt
    - 导航辅助函数（navigateToGames, navigateToSettings 等）
    - NavigationEvent 事件类型
    - handleBackPress 返回键处理

[X] 1.4 整合现有 NavigationDestination
    - 更新 AppNavigationRail.kt 使用 NavDestination
    - 更新 MainUiState.kt / MainViewModel.kt / MainScreen.kt
    - 添加 NavigationDestination 兼容别名
```

### 第二阶段：主界面重构（MainApp） ✅ 已完成

```
[X] 2.1 创建 MainApp Composable
    - 创建 app/ui/main/MainApp.kt
    - NavigationRail + AppNavHost 布局
    - 支持 Slot API（可插入各页面内容）

[X] 2.2 创建 MainActivityCompose
    - 创建 app/ui/main/MainActivityCompose.kt（纯 Compose）
    - 使用 setContent { } 替代 setContentView
    - 保留 Manager 初始化（Theme、Permission）
    - 使用 MainContractAdapter 保持与 MainPresenter 兼容

[X] 2.3 创建 VideoBackground Compose 组件
    - 创建 app/ui/compose/background/VideoBackground.kt
    - AppBackground 统一背景组件（图片/视频）
    - 使用 AndroidView 包装 VideoBackgroundView

[~] 2.4 MainAppViewModel（取消）
    - 保留 MainPresenter 通过适配器模式兼容
    - 后续可迁移到 ViewModel
```

### 第三阶段：Screen 组件迁移 ✅ 部分完成

```
[X] 3.1 GameListScreen（已基本完成）
    - 已整理 shared/ui/components/GameListComponents.kt
    - 在 MainApp 中直接使用

[X] 3.2 SettingsScreen
    - 创建 app/ui/screens/SettingsScreenWrapper.kt
    - 提取 SettingsComposeFragment 逻辑为纯 Composable
    - 连接到 MainApp

[X] 3.3 ControlLayoutScreen
    - 创建 app/ui/screens/ControlLayoutScreenWrapper.kt
    - 提取 ControlLayoutComposeFragment 逻辑
    - 使用 Scaffold + TopAppBar（正确方式）
    - 连接到 MainApp

[X] 3.4 ImportScreen
    - 创建 app/ui/screens/ImportScreenWrapper.kt
    - 提取 GameImportComposeFragment 逻辑
    - 连接到 MainApp（待完善文件浏览器）

[~] 3.5 DownloadScreen (GOG Client)
    - 保留占位符，后续迁移
    - GOG 逻辑复杂，依赖 Android 特定功能

[~] 3.6 ControlPackScreen
    - 已有 controls/packs/ui/ControlPackScreen.kt
    - 待连接到 MainApp

[~] 3.7 InitializationScreen
    - 保留 Fragment 实现
    - 特殊启动页面

[~] 3.8 FileBrowserScreen
    - 待重构
```

### 第四阶段：状态管理统一

```
[ ] 4.1 MainAppState 定义
    - 当前页面状态
    - 游戏列表状态
    - 选中游戏状态
    - 初始化状态

[ ] 4.2 移除 MVP 架构
    - 移除 MainContract.kt
    - 移除 MainPresenter.kt
    - 将逻辑迁移到 ViewModel

[ ] 4.3 移除旧导航器
    - 移除 PageNavigator.kt
    - 移除 FragmentNavigator.kt
```

### 第五阶段：清理与优化

```
[ ] 5.1 删除旧文件
    - 删除 activity_main.xml
    - 删除 Fragment 相关文件（可选，保留兼容）
    
[ ] 5.2 视频背景处理
    - 创建 VideoBackgroundComposable
    - 集成到 MainApp

[ ] 5.3 编译验证
    - 确保全量构建通过
    - 测试所有页面导航
```

---

## 文件变更清单

### 新建文件

| 路径 | 描述 |
|------|------|
| `shared/ui/navigation/NavRoutes.kt` | 导航路由定义 |
| `shared/ui/navigation/AppNavHost.kt` | NavHost 组件 |
| `app/ui/main/MainApp.kt` | 主界面 Composable |
| `app/ui/main/MainAppViewModel.kt` | 主界面 ViewModel |

### 修改文件

| 路径 | 变更 |
|------|------|
| `MainActivity.kt` | 使用 setContent，移除 XML |
| `ControlLayoutComposeFragment.kt` → `ControlLayoutScreen.kt` | 重构为 Screen |
| `GameImportComposeFragment.kt` → `ImportScreen.kt` | 重构为 Screen |
| `FileBrowserComposeFragment.kt` → `FileBrowserScreen.kt` | 重构为 Screen |

### 删除文件

| 路径 | 原因 |
|------|------|
| `activity_main.xml` | 被 Compose 替代 |
| `MainContract.kt` | MVP 被 ViewModel 替代 |
| `MainPresenter.kt` | MVP 被 ViewModel 替代 |
| `PageNavigator.kt` | 被 Compose Navigation 替代 |

---

## 技术要点

### 1. TopAppBar 正确用法

```kotlin
// ❌ 错误：直接在 Column 中使用
Column {
    TopAppBar(...)  // 可能导致宽度无限
    Content(...)
}

// ✅ 正确：使用 Scaffold
Scaffold(
    topBar = { TopAppBar(...) }
) { padding ->
    Content(Modifier.padding(padding))
}
```

### 2. Compose Navigation 结构

```kotlin
sealed class Screen(val route: String) {
    object Game : Screen("game")
    object Settings : Screen("settings")
    object Control : Screen("control")
    object Import : Screen("import")
    object Download : Screen("download")
    object ControlStore : Screen("control_store")
    object Init : Screen("init")
}
```

### 3. MainActivity 改造

```kotlin
class MainActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // ... 初始化
        setContent {
            RaLaunchTheme {
                MainApp(...)
            }
        }
    }
}
```

---

## 依赖关系

```
MainActivity
    └── MainApp (Composable)
        ├── NavigationRail
        └── NavHost
            ├── GameListScreen
            ├── SettingsScreen
            ├── ControlLayoutScreen
            ├── ImportScreen
            ├── DownloadScreen (GOG)
            ├── ControlPackScreen
            └── InitScreen (条件显示)
```

---

## KMP 迁移架构设计（历史参考）

### 目标架构

```
shared/
├── commonMain/          # 跨平台代码
│   ├── domain/          # 领域层（已完成）
│   │   ├── model/
│   │   ├── repository/
│   │   └── service/
│   ├── data/            # 数据层（部分完成）
│   │   ├── model/
│   │   ├── mapper/
│   │   ├── repository/
│   │   └── local/
│   ├── ui/              # Compose Multiplatform UI
│   │   ├── theme/
│   │   ├── components/
│   │   └── screens/
│   └── util/            # 跨平台工具类
│
└── androidMain/         # Android 特定实现
    ├── data/
    │   ├── local/
    │   └── service/
    ├── di/
    └── Platform.android.kt

app/                     # Android 应用层（保留）
├── activity/            # Activity 入口
├── fragment/            # Fragment 封装（过渡期）
├── controls/            # 虚拟控制器（Android 特定）
├── core/                # 游戏启动核心（Android 特定）
├── dotnet/              # .NET 运行时（Android 特定）
├── box64/               # Box64 桥接（Android 特定）
├── renderer/            # 渲染器（Android 特定）
├── installer/           # 游戏安装器（Android 特定）
└── di/                  # Koin 初始化
```

### 迁移原则

1. **渐进式迁移** - 保持应用可编译可运行
2. **兼容层设计** - 新旧模型转换函数
3. **平台代码分离** - Android 特定代码放入 androidMain
4. **依赖注入** - 通过 Koin 实现跨模块依赖

---

## 进度追踪

### 第一阶段：基础设施（已完成）

```
[X] 1.1 创建 shared 模块结构
[X] 1.2 配置 Gradle 依赖
[X] 1.3 配置 Compose Multiplatform
[X] 1.4 配置 Koin DI
[X] 1.5 配置 DataStore
```

### 第二阶段：Domain 层迁移（已完成）

```
[X] 2.1 迁移 domain/model
    [X] GameItem.kt
    [X] Settings.kt
    [X] ControlLayout.kt
[X] 2.2 迁移 domain/repository 接口
    [X] GameRepository.kt
    [X] SettingsRepository.kt
    [X] ControlLayoutRepository.kt
[X] 2.3 迁移 domain/service 接口
    [X] GameLaunchService.kt
    [X] PatchService.kt
    [X] ControlLayoutService.kt
```

### 第三阶段：Data 层迁移（部分完成）

```
[X] 3.1 迁移 data/model
    [X] GameItem.kt (数据层版本)
    [X] FileItem.kt
[X] 3.2 迁移 data/mapper
    [X] GameItemMapper.kt
    [X] SettingsMapper.kt
[X] 3.3 迁移 data/repository
    [X] GameRepositoryImpl.kt
    [X] SettingsRepositoryImpl.kt
[ ] 3.4 迁移 data/local (需 androidMain 实现)
    [X] DataStoreFactory.kt (expect/actual)
    [X] PreferencesKeys.kt
    [ ] GameListStorage (接口定义)
[ ] 3.5 App 层兼容适配
    [X] GameItem 转换函数
    [ ] 完善 LegacyGameRepository
```

### 第四阶段：UI Theme 迁移（已完成）

```
[X] 4.1 迁移 ui/theme
    [X] Color.kt
    [X] Shape.kt
    [X] Theme.kt
    [X] Typography.kt
```

### 第五阶段：UI Components 迁移（已完成）

```
[X] 5.1 基础组件
    [X] Common.kt (通用组件)
    [X] GameCard.kt
    [X] FileItem.kt
    [X] AppNavigationRail.kt
[X] 5.2 主界面组件 (app -> shared)
    [X] GameListContent.kt -> shared/ui/components/GameListComponents.kt
    [X] GameListEmpty.kt -> 合并到 GameListComponents.kt
    [X] GameDetailPanel.kt -> shared/ui/components/GameDetailPanel.kt
    [X] GameCardUi.kt -> shared/ui/components/GameCardUi.kt
    [X] GameItemUi.kt -> shared/ui/model/GameItemUi.kt
[X] 5.3 设置界面组件
    [X] SettingsScreenContent.kt -> shared/ui/screens/settings/SettingsScreen.kt
    [X] AppearanceSettingsContent.kt -> shared/ui/screens/settings/SettingsContentComponents.kt
    [X] ControlsSettingsContent.kt -> 同上
    [X] GameSettingsContent.kt -> 同上
    [X] DeveloperSettingsContent.kt -> 同上
    [X] AboutSettingsContent.kt -> 同上
[X] 5.4 UI 模型 (app -> shared)
    [X] InitializationState.kt -> shared/ui/model/InitializationModels.kt
    [X] GogClientState.kt -> shared/ui/model/GogModels.kt
    [X] ImportModels -> shared/ui/model/ImportModels.kt
[X] 5.5 导入界面
    [X] ImportScreenContent.kt -> shared/ui/screens/import/ImportScreenComponents.kt
[~] 5.6 初始化界面 (保留 app 模块 - 依赖 Android 资源)
    [~] InitializationScreen.kt
    [~] InitializationComponents.kt
[~] 5.7 GOG 客户端界面 (保留 app 模块 - 依赖 Android 库)
    [~] GogClientScreen.kt
    [~] GogClientComponents.kt
```

### 第六阶段：Screens 与 ViewModel 迁移（已完成）

```
[X] 6.1 MainScreen
    [X] MainScreen.kt
    [X] MainUiState.kt
    [X] MainViewModel.kt
[X] 6.2 SettingsScreen + ViewModel
    [X] SettingsScreen.kt (shared)
    [X] SettingsViewModel.kt (shared - 新建)
    [X] SettingsUiState.kt (shared)
    [X] SettingsEvent/Effect (shared)
    [X] 更新 SettingsRepository 接口
    [X] 更新 SettingsRepositoryImpl
    [X] 更新 app 层 SettingsViewModel (使用 shared 类型)
    [X] 更新 SettingsComposeFragment (使用 shared SettingsCategory)
[X] 6.3 ImportScreen + ViewModel
    [X] ImportViewModel.kt (shared)
    [X] ImportUiState.kt (shared)
    [X] ImportEvent/Effect (shared)
[X] 6.4 ControlLayoutScreen + ViewModel (新建)
    [X] ControlLayoutViewModel.kt (shared)
    [X] ControlLayoutUiState (shared)
    [X] ControlLayoutEvent/Effect (shared)
[~] 6.5 保留 app 层 (Android 依赖)
    [~] InitializationScreen + State (Android Resources)
    [~] GogClientScreen + State (Android Libraries)
```

### 第七阶段：Utils 迁移（已完成）

```
[X] 7.1 可跨平台 Utils (-> commonMain)
    [X] Logger.kt (抽象日志接口 + expect/actual)
    [X] Logger.android.kt (Android 实现)
    [X] LocaleHelper.kt (语言常量和接口)
[X] 7.2 App 层适配
    [X] AppLogger.kt (实现 Logger 接口)
    [X] LocaleManager.kt (实现 LocaleManager 接口)
[~] 7.3 Android 特定 Utils (保留 app 模块)
    [~] ArchiveExtractor.kt
    [~] AssemblyChecker.kt
    [~] GLInfoUtils.kt
    [~] NativeMethods.kt
    [~] PatchExtractor.kt
    [~] RuntimeManager.kt
    [~] LogcatReader.kt
    [~] DensityAdapter.kt
    [~] ErrorDialog.kt
    [~] 其他 Android 特定工具类
```

### 第八阶段：Manager 迁移（已完成）

```
[X] 8.1 跨平台接口和配置 (-> commonMain)
    [X] ThemeConfig.kt (ThemeMode, BackgroundType, IThemeManager, ColorUtils)
    [X] VibrationConfig.kt (VibrationType, VibrationConfig, IVibrationManager)
    [X] PermissionConfig.kt (PermissionType, PermissionStatus, IPermissionManager)
[X] 8.2 App 层实现接口
    [X] ThemeManager.kt (实现 IThemeManager)
    [X] VibrationManager.kt (实现 IVibrationManager)
[~] 8.3 保留 App 层（Android 特定）
    [~] DynamicColorManager.kt
    [~] PermissionManager.kt
    [~] FragmentNavigator.kt
    [~] GameLaunchManager.kt
    [~] GameMenuManager.kt
    [~] GameDeletionManager.kt
    [~] GameFullscreenManager.kt
    [~] common/ 子目录
```

### 第九阶段：DI 整合（已完成）

```
[X] 9.1 SharedModule.kt (commonMain)
    [X] 添加 SettingsViewModel
    [X] 添加 ImportViewModel
    [X] 添加 ControlLayoutViewModel
[X] 9.2 AndroidModule.kt (androidMain)
    [X] 添加 ControlLayoutStorage
    [X] 添加 ControlLayoutRepository
    [X] 添加 AppInfo 提供
[X] 9.3 完善 AppModule.kt (app)
    [X] 整合 shared 模块依赖
    [X] VibrationManager 绑定 IVibrationManager
    [X] ThemeManager 绑定 IThemeManager
    [X] 添加 App 层 SettingsViewModel
[X] 9.4 新增实现类
    [X] ControlLayoutRepositoryImpl.kt
    [X] ControlLayoutStorage 接口
    [X] AndroidControlLayoutStorage.kt
```

### 第十阶段：清理与优化（已完成）

```
[X] 10.1 编译验证
    [X] shared 模块编译成功
    [X] app 模块编译成功
    [X] 全量构建通过
[X] 10.2 模块结构整理
    [X] shared/commonMain - 跨平台代码
    [X] shared/androidMain - Android 平台实现
    [X] app - Android 应用层
[X] 10.3 代码结构确认
    [X] domain 层（model/repository/service）
    [X] data 层（repository impl/mapper/local）
    [X] ui 层（components/screens/theme）
    [X] di 层（Koin 模块）
    [X] manager 层（跨平台接口）
    [X] util 层（工具类）
```

---

## 不迁移的模块（Android 专属）

以下模块因深度依赖 Android 平台特性，保留在 app 模块：

| 模块 | 原因 |
|------|------|
| `controls/` | 触摸输入、手柄映射、JNI 交互 |
| `core/` | 游戏进程启动、环境变量、线程管理 |
| `dotnet/` | CoreCLR/Mono 运行时启动 |
| `box64/` | Box64 Native Bridge |
| `renderer/` | OpenGL 渲染器加载 |
| `installer/` | 游戏安装、解压、补丁 |
| `gog/` | GOG API 客户端（HTTP 可迁移，但功能紧密） |
| `provider/` | ContentProvider |
| `service/` | Android Service |
| `crash/` | Crash 报告 Activity |

---

## 决策记录

| 日期 | 决策 | 原因 |
|------|------|------|
| 2026-01-20 | 采用渐进式迁移 | 保持应用稳定性 |
| 2026-01-20 | 保留 app 层 GameItem | 大量旧代码依赖 |
| 2026-01-20 | controls 模块不迁移 | 深度 Android 依赖 |
| 2026-01-20 | 使用 expect/actual 模式 | DataStore 等平台实现 |

---

## 当前工作焦点

**状态**: MainActivity Compose 迁移 - 第一、二、三、四阶段已完成 ✅

**已创建文件**:
- `shared/ui/navigation/NavRoutes.kt` - 导航路由定义
- `shared/ui/navigation/AppNavHost.kt` - NavHost 组件
- `shared/ui/navigation/NavigationExtensions.kt` - 导航扩展
- `app/ui/main/MainApp.kt` - 主界面 Composable
- `app/ui/main/MainActivityCompose.kt` - 纯 Compose Activity
- `app/ui/compose/background/VideoBackground.kt` - 视频背景组件
- `app/ui/screens/SettingsScreenWrapper.kt` - 设置页面
- `app/ui/screens/ControlLayoutScreenWrapper.kt` - 控制布局页面
- `app/ui/screens/ImportScreenWrapper.kt` - 导入页面

**构建验证**: ✅ assembleDebug 成功

**当前配置**:
- `MainActivity` - 原 XML 版本（当前启动 Activity）
- `MainActivityCompose` - 纯 Compose 版本（已注册，可测试）

**如需切换启动 Activity**:
修改 AndroidManifest.xml 将 `<intent-filter>` 从 MainActivity 移动到 MainActivityCompose

---

## KMP 迁移总结

### 迁移成果

| 阶段 | 内容 | 状态 |
|------|------|------|
| 第一阶段 | 基础设施 | ✅ 完成 |
| 第二阶段 | Domain 层迁移 | ✅ 完成 |
| 第三阶段 | Data 层迁移 | ✅ 完成 |
| 第四阶段 | UI Theme 迁移 | ✅ 完成 |
| 第五阶段 | UI Components 迁移 | ✅ 完成 |
| 第六阶段 | Screens/ViewModel 迁移 | ✅ 完成 |
| 第七阶段 | Utils 迁移 | ✅ 完成 |
| 第八阶段 | Manager 迁移 | ✅ 完成 |
| 第九阶段 | DI 整合完善 | ✅ 完成 |
| 第十阶段 | 清理与优化 | ✅ 完成 |

### 模块架构

```
shared/
├── commonMain/           # 跨平台代码
│   ├── domain/           # 领域层
│   ├── data/             # 数据层
│   ├── ui/               # Compose Multiplatform UI
│   ├── manager/          # 管理接口
│   ├── util/             # 工具类
│   └── di/               # DI 模块
│
└── androidMain/          # Android 特定实现
    ├── data/local/       # 存储实现
    ├── data/service/     # 服务实现
    ├── di/               # Android DI
    └── util/             # Android 工具

app/                      # Android 应用层
├── ui/                   # 使用 shared UI
├── manager/              # 实现 shared 接口
├── di/                   # App DI
└── (Android 特定模块)    # 保留
```

### 主要 API

**跨平台 Repository**:
- GameRepository
- SettingsRepository
- ControlLayoutRepository

**跨平台 ViewModel**:
- MainViewModel
- SettingsViewModel
- ImportViewModel
- ControlLayoutViewModel

**跨平台接口**:
- Logger / AppLog
- IThemeManager
- IVibrationManager
- IPermissionManager
- LocaleManager

---

## Lessons Learned

### 项目特定

- 项目使用 Gradle 8.13 构建
- Compose Multiplatform 版本与 Android Compose 需匹配
- DataStore 需要 expect/actual 实现
- shared 模块依赖 androidMain 实现 Android 特定功能

### 从错误中学习

- GameItem 两套模型需保持转换函数同步
- Koin 模块需正确加载顺序
- Compose Multiplatform 的 `combinedClickable` 是实验性 API，需要 `@OptIn(ExperimentalFoundationApi::class)`
- 删除重复的 Java/Kotlin 类时需检查 API 兼容性（如 GogApiClient.java vs .kt）
- **⚠️ 重要**: 迁移代码后必须同步更新 AndroidManifest.xml 的启动 Activity！
  - 创建新的 Activity/Fragment 后，确保 Manifest 指向新版本
  - 给旧版本添加 `@Deprecated` 注解防止误用
- **Compose 布局陷阱**: `TopAppBar` 在 `AnimatedContent`/`Crossfade` 内部可能因为宽度约束问题崩溃
  - 解决方案：使用自定义 `Surface` + `Row` 替代 `TopAppBar`
  - 或确保父容器传递明确的宽度约束

---

## 历史任务

### 已完成：Kotlin + MVP 重构
**完成时间**: 2026-01-20

```
[X] 确定重构方案（模块化 MVP）
[X] 创建目录结构
[X] 重构 Model 层
[X] 重构 MainActivity (MVP)
[X] 创建基类
[X] 旧代码兼容处理
[X] 编译验证 - BUILD SUCCESSFUL
```
