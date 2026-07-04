# itg-ui：TabLayout + ViewPager2 通用框架设计方案

> **目标**：基于 `itg-base` 的组合式 MVVM 架构，在 `itg-ui` 中构建一套 TabLayout + ViewPager2 通用框架，将多 Tab 场景的样板代码归零，业务层仅需声明"有哪些 Tab、每个 Tab 长什么样"即可。

---

## 1. 现状分析

### 1.1 itg-base 已有的能力（可直接复用）

```
AutoBindingBaseActivity<VB, VM>          AutoBindingBaseFragment<VB, VM>
├── binding: VB         ← 自动 inflate     ├── binding: VB        ← 自动 inflate
├── viewModel: VM       ← 自动创建         ├── viewModel: VM      ← 与宿主 Activity 共享
├── systemBars          ← 边到边           ├── messages           ← Toast/Snackbar
├── launcher            ← ActivityResult   ├── permissions        ← 运行时权限
├── permissions         ← 运行时权限        └── uiState           ← Loading/Empty/Error
├── messages            ← Toast/Snackbar
└── uiState             ← Loading/Empty/Error
        └── 自动观察 viewModel.loading → showLoading / showContent
                       viewModel.error   → toast
```

核心设计模式：**Ability 组合** → `LifeAbility` 注入 Activity 生命周期，三级定制（Config / Protected open / 整体替换）。

### 1.2 itg-ui 当前状态

- **完全空的 Android Library 模块**，命名空间 `com.itg.itg_ui`
- 已有依赖：AndroidX appcompat、core-ktx、Material
- **未依赖 `itg-base`** → 需要新增依赖
- **未声明 ViewPager2** → 需要在 `libs.versions.toml` 中新增

### 1.3 项目导航方式

- 使用 **TheRouter**（`cn.therouter:router:1.3.2`）做页面间路由
- **无** Jetpack Navigation Component
- Tab 切换属于**页内导航**，不经过 TheRouter，由 ViewPager2 管理

---

## 2. 架构设计

### 2.1 分层总览

```
┌──────────────────────────────────────────────────────────────────┐
│                        业务层 (app)                               │
│  class HomeTabActivity : TabHostActivity()                       │
│  class HomeFragment : BaseTabFragment<FragmentHomeBinding, ...>() │
│  仅声明 Tab 列表 + 每个 Tab 的 Fragment + ViewModel                │
└───────────────────────────────┬──────────────────────────────────┘
                                │ 继承 / 配置
┌───────────────────────────────▼──────────────────────────────────┐
│                     itg-ui（本方案构建）                           │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │  TabHostActivity / TabHostFragment  ← 宿主容器               │ │
│  │  TabViewPagerAbility                ← TabLayout+VP2 绑定能力  │ │
│  │  GenericTabAdapter                  ← FragmentStateAdapter   │ │
│  │  TabItem / TabConfig                ← 声明式配置模型          │ │
│  │  BaseTabFragment<VB, VM>            ← Tab 内容 Fragment 基类  │ │
│  │  TabBadgeAbility                    ← 角标能力               │ │
│  └─────────────────────────────────────────────────────────────┘ │
└───────────────────────────────┬──────────────────────────────────┘
                                │ 依赖
┌───────────────────────────────▼──────────────────────────────────┐
│                       itg-base（已有）                            │
│  AutoBindingBaseActivity / AutoBindingBaseFragment               │
│  ItgModel / Event / LifeAbility / AutoClearedValue               │
│  UiStateAbility / MessageAbility / PermissionAbility ...         │
└──────────────────────────────────────────────────────────────────┘
```

### 2.2 核心类职责矩阵

| 类 | 继承自 | 职责 | 对业务可见 |
|----|--------|------|-----------|
| `TabItem` | data class | 描述一个 Tab（标题、图标、Fragment 类、参数） | ✅ 业务声明 |
| `TabConfig` | data class | 全局 Tab 行为配置（模式、滑动、预加载等） | ✅ 业务声明 |
| `GenericTabAdapter` | `FragmentStateAdapter` | 根据 `List<TabItem>` 自动创建 Fragment | ❌ 内部使用 |
| `TabViewPagerAbility` | `LifeAbility` | 绑定 TabLayout + ViewPager2 + Adapter，管理联动生命周期 | ❌ 内部使用 |
| `TabHostActivity` | `AutoBindingBaseActivity` | 一站式 Tab 宿主 Activity | ✅ 业务继承 |
| `TabHostFragment` | `AutoBindingBaseFragment` | 嵌套场景的 Tab 宿主 Fragment | ✅ 业务继承 |
| `BaseTabFragment` | `AutoBindingBaseFragment` | Tab 内容页基类，提供可见性回调 + 懒加载 | ✅ 业务继承 |
| `TabBadgeAbility` | `LifeAbility` | Tab 角标（数字/红点）管理 | ⚪ 按需调用 |
| `TabStyle` | data class | 全局视觉样式：指示器、文字、Tab 项、容器 | ✅ 业务声明 |
| `TabIndicatorStyle` | data class | 指示器颜色/高度/形状/动画 | ✅ 业务声明 |
| `TabTextStyle` | data class | 选中/未选中文字颜色、大小、字体 | ✅ 业务声明 |
| `TabItemStyle` | data class | 单个 Tab 项的内边距、背景、图标位置/大小 | ✅ 业务声明 |
| `CustomTabViewProvider` | interface | 完全自定义 Tab View 的结构化接口 | ✅ 业务实现 |

---

## 3. 详细设计

### 3.1 声明式配置模型

```kotlin
// —— itg-ui/src/main/java/com/itg/itg_ui/tab/TabItem.kt

/**
 * 描述一个 Tab 页签。
 *
 * @param F  Fragment 类型，须继承 [BaseTabFragment]（推荐）或至少为 [Fragment] 子类。
 *            声明为 Class<F> 以保证编译期类型安全。
 */
data class TabItem<F : Fragment>(
    /** Tab 标题文本（与 titleRes 二选一，直接传字符串） */
    val title: String? = null,
    /** Tab 标题资源 ID（与 title 二选一） */
    val titleRes: Int? = null,
    /** Tab 图标（可选） */
    val icon: Drawable? = null,
    /** Tab 图标资源 ID（可选） */
    val iconRes: Int? = null,
    /** Fragment 类对象，Adapter 通过反射实例化 */
    val fragmentClass: Class<F>,
    /** 传给 Fragment 的 arguments（可选），在 Fragment 创建时自动 setArguments */
    val arguments: Bundle? = null,
    /** 角标配置（可选） */
    val badge: TabBadge? = null,
    /** 唯一标识，默认用 fragmentClass 的 canonicalName */
    val tag: String = fragmentClass.canonicalName ?: fragmentClass.simpleName
)
```

```kotlin
// —— itg-ui/src/main/java/com/itg/itg_ui/tab/TabConfig.kt

/**
 * 全局 Tab 行为配置。
 *
 * 采用与 itg-base Ability 一致的 Config 模式：
 *  - 每个 field 都有合理默认值
 *  - 业务层按需覆盖，零行代码跑默认行为
 */
data class TabConfig(
    /** Tab 模式：MODE_FIXED / MODE_SCROLLABLE */
    val tabMode: Int = TabLayout.MODE_FIXED,
    /** Tab 对其方式：GRAVITY_FILL / GRAVITY_CENTER */
    val tabGravity: Int = TabLayout.GRAVITY_FILL,
    /** 默认选中位置（0-based） */
    val defaultPosition: Int = 0,
    /** 是否允许滑动切换 */
    val swipeable: Boolean = true,
    /** 离屏保留的页面数（默认 1，即保留左右各 1 页） */
    val offscreenPageLimit: Int = 1,
    /** TabLayout 与 ViewPager2 联动时，是否自动设置 title（默认 true） */
    val autoTitle: Boolean = true,
    /** 全局视觉样式（null = 使用 TabLayout 自身的 XML 属性 / 系统默认） */
    val style: TabStyle? = null,
    /** 用户滑动到某一页时的附加回调（null = 无额外操作） */
    val onPageSelected: ((Int, Fragment) -> Unit)? = null,
    /** 首次选中某 Tab 时是否触发懒加载回调（默认 true） */
    val lazyLoadOnFirstSelect: Boolean = true,
)
```

