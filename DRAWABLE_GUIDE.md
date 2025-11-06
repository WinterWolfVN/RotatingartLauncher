# Drawable 使用指南

## 统一主题 Drawable 系统

本项目已统一所有 drawable 资源，使用主题属性自动适配亮色/深色模式。

---

## 📦 背景 Drawable

### 1. **bg_card_8dp.xml** - 标准卡片背景
- **用途**: 一般卡片、容器背景
- **样式**: 8dp 圆角，无边框
- **颜色**: `?attr/backgroundCard`（主题自适应）
- **适用场景**: 
  - 游戏信息卡片
  - 设置选项容器
  - 一般性内容区域

### 2. **bg_card_16dp.xml** - 大圆角卡片背景
- **用途**: 需要更圆润的卡片
- **样式**: 16dp 圆角，无边框
- **颜色**: `?attr/backgroundCard`（主题自适应）
- **适用场景**: 
  - 主要内容卡片
  - 对话框背景
  - 强调性区域

### 3. **bg_input.xml** - 输入框背景
- **用途**: 输入框、下拉框、弹出菜单
- **样式**: 8dp 圆角，带边框
- **颜色**: 
  - 背景: `?attr/backgroundCard`
  - 边框: `?attr/dividerColor`
- **适用场景**: 
  - EditText
  - Spinner
  - PopupMenu
  - 任何需要边框的输入控件

### 4. **bg_rounded_transparent.xml** - 透明圆角背景
- **用途**: 配合 `backgroundTint` 使用
- **样式**: 8dp 圆角，透明填充
- **颜色**: 透明 `#00000000`
- **适用场景**: 
  - 需要动态着色的按钮
  - 配合 `android:backgroundTint` 属性
  - 主题色按钮（如启动游戏按钮）

### 5. **bg_main.xml** - 主背景渐变
- **用途**: 应用主背景
- **样式**: 45度渐变
- **颜色**: 
  - 浅色模式: `#DBE9FD → #FFFFFF → #DCD9FF`（蓝紫渐变）
  - 深色模式: `#000000 → #121212 → #1A1A1A`（黑灰渐变）
- **适用场景**: 
  - Activity 根布局背景

### 6. **bg_circle.xml** - 圆形背景
- **用途**: 圆形图标背景
- **适用场景**: 
  - 圆形头像
  - 圆形按钮

---

## 🔘 按钮 Drawable

### 7. **selector_button.xml** - 主按钮选择器
- **用途**: 主要操作按钮
- **样式**: 12dp 圆角，带渐变和状态
- **颜色**: 紫色 `@color/accent_primary`
- **状态**: 
  - 按下: 紫色渐变
  - 启用: 紫色渐变
  - 禁用: 灰色
- **适用场景**: 
  - 确认按钮
  - 提交按钮
  - 主要操作

### 8. **secondary_button.xml** - 次要按钮选择器
- **用途**: 次要操作按钮
- **样式**: 12dp 圆角，带状态
- **颜色**: 根据主题自适应
- **状态**: 
  - 按下: `button_secondary`
  - 启用: `background_card`
  - 禁用: `background_sidebar`
- **适用场景**: 
  - 取消按钮
  - 次要操作
  - 工具按钮

---

## 🎨 主题颜色属性

所有 drawable 使用以下主题属性，自动适配亮色/深色模式：

### 背景色
- `?attr/backgroundPrimary` - 主背景
- `?attr/backgroundSecondary` - 次要背景
- `?attr/backgroundCard` - 卡片背景
- `?attr/backgroundSidebar` - 侧边栏背景

### 文本色
- `?attr/textPrimary` - 主文本
- `?attr/textSecondary` - 次要文本
- `?attr/textHint` - 提示文本

### 其他
- `?attr/dividerColor` - 分隔线颜色
- `@color/accent_primary` - 强调色（紫色 #BB86FC）
- `@color/accent_secondary` - 次要强调色（深紫 #9965F4）

---

## 📝 使用示例

### 示例 1: 普通卡片
```xml
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="@drawable/bg_card_8dp"
    android:padding="16dp">
    <!-- 内容 -->
</LinearLayout>
```

### 示例 2: 输入框
```xml
<EditText
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="@drawable/bg_input"
    android:padding="12dp" />
```

### 示例 3: 动态着色按钮
```xml
<Button
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:background="@drawable/bg_rounded_transparent"
    android:backgroundTint="@color/accent_primary"
    android:text="启动游戏" />
```

### 示例 4: CardView
```xml
<androidx.cardview.widget.CardView
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:cardCornerRadius="16dp"
    app:cardBackgroundColor="?attr/backgroundCard">
    <!-- 内容 -->
</androidx.cardview.widget.CardView>
```

---

## ✅ 最佳实践

1. **优先使用主题属性**
   - ❌ 不要: `android:background="#2A2A2A"`
   - ✅ 推荐: `android:background="?attr/backgroundCard"`

2. **统一使用预定义 drawable**
   - ❌ 不要: 创建新的相似 drawable
   - ✅ 推荐: 使用 `bg_card_8dp` 或 `bg_input`

3. **动态着色使用透明背景**
   - ❌ 不要: `bg_card_8dp` + `backgroundTint`
   - ✅ 推荐: `bg_rounded_transparent` + `backgroundTint`

4. **CardView 使用主题属性**
   - ❌ 不要: `app:cardBackgroundColor="#1E1E1E"`
   - ✅ 推荐: `app:cardBackgroundColor="?attr/backgroundCard"`

---

## 🗑️ 已删除的冗余 Drawable

以下 drawable 已被删除并替换：

| 旧文件 | 新文件 | 说明 |
|--------|--------|------|
| `bg_rounded_corner.xml` | `bg_card_8dp.xml` | 统一命名 |
| `bg_rounded_corner_dark.xml` | `bg_card_16dp.xml` | 统一命名 |
| `bg_edittext.xml` | `bg_input.xml` | 合并 |
| `bg_spinner.xml` | `bg_input.xml` | 合并 |
| `bg_popup_menu.xml` | `bg_input.xml` | 合并 |

---

## 🌈 主题效果

### 浅色主题
- 主背景: 蓝紫渐变 `#DBE9FD → #FFFFFF → #DCD9FF`
- 卡片背景: 接近白色 `#FAFAFA`
- 文本: 黑色 `#000000`
- 强调色: 紫色 `#BB86FC`

### 深色主题
- 主背景: 黑灰渐变 `#000000 → #121212 → #1A1A1A`
- 卡片背景: 深灰 `#1D1D1D`
- 文本: 白色 `#FFFFFF`
- 强调色: 紫色 `#BB86FC`

---

**最后更新**: 2025-11-05




