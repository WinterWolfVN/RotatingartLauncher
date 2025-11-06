# 运行时切换弹窗美化指南

## 📱 全新的运行时选择体验

已完成对 .NET 运行时版本选择功能的全面美化和动画升级，提供更加流畅和现代的用户体验。

---

## ✨ 新特性

### 1. **美观的卡片式按钮**
- ✅ 替换了原来的 Spinner 下拉框
- ✅ 使用 MaterialCardView 卡片设计
- ✅ 显示当前选中的运行时版本
- ✅ 紫色边框和图标，视觉突出
- ✅ 点击按钮触发对话框

### 2. **精美的对话框设计**
- ✅ 24dp 圆角卡片设计
- ✅ 16dp 阴影效果
- ✅ 带标题和关闭按钮
- ✅ 显示当前版本状态
- ✅ 列表式版本选择
- ✅ 确定/取消按钮

### 3. **流畅的动画效果**

#### 进入动画
- 缩放动画：0.8x → 1.0x
- 淡入效果：透明 → 不透明
- 持续时间：300ms
- 缓动函数：DecelerateInterpolator

#### 退出动画
- 缩放动画：1.0x → 0.8x
- 淡出效果：不透明 → 透明
- 持续时间：250ms
- 缓动函数：AccelerateInterpolator

#### 交互动画
- 按钮点击：脉冲动画（Pulse）
- 版本选择：缩放反馈（0.95x → 1.0x）
- 版本切换：放大缩小动画（1.0x → 1.2x → 1.0x）
- 列表微动：轻微脉冲效果

### 4. **版本列表美化**
- ✅ 圆形图标背景
- ✅ 版本名称和描述信息
- ✅ 选中状态高亮显示
- ✅ 紫色边框标识选中项
- ✅ 勾选图标指示当前版本

---

## 🎨 UI 组件

### 主界面按钮
**位置**: `activity_main.xml` - 右侧游戏信息区域

```xml
<MaterialCardView>
    - 图标: ic_settings (紫色圆形背景)
    - 标题: ".NET 运行时"
    - 当前版本: 动态显示
    - 箭头: ic_arrow_forward
</MaterialCardView>
```

### 对话框布局
**文件**: `dialog_runtime_selector.xml`

#### 结构
```
MaterialCardView (24dp圆角)
├── 标题栏
│   ├── 图标 (ic_settings)
│   ├── 标题 (".NET 运行时版本")
│   └── 关闭按钮 (ic_close)
├── 当前版本显示
│   └── 带勾选图标的卡片
├── 版本列表 (RecyclerView)
│   └── item_runtime_version.xml
└── 操作按钮
    ├── 取消
    └── 确定 (紫色)
```

### 版本列表项
**文件**: `item_runtime_version.xml`

```
MaterialCardView (12dp圆角)
├── 图标 (圆形背景)
├── 版本信息
│   ├── 版本名称 (.NET 8.0.0)
│   └── 版本描述 (推荐版本/LTS等)
└── 选中图标 (勾选)
```

---

## 🎬 动画资源

### 对话框动画
**文件**: `anim/dialog_enter.xml` 和 `anim/dialog_exit.xml`

- **进入动画**: 缩放 + 淡入
- **退出动画**: 缩放 + 淡出
- **时长**: 300ms / 250ms
- **插值器**: DecelerateInterpolator / AccelerateInterpolator

### 交互动画
**代码实现**: `RuntimeSelectorDialog.java`

- **按钮点击**: YoYo Pulse 动画
- **版本选择**: ValueAnimator 脉冲效果
- **项目点击**: View.animate() 缩放反馈

---

## 📝 主要文件

### 布局文件
| 文件 | 说明 |
|------|------|
| `dialog_runtime_selector.xml` | 运行时选择对话框主布局 |
| `item_runtime_version.xml` | 版本列表项布局 |
| `activity_main.xml` | 主界面运行时按钮 |

### Java 文件
| 文件 | 说明 |
|------|------|
| `RuntimeSelectorDialog.java` | 对话框逻辑和适配器 |
| `MainActivity.java` | 显示对话框和处理回调 |

### Drawable 资源
| 文件 | 说明 |
|------|------|
| `ic_close.xml` | 关闭图标 |
| `ic_arrow_forward.xml` | 右箭头图标 |
| `bg_circle.xml` | 圆形背景 |