```kotlin
// —— itg-ui/src/main/java/com/itg/itg_ui/tab/TabBadge.kt

/**
 * Tab 角标描述。
 */
data class TabBadge(
    /** 角标数字（≤0 时配合 showAsDot 展示纯红点） */
    val count: Int = 0,
    /** 纯红点模式（忽略 count，仅展示红色圆点） */
    val showAsDot: Boolean = false,
    /** 角标背景色（null = Material 默认 Badge 颜色） */
    val backgroundColor: Int? = null,
    /** 最大显示数字，超过显示如 "99+"（默认 99） */
    val maxNumber: Int = 99,
)
```

### 3.1-bis Tab 样式体系（TabStyle）

TabLayout 的视觉样式拆分为 **4 个独立子配置**，每个都遵循"零行用默认，一行换单项"的 Config 模式：

```
TabStyle                         ← 聚合入口
├── indicator: TabIndicatorStyle ← 指示器：颜色、高度、形状、圆角、动画
├── textStyle: TabTextStyle      ← 文字：选中/未选中的颜色、大小、字体
├── itemStyle: TabItemStyle      ← Tab 项：内边距、背景、图标位置
└── tabViewTemplate              ← 自定义 Tab View（结构化接口）
```

#### 3.1-bis.1 TabIndicatorStyle — 指示器样式

```kotlin
// —— itg-ui/src/main/java/com/itg/itg_ui/tab/style/TabIndicatorStyle.kt

/**
 * TabLayout 指示器（Indicator）视觉配置。
 *
 * Material 默认行为：底部横线，颜色 = colorPrimary，高度 2dp，自动跟随 Tab 宽度。
 * 以下所有字段为 null 时保持 Material 默认。
 */
data class TabIndicatorStyle(
    // ========== 颜色 ==========

    /** 指示器颜色（null = Material 默认 colorPrimary）。支持 @ColorInt 或 @ColorRes */
    val color: Int? = null,

    // ========== 尺寸 ==========

    /** 指示器高度（dp），null = Material 默认 2dp */
    val heightDp: Int? = null,
    /** 指示器宽度（dp），null = 自动跟随 Tab 文字宽度。仅在 [widthMode] = FIXED 时生效 */
    val widthDp: Int? = null,

    // ========== 形状 ==========

    /** 指示器圆角半径（dp），null = 直角。与 [drawable] 互斥 */
    val cornerRadiusDp: Float? = null,
    /** 完全自定义指示器 Drawable（与 [cornerRadiusDp] 互斥，Drawable 优先级更高） */
    val drawable: Drawable? = null,
    /** 指示器内边距（左右边距），dp。用于缩小指示器相对于 Tab 的宽度 */
    val horizontalPaddingDp: Float? = null,

    // ========== 位置 ==========

    /** 指示器位置：GRAVITY_BOTTOM / GRAVITY_TOP / GRAVITY_CENTER / GRAVITY_STRETCH */
    val gravity: Int = TabLayout.INDICATOR_GRAVITY_BOTTOM,

    // ========== 动画 ==========

    /** 指示器切换动画时长（ms），默认 300ms */
    val animationDurationMs: Int = 300,
    /** 动画插值器（null = LinearOutSlowInInterpolator，Material 标准） */
    val animationInterpolator: Interpolator? = null,
    /** 是否启用指示器切换动画 */
    val animationEnabled: Boolean = true,

    // ========== 高级 ==========

    /** 指示器与文字之间的间距（dp） */
    val distanceFromTextDp: Int? = null,
)
```

#### 3.1-bis.2 TabTextStyle — 文字样式

```kotlin
// —— itg-ui/src/main/java/com/itg/itg_ui/tab/style/TabTextStyle.kt

/**
 * Tab 标签文字视觉配置。
 *
 * 核心：通过 [selectedColor] + [unselectedColor] 自动创建 ColorStateList 设置到 TabLayout。
 * 如需更复杂的 ColorStateList（如 disabled 态），可使用 [textColorStateList] 整体替换。
 */
data class TabTextStyle(
    // ========== 颜色（两个最常用场景） ==========

    /** 选中态文字颜色（@ColorInt），与 [unselectedColor] 配对使用 */
    val selectedColor: Int? = null,
    /** 未选中态文字颜色（@ColorInt），与 [selectedColor] 配对使用 */
    val unselectedColor: Int? = null,
    /**
     * 完整 ColorStateList（优先级高于 selectedColor/unselectedColor）。
     * 用于需要 disabled 态等复杂颜色场景。
     */
    val textColorStateList: ColorStateList? = null,

    // ========== 字号 ==========

    /** 选中态文字大小（sp），null = 不变 */
    val selectedSizeSp: Float? = null,
    /** 未选中态文字大小（sp），null = 不变 */
    val unselectedSizeSp: Float? = null,

    // ========== 字体 ==========

    /** 全局字体（选中/未选中统一），null = 系统默认 */
    val typeface: Typeface? = null,
    /** 选中态专用字体（如加粗），null = 使用 [typeface] */
    val selectedTypeface: Typeface? = null,
    /** 未选中态专用字体，null = 使用 [typeface] */
    val unselectedTypeface: Typeface? = null,

    // ========== 外观 ==========

    /** 字母间距（em），null = Material 默认 */
    val letterSpacing: Float? = null,
    /** 全部大写（Material 默认 true） */
    val allCaps: Boolean = true,
    /** 最大行数 */
    val maxLines: Int = 1,
    /** 文字省略方式 */
    val ellipsize: TextUtils.TruncateAt? = null,
)
```

#### 3.1-bis.3 TabItemStyle — 单个 Tab 项样式

```kotlin
// —— itg-ui/src/main/java/com/itg/itg_ui/tab/style/TabItemStyle.kt

/**
 * 单个 Tab 项的视觉配置。
 *
 * 注意：这是对 TabLayout 中每一个 Tab 的样式控制，不是对 Tab 内容 Fragment 的控制。
 */
data class TabItemStyle(
    // ========== 尺寸 ==========

    /** Tab 最小宽度（dp），null = Material 默认（24dp + 文字） */
    val minWidthDp: Int? = null,
    /** Tab 内边距（dp） */
    val padding: TabPadding? = null,

    // ========== 背景 ==========

    /** 选中态背景色（@ColorInt）或背景资源（@DrawableRes）。null = 透明 */
    val selectedBackground: Int? = null,
    /** 未选中态背景色或背景资源。null = 透明 */
    val unselectedBackground: Int? = null,
    /** 水波纹/触摸反馈颜色。null = Material 默认 */
    val rippleColor: Int? = null,

    // ========== 图标 ==========

    /**
     * 图标相对于文字的位置：
     *  - TabLayout.ICON_GRAVITY_TOP（默认，图标在文字上方）
     *  - TabLayout.ICON_GRAVITY_START（图标在文字左侧）
     *  - TabLayout.ICON_GRAVITY_END（图标在文字右侧）
     *  - TabLayout.ICON_GRAVITY_BOTTOM（图标在文字下方）
     */
    val iconGravity: Int = TabLayout.ICON_GRAVITY_TOP,
    /** 图标大小（dp），null = 24dp（Material 默认） */
    val iconSizeDp: Int? = null,
    /** 图标着色（支持选中/未选中双色），null = 不自动着色 */
    val iconTint: ColorStateList? = null,
    /** 图标与文字的间距（dp），null = Material 默认 */
    val iconTextGapDp: Int? = null,

    // ========== 布局 ==========

    /** Tab 内容对齐方式：GRAVITY_CENTER / GRAVITY_FILL */
    val contentGravity: Int = androidx.appcompat.R.attr.actionBarItemBackground, // placeholder
)
```

#### 3.1-bis.4 TabPadding — 通用内边距

```kotlin
// —— itg-ui/src/main/java/com/itg/itg_ui/tab/style/TabPadding.kt

/**
 * 四向内边距（dp），替代直接使用 Pixel 值的混乱。
 *
 * 所有字段默认 0dp —— 零行声明 = 不改变现有 padding。
 */
data class TabPadding(
    val leftDp: Float = 0f,
    val topDp: Float = 0f,
    val rightDp: Float = 0f,
    val bottomDp: Float = 0f,
) {
    companion object {
        /** 均匀内边距 */
        fun all(dp: Float) = TabPadding(dp, dp, dp, dp)
        /** 水平内边距（左右相等） */
        fun horizontal(dp: Float) = TabPadding(dp, 0f, dp, 0f)
        /** 垂直内边距（上下相等） */
        fun vertical(dp: Float) = TabPadding(0f, dp, 0f, dp)
    }
}
```

#### 3.1-bis.5 TabStyle — 聚合入口

```kotlin
// —— itg-ui/src/main/java/com/itg/itg_ui/tab/style/TabStyle.kt

/**
 * TabLayout 全局视觉样式聚合。
 *
 * 使用方式（在 [TabConfig] 中）：
 * ```
 * TabConfig(
 *     style = TabStyle(
 *         indicator = TabIndicatorStyle(color = Color.RED, heightDp = 4),
 *         textStyle = TabTextStyle(
 *             selectedColor = Color.BLACK,
 *             unselectedColor = Color.GRAY,
 *             selectedSizeSp = 16f,
 *             selectedTypeface = Typeface.DEFAULT_BOLD,
 *         ),
 *     )
 * )
 * ```
 *
 * 四层样式各自独立，未指定的部分保持 Material 默认或使用 XML 属性值。
 */
data class TabStyle(
    /** 指示器样式（null = Material 默认） */
    val indicator: TabIndicatorStyle? = null,

    /** 文字样式（null = Material 默认） */
    val textStyle: TabTextStyle? = null,

    /** Tab 项样式（null = Material 默认） */
    val itemStyle: TabItemStyle? = null,

    // ========== TabLayout 容器级 ==========

    /** TabLayout 整体背景（@ColorInt 或 @DrawableRes）。null = 默认透明 */
    val tabBackground: Int? = null,
    /** TabLayout 高度（dp）。null = wrap_content */
    val tabHeightDp: Int? = null,
    /** TabLayout 阴影（dp）。null = Material 默认（无阴影） */
    val tabElevationDp: Float? = null,
    /** Tab 之间的分割线 Drawable。null = 无分割线 */
    val tabDividerDrawable: Drawable? = null,
    /** Tab 分割线内边距（上下缩进，dp） */
    val tabDividerPaddingDp: Int? = null,

    // ========== 自定义 Tab View（完全控制） ==========

    /**
     * 完全自定义 Tab View 的入口。
     *
     * null = 使用系统默认 TabView（TextView + 可选 icon）
     * 非 null = 每个 Tab 调用此 Provider 创建自定义 View。
     *
     * 更简单的场景推荐先尝试 [TabTextStyle] + [TabItemStyle] + [TabItem.icon]，
     * 只有在需要特殊布局（如：图标覆盖在文字上方、自定义进度条、特殊角标位置等）时才使用此项。
     */
    val customTabViewProvider: CustomTabViewProvider? = null,
)
```

#### 3.1-bis.6 CustomTabViewProvider — 结构化自定义 Tab View

```kotlin
// —— itg-ui/src/main/java/com/itg/itg_ui/tab/style/CustomTabViewProvider.kt

/**
 * 完全自定义 Tab View 的结构化接口。
 *
 * 对比旧方案（Lambda `(Inflater, TabLayout, Int, TabItem) -> View`）的优势：
 *  1. 支持选中/未选中的状态更新（不用每次重建 View）
 *  2. 支持 View 回收复用（与 RecyclerView 思路一致）
 *  3. 语义更清晰
 *
 * 使用示例——带未读数的自定义 Tab：
 * ```
 * val provider = object : CustomTabViewProvider {
 *     override fun createView(
 *         inflater: LayoutInflater, parent: TabLayout,
 *         position: Int, item: TabItem<*>
 *     ): View {
 *         return inflater.inflate(R.layout.custom_tab_badge, parent, false)
 *     }
 *
 *     override fun bindView(view: View, position: Int, item: TabItem<*>) {
 *         view.findViewById<TextView>(R.id.tabTitle).text = item.title
 *     }
 *
 *     override fun onSelectedChanged(view: View, selected: Boolean) {
 *         view.findViewById<TextView>(R.id.tabTitle).apply {
 *             setTextColor(if (selected) Color.RED else Color.GRAY)
 *         }
 *     }
 * }
 * ```
 */
interface CustomTabViewProvider {
    /**
     * 创建 Tab View（仅调用一次，在 Tab 首次创建时）。
     *
     * @param inflater  LayoutInflater（已设置 parent context theme）
     * @param parent    TabLayout 容器
     * @param position  Tab 在列表中的位置
     * @param item      当前 Tab 的声明信息（title、icon、badge 等）
     */
    fun createView(
        inflater: LayoutInflater,
        parent: TabLayout,
        position: Int,
        item: TabItem<*>,
    ): View

    /**
     * 绑定数据到 View（在 [createView] 之后立即调用，也可用于后续刷新）。
     */
    fun bindView(view: View, position: Int, item: TabItem<*>) {}

    /**
     * 选中状态变化回调（每次 Tab 切换时触发）。
     *
     * @param view     之前 [createView] 返回的 View 实例
     * @param selected true = 该 Tab 被选中；false = 该 Tab 被取消选中
     */
    fun onSelectedChanged(view: View, selected: Boolean) {}

    /**
     * 角标更新回调（当业务层调用 [TabViewPagerAbility.updateBadge] 时触发）。
     * 默认不实现——如果自定义 View 中需要显示角标，覆写此方法即可。
     */
    fun onBadgeChanged(view: View, badge: TabBadge?) {}
}
```

#### 3.1-bis.7 样式应用流程

```
TabViewPagerAbility.bind()
│
├─ 1. 读取 TabConfig.style
│
├─ 2. 应用容器级样式（一次性设置）
│   ├─ tabBackground  → TabLayout.setBackground()
│   ├─ tabElevationDp → TabLayout.setElevation()
│   ├─ tabHeightDp    → TabLayout.layoutParams.height
│   └─ tabDivider*    → TabLayout.setDividerDrawable() + setDividerPadding()
│
├─ 3. 应用 TabItemStyle（通过 TabLayoutMediator 的 onConfigureTab 回调）
│   ├─ minWidth   → tab.view.minimumWidth
│   ├─ padding    → tab.view.setPadding()
│   ├─ background → tab.view.setBackground()（配合 ColorStateList）
│   ├─ iconGravity / iconSize → 通过 TabLayout.setTabIconSize() / tab.setIcon()
│   └─ rippleColor → TabLayout.setTabRippleColor()
│
├─ 4. 应用 TabTextStyle（直接设置到 TabLayout）
│   ├─ textColor  → TabLayout.setTabTextColors() 或 setTabTextColors(ColorStateList)
│   ├─ typeface   → 通过 TabLayoutMediator 设置每个 tab 的 TextView typeface
│   ├─ allCaps    → 通过 TabLayoutMediator 设置每个 tab 的 TextView isAllCaps
│   └─ 字大小    → 通过 TabLayoutMediator 设置每个 tab 的 TextView textSize
│       └─ 选中态字大小变化需要在 onTabSelected 回调中动态更新
│
├─ 5. 应用 TabIndicatorStyle
│   ├─ 简单场景（color + height + cornerRadius）
│   │   → 构造 MaterialShapeDrawable → TabLayout.setSelectedTabIndicator()
│   ├─ 自定义 drawable
│   │   → TabLayout.setSelectedTabIndicator(drawable)
│   ├─ gravity      → TabLayout.setSelectedTabIndicatorGravity()
│   ├─ animation    → TabLayout.setTabIndicatorAnimationMode() + duration
│   └─ padding      → TabLayout.setSelectedTabIndicatorPadding()
│
└─ 6. 注册 CustomTabViewProvider（替换默认 tab.contentView）
    ├─ tab.setCustomView(provider.createView(...))
    └─ 在 onPageSelected 回调中遍历所有 tab，调 provider.onSelectedChanged(view, selected)
```