### 动画资源
| 文件 | 说明 |
|------|------|
| `dialog_enter.xml` | 进入动画 |
| `dialog_exit.xml` | 退出动画 |

### 样式资源
| 样式 | 说明 |
|------|------|
| `RuntimeDialogStyle` | 对话框主题 |
| `DialogAnimation` | 动画样式 |

---

## 🔄 工作流程

### 1. 初始化
```java
setupRuntimeSelector()
├── 检查运行时版本
├── 显示当前版本
└── 设置点击监听
```

### 2. 点击事件
```java
btnRuntimeSelector.onClick
├── Pulse 动画
└── 显示对话框
```

### 3. 对话框显示
```java
RuntimeSelectorDialog.show()
├── 加载版本列表
├── 显示当前版本
├── 设置 RecyclerView
└── 进入动画
```

### 4. 版本选择
```java
onVersionItemClicked()
├── 更新选中状态
├── 高亮边框
├── 显示勾选图标
└── 轻微动画反馈
```

### 5. 确认切换
```java
onConfirmClicked()
├── 保存新版本
├── 更新主界面显示
├── 版本切换动画
└── 关闭对话框
```

---

## 🎯 版本信息标识

根据版本号自动显示描述：

| 版本号 | 描述 |
|--------|------|
| 10.x.x | 最新版本 - 推荐使用 |
| 9.x.x | 稳定版本 - 推荐使用 |
| 8.x.x | 长期支持版本 (LTS) |
| 7.x.x | 旧版本 - 兼容性好 |
| 其他 | 兼容版本 |

---

## 🎨 主题适配

### 浅色主题
- 对话框背景：`?attr/backgroundCard` (#FAFAFA)
- 文本颜色：`?attr/textPrimary` (#000000)
- 强调色：`@color/accent_primary` (#BB86FC)
- 卡片背景：`?attr/backgroundSecondary` (#F5F5F5)

### 深色主题
- 对话框背景：`?attr/backgroundCard` (#1D1D1D)
- 文本颜色：`?attr/textPrimary` (#FFFFFF)
- 强调色：`@color/accent_primary` (#BB86FC)
- 卡片背景：`?attr/backgroundSecondary` (#121212)

---

## ⚡ 性能优化

### RecyclerView 优化
- ✅ ViewHolder 模式
- ✅ 局部刷新（notifyDataSetChanged）
- ✅ 最大高度限制（300dp）
- ✅ 垂直滚动支持

### 动画优化
- ✅ 硬件加速
- ✅ 短时长动画（100-300ms）
- ✅ 适当的插值器
- ✅ withEndAction 回调清理

---

## 📱 用户体验

### 交互反馈
✅ **即时反馈**: 所有点击都有动画响应
✅ **视觉引导**: 清晰的选中状态和边框
✅ **流畅过渡**: 平滑的进入/退出动画
✅ **信息明确**: 版本描述和当前状态显示

### 易用性
✅ **一键切换**: 点击按钮即可选择
✅ **确认机制**: 点击确定才生效
✅ **取消选项**: 可随时取消操作
✅ **快速关闭**: 多种关闭方式（关闭按钮/取消/对话框外点击）

---

## 🚀 与原版对比

| 特性 | 原版 Spinner | 新版对话框 |
|------|-------------|-----------|
| UI 设计 | 系统默认下拉框 | 精美卡片对话框 |
| 动画效果 | 无 | 丰富的进入/退出/交互动画 |
| 视觉反馈 | 基础 | 多层次动画反馈 |
| 信息展示 | 仅版本号 | 版本号 + 描述信息 |
| 选中状态 | 不明显 | 紫色边框 + 勾选图标 |
| 用户体验 | 传统 | 现代、流畅、美观 |

---

## 📝 使用示例

### 显示对话框
```java
RuntimeSelectorDialog dialog = new RuntimeSelectorDialog();
dialog.setOnVersionSelectedListener(version -> {
    // 处理版本切换
    tvCurrentRuntime.setText(".NET " + version);
});
dialog.show(getSupportFragmentManager(), "RuntimeSelectorDialog");
```

### 更新版本显示
```java
String currentVersion = RuntimeManager.getSelectedVersion(context);
tvCurrentRuntime.setText(".NET " + currentVersion);
```

---

**最后更新**: 2025-11-05

**特色**: 🎨 美观 | ⚡ 流畅 | 🎬 动画丰富 | 📱 现代化设计