#### 3.1-bis.8 样式定制场景速查

| 场景 | 配置层级 | 代码量 |
|------|---------|--------|
| 改指示器颜色 | `TabIndicatorStyle(color = 0xFF6200EE.toInt())` | 1 行 |
| 指示器圆角 + 加高 | `TabIndicatorStyle(heightDp = 4, cornerRadiusDp = 2f)` | 1 行 |
| 选中文字变色 + 加粗 | `TabTextStyle(selectedColor = COLOR_PRIMARY, selectedTypeface = Typeface.DEFAULT_BOLD)` | 1 行 |
| 底部导航样式（图标在上+小字） | `TabItemStyle(iconGravity = ICON_GRAVITY_TOP) + TabTextStyle(selectedSizeSp = 12f)` | 2 行 |
| 带圆角背景的选中态 | `TabItemStyle(selectedBg = R.drawable.bg_tab_selected)` | 1 行 |
| Tab 等宽分布 | `TabItemStyle(minWidthDp = 0) + TabConfig(tabMode = MODE_FIXED, tabGravity = GRAVITY_FILL)` | 在 TabConfig 中搞定 |
| 完全自定义 Tab View | `CustomTabViewProvider` 实现 ~15 行 | 仅复杂场景需要 |
| 不使用 itg-ui 的样式，全部 XML 控制 | `TabConfig(style = null)`，在布局 XML 中写 app 属性 | 0 行 Kotlin |

### 3.2 GenericTabAdapter — 通用 Adapter

```kotlin
// —— itg-ui/src/main/java/com/itg/itg_ui/tab/GenericTabAdapter.kt

/**
 * 通用 FragmentStateAdapter。
 *
 * 与 [TabLayout] 配合时，通过 [TabLayoutMediator] 自动同步标题。
 * 每条 [TabItem] 通过反射创建 Fragment 实例。
 *
 * 内存安全：
 *  - 继承 FragmentStateAdapter，不可见页面会被正确保存/恢复状态
 *  - Fragment 实例由 Adapter 内部管理，业务层不直接持有引用
 */
class GenericTabAdapter(
    private val hostFragment: Fragment,          // 宿主 Fragment（或 Activity 的 supportFragmentManager）
    private val hostActivity: FragmentActivity,   // 当宿主是 Activity 时使用
    private val tabs: List<TabItem<*>>,
) : FragmentStateAdapter(
    // 智能选择 fragmentManager：优先宿主 Fragment，否则用 Activity
    when {
        hostFragment.isAdded -> hostFragment
        else -> hostActivity
    }.let { /* 根据实例类型选择合适的 FragmentManager */ }
) {

    /** 已创建的 Fragment 缓存（position → Fragment），用于懒加载回调等场景 */
    private val fragmentCache = SparseArray<Fragment>(tabs.size)

    override fun getItemCount(): Int = tabs.size

    override fun createFragment(position: Int): Fragment {
        val item = tabs[position]
        return item.fragmentClass
            .getDeclaredConstructor()
            .newInstance()
            .also { fragment ->
                item.arguments?.let { fragment.arguments = it }
                fragmentCache.put(position, fragment)
            }
    }

    /**
     * 获取已创建的 Fragment（可能为 null，取决于该页面是否已被实例化）。
     * ViewPager2 的 offscreenPageLimit 决定哪些 position 已创建。
     */
    fun getFragmentAt(position: Int): Fragment? = fragmentCache.get(position)

    /**
     * 提供 TabLayout 标题（当 TabConfig.autoTitle = true 时由 Mediator 调用）。
     */
    fun getPageTitle(position: Int): CharSequence? {
        val item = tabs[position]
        return item.title
            ?: item.titleRes?.let { hostActivity.getString(it) }
    }
}
```

**关键设计决策**：

| 决策 | 选择 | 理由 |
|------|------|------|
| Fragment 实例化方式 | `Class.newInstance()` 反射 | 与 itg-base 的 ViewBinding 反射一致；Fragment 必须有无参构造器 |
| Fragment 缓存 | `SparseArray`（内部） | 支持懒加载回调（`onTabFirstVisible`），不暴露给业务层 |
| FragmentManager 来源 | 智能选择（Fragment 的 childFragmentManager vs Activity 的 supportFragmentManager） | 同时支持 Activity 宿主和 Fragment 宿主 |

### 3.3 TabViewPagerAbility — 核心绑定能力

```kotlin
// —— itg-ui/src/main/java/com/itg/itg_ui/tab/TabViewPagerAbility.kt

/**
 * TabLayout + ViewPager2 绑定能力，继承 [LifeAbility] 获得生命周期感知。
 *
 * 职责：
 *  1. 创建 [GenericTabAdapter]
 *  2. 将 Adapter 设置到 ViewPager2
 *  3. 通过 [TabLayoutMediator] 联动 TabLayout 和 ViewPager2
 *  4. 管理 Tab 选中/取消选中事件，分发给 [BaseTabFragment]
 *  5. 管理角标（BadgeDrawable）
 *  6. 在 onDestroy 时清理资源
 *
 * 使用方式（在 TabHostActivity 中）：
 * ```
 * override fun onCreate(savedInstanceState: Bundle?) {
 *     super.onCreate(savedInstanceState)
 *     tabViewPager.bind(
 *         tabLayout = binding.tabLayout,
 *         viewPager = binding.viewPager,
 *         tabs = listOf(
 *             TabItem(title = "首页", fragmentClass = HomeFragment::class.java),
 *             TabItem(title = "我的", fragmentClass = ProfileFragment::class.java),
 *         ),
 *         config = TabConfig()  // 全部默认
 *     )
 * }
 * ```
 */
class TabViewPagerAbility : LifeAbility() {

    // ==================== 内部状态 ====================

    private var adapter: GenericTabAdapter? = null
    private var tabMediator: TabLayoutMediator? = null
    private var config: TabConfig = TabConfig()
    private var tabs: List<TabItem<*>> = emptyList()

    // ==================== 绑定入口 ====================

    /**
     * 绑定 TabLayout + ViewPager2。
     *
     * @param tabLayout   Material TabLayout
     * @param viewPager   ViewPager2
     * @param tabs        Tab 声明列表
     * @param hostFragment 当宿主是 Fragment 时必须传入
     * @param config      可选配置（默认 [TabConfig()]）
     */
    fun bind(
        tabLayout: TabLayout,
        viewPager: ViewPager2,
        tabs: List<TabItem<*>>,
        hostFragment: Fragment? = null,
        config: TabConfig = TabConfig(),
    ) {
        this.tabs = tabs
        this.config = config

        // 1. 应用样式（在任何 Tab 创建之前）
        config.style?.let { applyStyle(tabLayout, it) }

        // 2. 创建 Adapter
        adapter = GenericTabAdapter(
            hostFragment = hostFragment ?: Fragment(),
            hostActivity = ownerActivity,
            tabs = tabs,
        )
        viewPager.adapter = adapter
        viewPager.isUserInputEnabled = config.swipeable
        viewPager.offscreenPageLimit = config.offscreenPageLimit

        // 3. 设置默认页
        if (config.defaultPosition in tabs.indices) {
            viewPager.setCurrentItem(config.defaultPosition, false)
        }

        // 4. TabLayoutMediator 联动（注入 itemStyle + textStyle）
        tabMediator = TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            if (config.autoTitle) {
                tab.text = adapter?.getPageTitle(position)
            }
            // 应用 TabItemStyle 到每个 tab.view
            config.style?.itemStyle?.let { applyItemStyle(tab, it, tabs[position]) }
            // 自定义 Tab View（优先于 itemStyle）
            config.style?.customTabViewProvider?.let { provider ->
                val customView = provider.createView(
                    LayoutInflater.from(tabLayout.context), tabLayout, position, tabs[position]
                )
                provider.bindView(customView, position, tabs[position])
                tab.customView = customView
            }
            // 初始角标
            tabs[position].badge?.let { applyBadge(tab, it) }
        }
        tabMediator?.attach()

        // 5. 应用 TabTextStyle（需要在 Mediator.attach() 后设置，因为 attach 会创建 TextView）
        config.style?.textStyle?.let { applyTextStyle(tabLayout, it) }

        // 6. 注册页面切换回调（处理 textStyle 的选中态字大小切换、customView 状态更新）
        viewPager.registerOnPageChangeCallback(pageChangeCallback)
    }

    // ==================== 公开操作 ====================

    /** 切换到指定 Tab */
    fun selectTab(position: Int, smoothScroll: Boolean = true) {
        viewPager?.setCurrentItem(position, smoothScroll)
    }

    /** 获取当前选中的 position */
    fun currentPosition(): Int = viewPager?.currentItem ?: 0

    /** 动态更新角标 */
    fun updateBadge(position: Int, badge: TabBadge?) {
        val tab = tabLayout?.getTabAt(position) ?: return
        if (badge != null) {
            applyBadge(tab, badge)
        } else {
            tab.removeBadge()
        }
    }

    /** 获取指定 position 的 Fragment 实例（可能为 null） */
    fun getFragmentAt(position: Int): Fragment? = adapter?.getFragmentAt(position)

    /** 更新整个 Tab 列表（用于动态增减 Tab，注意开销较大，会重建 Adapter） */
    fun updateTabs(newTabs: List<TabItem<*>>) {
        // 清理旧状态
        tabMediator?.detach()
        tabMediator = null
        // 重新绑定（需外部传入引用，所以这里仅做标记，让外部重新调 bind）
        // 实际实现中建议提供 rebuild() 方法
    }

    // ==================== 页面切换回调 ====================

    private val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        private var lastPosition = -1

        override fun onPageSelected(position: Int) {
            super.onPageSelected(position)

            // 通知旧页面：Tab 取消选中
            if (lastPosition >= 0 && lastPosition < tabs.size) {
                val prevFragment = adapter?.getFragmentAt(lastPosition)
                (prevFragment as? BaseTabFragment<*, *>)?.onTabUnselected()
                // 自定义 Tab View 选中状态更新
                notifyCustomViewSelection(lastPosition, selected = false)
            }

            // 通知新页面：Tab 选中
            val curFragment = adapter?.getFragmentAt(position)
            (curFragment as? BaseTabFragment<*, *>)?.onTabSelected(firstTime = false)
            // 自定义 Tab View 选中状态更新
            notifyCustomViewSelection(position, selected = true)
            // 动态更新 textStyle 的选中态字号（selectedSize != unselectedSize 时）
            updateTextSizeOnSelection(position, lastPosition)

            // 业务层自定义回调
            curFragment?.let { config.onPageSelected?.invoke(position, it) }
            lastPosition = position
        }
    }

    // ==================== 样式应用 ====================

    /**
     * 应用 [TabStyle] 到 TabLayout。
     * 调用时机：bind() 中，在 Adapter 创建之前（容器级）和 Mediator 配置时（Tab 级）。
     */
    private fun applyStyle(tabLayout: TabLayout, style: TabStyle) {
        val ctx = tabLayout.context

        // —— 容器级 ——
        style.tabBackground?.let { tabLayout.setBackgroundResource(it) }
        style.tabElevationDp?.let { tabLayout.elevation = dp2px(it) }
        style.tabDividerDrawable?.let { tabLayout.setDividerDrawable(it) }
        style.tabDividerPaddingDp?.let { tabLayout.dividerPadding = dp2px(it) }
        style.tabHeightDp?.let {
            tabLayout.layoutParams = tabLayout.layoutParams.apply { height = dp2px(it) }
        }

        // —— 指示器 ——
        style.indicator?.let { applyIndicatorStyle(tabLayout, it) }
    }

    private fun applyIndicatorStyle(tabLayout: TabLayout, indicator: TabIndicatorStyle) {
        // 简单场景：使用 TabLayout 内置方法
        if (indicator.drawable != null) {
            tabLayout.setSelectedTabIndicator(indicator.drawable)
        } else {
            // 构造 MaterialShapeDrawable（支持圆角）
            val drawable = MaterialShapeDrawable(
                ShapeAppearanceModel.builder()
                    .setAllCornerSizes(indicator.cornerRadiusDp?.let { dp2px(it).toFloat() }
                        ?: 0f)
                    .build()
            ).apply {
                fillColor = ColorStateList.valueOf(indicator.color ?: tabLayout.context.getColor(
                    com.google.android.material.R.attr.colorPrimary))
                setTint(indicator.color ?: tabLayout.context.getColor(
                    com.google.android.material.R.attr.colorPrimary))
            }
            tabLayout.setSelectedTabIndicator(drawable)
            indicator.heightDp?.let { tabLayout.setSelectedTabIndicatorHeight(dp2px(it)) }
        }
        tabLayout.setSelectedTabIndicatorGravity(indicator.gravity)
        if (indicator.animationEnabled) {
            // TabLayout 内置动画模式
            tabLayout.tabIndicatorAnimationMode = TabLayout.INDICATOR_ANIMATION_MODE_LINEAR
        }
    }

    private fun applyTextStyle(tabLayout: TabLayout, textStyle: TabTextStyle) {
        // 颜色：优先完整 ColorStateList，否则用 selected/unselected 构建
        when {
            textStyle.textColorStateList != null ->
                tabLayout.setTabTextColors(textStyle.textColorStateList)
            textStyle.selectedColor != null || textStyle.unselectedColor != null ->
                tabLayout.setTabTextColors(
                    textStyle.unselectedColor ?: tabLayout.tabTextColors?.defaultColor ?: Color.GRAY,
                    textStyle.selectedColor ?: tabLayout.tabTextColors?.defaultColor ?: Color.BLACK,
                )
        }
        // 字体、大小、外观 —— 在 Mediator 中逐个 Tab 设置，因为需要拿到每个 Tab 的 TextView
    }

    private fun applyItemStyle(tab: TabLayout.Tab, itemStyle: TabItemStyle, item: TabItem<*>) {
        val view = tab.view
        itemStyle.minWidthDp?.let { view.minimumWidth = dp2px(it) }
        itemStyle.padding?.let {
            view.setPadding(dp2px(it.leftDp), dp2px(it.topDp), dp2px(it.rightDp), dp2px(it.bottomDp))
        }
        // 注意：rippleColor、选中背景等通过 RippleDrawable + ColorStateList 在 onSelected 时动态设置
        itemStyle.iconGravity.let { /* TabLayout 不支持运行时切换 iconGravity，需在创建 Tab 时通过 Tab.setIcon() 控制 */ }
    }

    private fun notifyCustomViewSelection(position: Int, selected: Boolean) {
        val provider = config.style?.customTabViewProvider ?: return
        tabLayout?.getTabAt(position)?.customView?.let { view ->
            provider.onSelectedChanged(view, selected)
        }
    }

    private fun updateTextSizeOnSelection(selectedPos: Int, unselectedPos: Int) {
        val textStyle = config.style?.textStyle ?: return
        if (textStyle.selectedSizeSp == null && textStyle.unselectedSizeSp == null) return

        // 更新新选中的 Tab 文字大小
        tabLayout?.getTabAt(selectedPos)?.view?.let { tabView ->
            (tabView as? TextView)?.textSize = textStyle.selectedSizeSp ?: textStyle.unselectedSizeSp ?: 14f
        }
        // 恢复取消选中的 Tab 文字大小
        if (unselectedPos >= 0) {
            tabLayout?.getTabAt(unselectedPos)?.view?.let { tabView ->
                (tabView as? TextView)?.textSize = textStyle.unselectedSizeSp ?: textStyle.selectedSizeSp ?: 14f
            }
        }
    }

    private fun dp2px(dp: Int): Int = (dp * ownerActivity.resources.displayMetrics.density).toInt()
    private fun dp2px(dp: Float): Float = dp * ownerActivity.resources.displayMetrics.density

    // ==================== 角标 ====================

    private fun applyBadge(tab: TabLayout.Tab, badge: TabBadge) {
        val badgeDrawable = tab.orCreateBadge
        if (badge.showAsDot || badge.count <= 0) {
            badgeDrawable.isVisible = true
            badgeDrawable.clearNumber()
        } else {
            badgeDrawable.number = badge.count.coerceAtMost(badge.maxNumber)
            badgeDrawable.maxCharacterCount = badge.maxNumber.toString().length + 1 // "99+"
            badgeDrawable.isVisible = true
        }
        badge.backgroundColor?.let { badgeDrawable.backgroundColor = it }
    }

    // ==================== 生命周期 ====================

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        tabMediator?.detach()
        tabMediator = null
        adapter = null
    }
}
```

### 3.4 BaseTabFragment — Tab 内容页基类

```kotlin
// —— itg-ui/src/main/java/com/itg/itg_ui/tab/BaseTabFragment.kt

/**
 * Tab 内容 Fragment 基类。
 *
 * 继承 [AutoBindingBaseFragment]，在现有 MVVM 能力之上增加：
 *  - [onTabSelected] / [onTabUnselected]  可见性感知
 *  - [onTabFirstVisible]                  懒加载（首次可见时触发，只触发一次）
 *  - [isTabVisible]                       当前是否可见
 *
 * 典型用法：
 * ```
 * class HomeFragment : BaseTabFragment<FragmentHomeBinding, HomeModel>() {
 *     override fun onTabFirstVisible() {
 *         viewModel.loadData()  // 仅首次可见时加载数据
 *     }
 * }
 * ```
 */
abstract class BaseTabFragment<VB : ViewBinding, VM : ItgModel>
    : AutoBindingBaseFragment<VB, VM>() {

    /** 当前 Tab 是否处于可见状态 */
    var isTabVisible: Boolean = false
        private set

    /** 是否已经完成首次可见加载 */
    private var firstVisibleHandled = false

    // ==================== 提供给 TabViewPagerAbility 调用的回调 ====================

    /**
     * Tab 被选中时调用（由 [TabViewPagerAbility] 分发）。
     *
     * @param firstTime 是否是该 Fragment 实例第一次被选中
     */
    internal fun onTabSelected(firstTime: Boolean) {
        isTabVisible = true
        onTabSelected()

        if (firstTime || !firstVisibleHandled) {
            if (!firstVisibleHandled) {
                firstVisibleHandled = true
                onTabFirstVisible()
            }
        }
    }

    /**
     * Tab 取消选中时调用（由 [TabViewPagerAbility] 分发）。
     */
    internal fun onTabUnselected() {
        isTabVisible = false
        onTabUnselected()
    }

    // ==================== 子类可覆写的钩子 ====================

    /**
     * Tab 变为可见时回调。
     * 子类可覆写以实现可见性感知（如暂停/恢复动画、埋点曝光）。
     */
    protected open fun onTabSelected() {}

    /**
     * Tab 变为不可见时回调。
     */
    protected open fun onTabUnselected() {}

    /**
     * Tab 首次变为可见时回调——**只触发一次**。
     * 用于懒加载数据，避免一次性加载所有 Tab 数据。
     *
     * **默认行为**：调用 [ItgModel] 的 loadData()（如果 ViewModel 有该方法）。
     * 子类可覆写实现自己的懒加载逻辑。
     */
    protected open fun onTabFirstVisible() {
        // 反射尝试调用 viewModel 的 loadData()（如果存在）
        try {
            viewModel::class.java.getMethod("loadData").invoke(viewModel)
        } catch (_: NoSuchMethodException) {
            // ViewModel 没有 loadData 方法，什么都不做
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        firstVisibleHandled = false  // Fragment 视图可能重建，允许再次触发
    }
}
```

### 3.5 TabHostActivity — 一站式宿主容器

```kotlin
// —— itg-ui/src/main/java/com/itg/itg_ui/tab/TabHostActivity.kt

/**
 * Tab 宿主 Activity。
 *
 * 业务层继承此类，在 [onCreateTabs] 中声明 Tab 列表即可，无需手动：
 *  - 找 View
 *  - 创建 Adapter
 *  - 创建 Mediator
 *  - 管理生命周期
 *
 * 布局要求：
 *  - 必须有 id 为 `tabLayout` 的 [TabLayout]
 *  - 必须有 id 为 `viewPager` 的 [ViewPager2]
 *  - 其余内容（Toolbar、FAB 等）自由布局
 *
 * 示例：
 * ```
 * @Route(path = RoutePath.MAIN_HOME_ACTIVITY)
 * class MainActivity : TabHostActivity<ActivityMainBinding, MainModel>() {
 *
 *     override fun onCreateTabs(): List<TabItem<*>> = listOf(
 *         TabItem(title = "首页",  fragmentClass = HomeFragment::class.java),
 *         TabItem(title = "发现",  fragmentClass = DiscoverFragment::class.java),
 *         TabItem(title = "消息",  fragmentClass = MessageFragment::class.java,
 *                 badge = TabBadge(count = 99)),
 *         TabItem(title = "我的",  fragmentClass = ProfileFragment::class.java),
 *     )
 *
 *     override fun onCreateTabConfig() = TabConfig(
 *         tabMode = TabLayout.MODE_FIXED,
 *         offscreenPageLimit = 3,
 *     )
 *
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *         // 在这里做 Tab 无关的初始化（Toolbar、FAB 等）
 *     }
 * }
 * ```
 */
abstract class TabHostActivity<VB : ViewBinding, VM : ItgModel>
    : AutoBindingBaseActivity<VB, VM>() {

    /** Tab 绑定能力 */
    protected val tabViewPager: TabViewPagerAbility by lazy { TabViewPagerAbility() }

    // ==================== 子类必须实现的抽象方法 ====================

    /** 声明 Tab 列表 */
    protected abstract fun onCreateTabs(): List<TabItem<*>>

    // ==================== 子类可选覆写 ====================

    /** 自定义 Tab 全局配置。默认全部使用默认值。 */
    protected open fun onCreateTabConfig(): TabConfig = TabConfig()

    /** 布局中的 TabLayout id（默认 R.id.tabLayout）。子类可用自定义 id。 */
    protected open val tabLayoutId: Int = R.id.tabLayout

    /** 布局中的 ViewPager2 id（默认 R.id.viewPager）。子类可用自定义 id。 */
    protected open val viewPagerId: Int = R.id.viewPager

    // ==================== 生命周期 ====================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tabViewPager.inject(this)

        val tabs = onCreateTabs()
        require(tabs.isNotEmpty()) { "onCreateTabs() 必须返回至少一个 Tab。" }

        binding.root.post {
            // post 到下一帧：确保 DataBinding 布局已完成
            tabViewPager.bind(
                tabLayout = findViewById(tabLayoutId),
                viewPager = findViewById(viewPagerId),
                tabs = tabs,
                config = onCreateTabConfig(),
            )
        }
    }
}
```

### 3.6 TabHostFragment — 嵌套场景宿主

```kotlin
// —— itg-ui/src/main/java/com/itg/itg_ui/tab/TabHostFragment.kt

/**
 * Tab 宿主 Fragment。
 *
 * 与 [TabHostActivity] 能力对称，用于"页面内嵌 Tab"场景。
 * 例如：一个 Activity 中有 BottomNavigation，某个 Tab 页面内部又嵌了二级 Tab。
 *
 * 布局要求：与 [TabHostActivity] 相同（tabLayout + viewPager）。
 *
 * 注意：子 Tab 的 ViewModel 与宿主 Activity 共享（Fragment 默认行为）。
 */
abstract class TabHostFragment<VB : ViewBinding, VM : ItgModel>
    : AutoBindingBaseFragment<VB, VM>() {

    protected val tabViewPager: TabViewPagerAbility by lazy { TabViewPagerAbility() }

    protected abstract fun onCreateTabs(): List<TabItem<*>>
    protected open fun onCreateTabConfig(): TabConfig = TabConfig()
    protected open val tabLayoutId: Int = R.id.tabLayout
    protected open val viewPagerId: Int = R.id.viewPager

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tabViewPager.inject(requireActivity() as AppCompatActivity)

        val tabs = onCreateTabs()
        require(tabs.isNotEmpty()) { "onCreateTabs() 必须返回至少一个 Tab。" }

        view.post {
            tabViewPager.bind(
                tabLayout = view.findViewById(tabLayoutId),
                viewPager = view.findViewById(viewPagerId),
                tabs = tabs,
                hostFragment = this,
                config = onCreateTabConfig(),
            )
        }
    }
}
```

### 3.7 默认布局资源

为减少业务层重复写布局，在 `itg-ui` 中提供了一个默认布局：

```xml
<!-- itg-ui/src/main/res/layout/itg_tab_host_default.xml -->

<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">

    <com.google.android.material.tabs.TabLayout
        android:id="@+id/tabLayout"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        app:tabMode="fixed"
        app:tabGravity="fill" />

    <androidx.viewpager2.widget.ViewPager2
        android:id="@+id/viewPager"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1" />

</LinearLayout>
```

---

## 4. 三级定制模式（与 itg-base 一致）

遵循 itg-base 已建立的 `Config → Protected open → 整体替换` 模式：

| 级别 | 方式 | 适用场景 | 示例 |
|------|------|---------|------|
| **L1 Config** | 覆写 `onCreateTabConfig()` 返回自定义 `TabConfig` | 改 Tab 模式、关闭滑动、改预加载数 | `TabConfig(swipeable = false)` |
| **L2 Protected open** | 覆写 `BaseTabFragment` 的钩子方法 | Tab 可见/不可见时的自定义逻辑 | `onTabSelected() { resumeAnimation() }` |
| **L3 整体替换** | 继承 `TabViewPagerAbility` 写全新的子类 | 需要完全不同的 Tab 行为 | `class MyTabAbility : TabViewPagerAbility()` |

### 典型定制场景

```kotlin
// ═══════════════════════════════════════════════════════════
// 场景一：底部导航样式（图标 + 小字 + 不可滑动 + 指示器圆角）
// ═══════════════════════════════════════════════════════════
class MainActivity : TabHostActivity<ActivityMainBinding, MainModel>() {
    override fun onCreateTabConfig() = TabConfig(
        tabMode = TabLayout.MODE_FIXED,
        tabGravity = TabLayout.GRAVITY_FILL,
        swipeable = false,
        offscreenPageLimit = 3,
        style = TabStyle(
            indicator = TabIndicatorStyle(
                color = Color.TRANSPARENT,  // 底部导航不需要指示器
                heightDp = 0,
            ),
            textStyle = TabTextStyle(
                selectedColor = 0xFF1976D2.toInt(),
                unselectedColor = 0xFF757575.toInt(),
                selectedSizeSp = 11f,
                unselectedSizeSp = 11f,
                allCaps = false,
            ),
            itemStyle = TabItemStyle(
                padding = TabPadding.vertical(8f),
                iconGravity = TabLayout.ICON_GRAVITY_TOP,
                iconSizeDp = 24,
            ),
        ),
    )
    override fun onCreateTabs() = listOf(
        TabItem(title = "首页", iconRes = R.drawable.ic_home, fragmentClass = HomeFragment::class.java),
        TabItem(title = "发现", iconRes = R.drawable.ic_discover, fragmentClass = DiscoverFragment::class.java),
        TabItem(title = "消息", iconRes = R.drawable.ic_msg, fragmentClass = MessageFragment::class.java,
                badge = TabBadge(count = 99)),
        TabItem(title = "我的", iconRes = R.drawable.ic_me, fragmentClass = ProfileFragment::class.java),
    )
}

// ═══════════════════════════════════════════════════════════
// 场景二：可滑动分类页（scrollable + 加粗选中态 + 指示器颜色）
// ═══════════════════════════════════════════════════════════
class CategoryActivity : TabHostActivity<ActivityCategoryBinding, CategoryModel>() {
    override fun onCreateTabConfig() = TabConfig(
        tabMode = TabLayout.MODE_SCROLLABLE,
        offscreenPageLimit = 2,
        style = TabStyle(
            indicator = TabIndicatorStyle(
                color = 0xFFFF5722.toInt(),
                heightDp = 3,
                cornerRadiusDp = 1.5f,
            ),
            textStyle = TabTextStyle(
                selectedColor = 0xFFFF5722.toInt(),
                unselectedColor = 0xFF9E9E9E.toInt(),
                selectedSizeSp = 16f,
                unselectedSizeSp = 14f,
                selectedTypeface = Typeface.DEFAULT_BOLD,
                allCaps = false,
            ),
            itemStyle = TabItemStyle(
                padding = TabPadding(16f, 12f, 16f, 12f),
            ),
        ),
    )
    override fun onCreateTabs(): List<TabItem<*>> =
        categories.map { cat ->
            TabItem(title = cat.name, fragmentClass = CategoryListFragment::class.java,
                    arguments = bundleOf("categoryId" to cat.id))
        }
}

// ═══════════════════════════════════════════════════════════
// 场景三：新闻 App 顶部 Tab（红底白字 + 白色指示器）
// ═══════════════════════════════════════════════════════════
class NewsActivity : TabHostActivity<ActivityNewsBinding, NewsModel>() {
    override fun onCreateTabConfig() = TabConfig(
        tabMode = TabLayout.MODE_SCROLLABLE,
        style = TabStyle(
            tabBackground = 0xFFD32F2F.toInt(),
            indicator = TabIndicatorStyle(
                color = Color.WHITE,
                heightDp = 3,
            ),
            textStyle = TabTextStyle(
                selectedColor = Color.WHITE,
                unselectedColor = 0xCCFFFFFF.toInt(),
                selectedSizeSp = 16f,
                unselectedSizeSp = 14f,
                allCaps = false,
            ),
        ),
    )
    override fun onCreateTabs() = listOf(
        TabItem("推荐", fragmentClass = RecommendFragment::class.java),
        TabItem("热点", fragmentClass = HotFragment::class.java),
        TabItem("科技", fragmentClass = TechFragment::class.java),
        TabItem("财经", fragmentClass = FinanceFragment::class.java),
    )
}

// ═══════════════════════════════════════════════════════════
// 场景四：完全自定义 Tab View（带未读数气泡的复杂布局）
// ═══════════════════════════════════════════════════════════
class SocialActivity : TabHostActivity<ActivitySocialBinding, SocialModel>() {
    override fun onCreateTabConfig() = TabConfig(
        style = TabStyle(
            // 自定义 Tab View 会覆盖 textStyle 和 itemStyle 的大部分行为
            customTabViewProvider = object : CustomTabViewProvider {
                override fun createView(
                    inflater: LayoutInflater, parent: TabLayout,
                    position: Int, item: TabItem<*>
                ): View {
                    return inflater.inflate(R.layout.custom_tab_with_badge, parent, false)
                }

                override fun bindView(view: View, position: Int, item: TabItem<*>) {
                    view.findViewById<TextView>(R.id.tabTitle).text = item.title
                    view.findViewById<ImageView>(R.id.tabIcon)
                        .setImageResource(item.iconRes ?: 0)
                }

                override fun onSelectedChanged(view: View, selected: Boolean) {
                    val title = view.findViewById<TextView>(R.id.tabTitle)
                    val indicator = view.findViewById<View>(R.id.tabIndicator)
                    title.setTextColor(if (selected) Color.RED else Color.GRAY)
                    title.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
                    indicator.visibility = if (selected) View.VISIBLE else View.GONE
                }

                override fun onBadgeChanged(view: View, badge: TabBadge?) {
                    val bubble = view.findViewById<View>(R.id.badgeBubble)
                    if (badge != null) {
                        bubble.visibility = View.VISIBLE
                        view.findViewById<TextView>(R.id.badgeText).text =
                            if (badge.showAsDot) "" else "${badge.count}"
                    } else {
                        bubble.visibility = View.GONE
                    }
                }
            },
        ),
    )
    override fun onCreateTabs() = listOf(
        TabItem("动态", iconRes = R.drawable.ic_feed, fragmentClass = FeedFragment::class.java,
                badge = TabBadge(showAsDot = true)),
        TabItem("消息", iconRes = R.drawable.ic_chat, fragmentClass = ChatFragment::class.java,
                badge = TabBadge(count = 3)),
        TabItem("通知", iconRes = R.drawable.ic_bell, fragmentClass = NotiFragment::class.java),
    )
}

// ═══════════════════════════════════════════════════════════
// 场景五：Tab 内嵌懒加载 + 可见性埋点
// ═══════════════════════════════════════════════════════════
class HomeFragment : BaseTabFragment<FragmentHomeBinding, HomeModel>() {
    override fun onTabFirstVisible() {
        viewModel.loadHomeData()         // 仅首次可见时请求
        Analytics.track("home_tab_view") // 首次曝光埋点
    }
    override fun onTabSelected() {
        // 每次切回来都刷新（如果从别的页面回来数据可能过期）
        viewModel.refreshIfStale()
    }
    override fun onTabUnselected() {
        binding.recyclerView.stopScroll()
    }
}

// ═══════════════════════════════════════════════════════════
// 场景六：动态角标（配合 ViewModel）
// ═══════════════════════════════════════════════════════════
class MessageFragment : BaseTabFragment<FragmentMessageBinding, MessageModel>() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.unreadCount.observe(viewLifecycleOwner) { count ->
            val tabPosition = 2 // 消息 Tab 在第 3 个位置
            (requireActivity() as? TabHostActivity<*, *>)?.tabViewPager
                ?.updateBadge(tabPosition, TabBadge(count = count))
        }
    }
}
```

---

## 5. 实施计划

### 5.1 依赖变更

| 文件 | 变更 |
|------|------|
| `gradle/libs.versions.toml` | 新增 `viewpager2` 版本和 library 声明 |
| `itg-ui/build.gradle.kts` | 新增 `implementation(project(":itg-base"))` + `implementation(libs.viewpager2)` |
| `itg-ui/build.gradle.kts` | 新增 `viewBinding = true`（BaseTabFragment 的 VB 泛型需要） |

### 5.2 新增文件清单

```
itg-ui/src/main/java/com/itg/itg_ui/
├── tab/
│   ├── TabItem.kt              # Tab 描述数据类
│   ├── TabConfig.kt            # 全局 Tab 行为配置
│   ├── TabBadge.kt             # 角标配置
│   ├── TabViewPagerAbility.kt  # TabLayout + VP2 绑定能力（含样式应用）
│   ├── GenericTabAdapter.kt    # FragmentStateAdapter 实现
│   ├── BaseTabFragment.kt      # Tab 内容 Fragment 基类
│   ├── TabHostActivity.kt      # Tab 宿主 Activity
│   └── TabHostFragment.kt      # Tab 宿主 Fragment（嵌套场景）
├── style/
│   ├── TabStyle.kt             # 视觉样式聚合入口
│   ├── TabIndicatorStyle.kt    # 指示器样式配置
│   ├── TabTextStyle.kt         # 文字样式配置
│   ├── TabItemStyle.kt         # Tab 项样式配置
│   ├── TabPadding.kt           # 通用四向内边距
│   └── CustomTabViewProvider.kt # 自定义 Tab View 接口
└── res/
    └── layout/
        └── itg_tab_host_default.xml  # 默认宿主布局
```

### 5.3 实施步骤

| 阶段 | 内容 | 预估 |
|------|------|------|
| **Phase 1** | 依赖配置：`libs.versions.toml` + `itg-ui/build.gradle.kts` | 极小 |
| **Phase 2** | 数据模型：`TabItem` / `TabConfig` / `TabBadge` | 纯数据类，无外部依赖 |
| **Phase 3** | Adapter：`GenericTabAdapter` | 依赖 ViewPager2 |
| **Phase 4** | Ability：`TabViewPagerAbility` | 核心逻辑，依赖 Phase 2+3 |
| **Phase 5** | 基类：`BaseTabFragment` | 依赖 itg-base 的 `AutoBindingBaseFragment` |
| **Phase 6** | 容器：`TabHostActivity` + `TabHostFragment` | 依赖 Phase 4+5 |
| **Phase 7** | 默认布局 + 资源 ID 定义 | XML 资源 |
| **Phase 8** | 集成测试：在 `app` 模块中写 Demo | 验证端到端流程 |

---

## 6. 扩展点 & 未来方向

### 6.1 当前不实现，但预留在架构中

| 扩展方向 | 预留方式 | 触发条件 |
|---------|---------|---------|
| **Tab 拖拽排序** | `TabConfig` 中增加 `draggable: Boolean` | 业务需要时可加 |
| **Tab 的 show/hide 控制** | `TabItem` 中增加 `visible: Boolean`，Adapter 过滤 | 权限控制场景 |
| **Tab 切换动画** | `ViewPager2.setPageTransformer()` 通过 Config 注入 | 运营需求 |
| **与 BottomNavigation 联动** | 新增 `BottomNavigationAbility`，复用同样的 `TabItem` 模型 | 主流 App 导航模式 |
| **与 TheRouter 混合导航** | Tab 内容 Fragment 内部可独立使用 TheRouter 跳转 | 复杂业务 |

### 6.2 BottomNavigation 扩展预览

同一套 `TabItem` / `TabConfig` 模型可以直接驱动 BottomNavigationView：

```kotlin
// 未来扩展：BottomNavigationAbility（本方案不实现，仅示意）
class BottomNavigationAbility : LifeAbility() {
    fun bind(bnv: BottomNavigationView, navController: NavController, tabs: List<TabItem<*>>) {
        // 使用同样的 TabItem 模型设置 menu
    }
}
```

---

## 7. 设计原则汇总

| 原则 | 体现 |
|------|------|
| **约定优于配置** | 布局 id 默认 `tabLayout` / `viewPager`，TabConfig 全部有默认值 |
| **声明式** | 业务层仅声明 `List<TabItem>` + `TabStyle`，不写 Adapter / Mediator / Drawable 样板代码 |
| **三级定制** | Config → Protected open → 完全替换，与 itg-base 完全一致。样式也有三级：`TabIndicatorStyle` / `TabTextStyle` → 覆写 `CustomTabViewProvider` → 整体替换 `TabViewPagerAbility` |
| **样式正交** | 指示器 / 文字 / Tab 项 / 容器 四个维度独立配置，互不干扰；未配置 = Material 默认 |
| **XML 兼容** | `TabConfig(style = null)` 时完全不介入样式，开发者可在布局 XML 中用 `app:` 属性控制 |
| **生命周期安全** | TabViewPagerAbility 继承 LifeAbility，onDestroy 自动清理 Mediator |
| **内存安全** | FragmentStateAdapter 自动管理 Fragment 生命周期；Event 一次性消费 |
| **类型安全** | `TabItem<F : Fragment>` 带泛型约束，编译期检查 Fragment 类型 |
| **零侵入** | 业务层可选择性使用；不用 TabHostActivity 可以自己拿 TabViewPagerAbility 拼 |
