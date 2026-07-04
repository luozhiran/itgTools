# itg-ui

基于 `itg-base` 组合式 MVVM 架构的 **TabLayout + ViewPager2 通用框架**——将多 Tab 场景的样板代码归零，业务层仅需声明"有哪些 Tab、每个 Tab 长什么样"。

## 要求

| 项目 | 最低版本 |
|------|---------|
| minSdk | 24 (Android 7.0) |
| compileSdk | 34+ |
| Kotlin | 1.9+ |
| Android Gradle Plugin | 8.0+ |
| itg-base | 1.0.0+ |

## 依赖

```kotlin
// build.gradle.kts
dependencies {
    // itg-ui 内部已传递依赖 itg-base，无需单独声明
    implementation("com.example.itg:itg-ui:1.0.0")
}
```

**必须开启 ViewBinding 或 DataBinding**（继承自 itg-base 的强制要求）：

```kotlin
android {
    buildFeatures {
        viewBinding = true  // 或 dataBinding = true
    }
}
```

## 架构

```
TabHostActivity<VB, VM>                    TabHostFragment<VB, VM>
│                                           │
├── binding: VB     ← 自动 inflate          ├── binding: VB     ← 自动 inflate
├── viewModel: VM   ← 自动创建              ├── viewModel: VM   ← 自动创建
│                                           │
├── tabViewPager: TabViewPagerAbility       ├── tabViewPager: TabViewPagerAbility
│   ├── bind(...) / unbind()                │   └── 同上（可覆写 createTabViewPager()）
│   ├── selectTab(pos)                      │
│   ├── updateBadge(pos, badge)             │
│   └── getFragmentAt(pos)                  │
│                                           │
├── 自动继承 itg-base 全部 Ability：         ├── 自动继承 itg-base 全部 Ability：
│   systemBars / messages / permissions     │   messages / permissions / uiState
│   uiState / launcher                      │
│                                           │
└── BaseTabFragment<VB, VM>  ← Tab 内容基类 │
    ├── onTabFirstVisible()  ← 懒加载       │
    ├── onTabSelected()      ← 可见回调     │
    └── onTabUnselected()    ← 不可见回调    │
```

## 快速开始

### 1. 创建 Tab 内容 Fragment

```kotlin
// 首页 Tab
class HomeFragment : BaseTabFragment<FragmentHomeBinding, HomeModel>() {

    override fun onTabFirstVisible() {
        // 首次可见时才加载——不会在 Activity 启动时一次性加载所有 Tab
        viewModel.loadData()
    }
}
```

```kotlin
// 消息 Tab（带未读数角标）
class MessageFragment : BaseTabFragment<FragmentMessageBinding, MessageModel>() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 观察未读数，同步到 Tab 角标
        viewModel.unreadCount.observe(viewLifecycleOwner) { count ->
            (requireActivity() as? TabHostActivity<*, *>)
                ?.tabViewPager?.updateBadge(2, TabBadge(count = count))
        }
    }
}
```

### 2. 创建宿主 Activity

```kotlin
@Route(path = RoutePath.MAIN_HOME_ACTIVITY)
class MainActivity : TabHostActivity<ActivityMainBinding, MainModel>() {

    override fun onCreateTabs(): List<TabItem<*>> = listOf(
        TabItem("首页", iconRes = R.drawable.ic_home, fragmentClass = HomeFragment::class.java),
        TabItem("发现", iconRes = R.drawable.ic_discover, fragmentClass = DiscoverFragment::class.java),
        TabItem("消息", iconRes = R.drawable.ic_msg, fragmentClass = MessageFragment::class.java,
                badge = TabBadge(showAsDot = true)),
        TabItem("我的", iconRes = R.drawable.ic_me, fragmentClass = ProfileFragment::class.java),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Tab 无关的初始化放这里
    }
}
```

### 3. 布局

```xml
<!-- activity_main.xml -->
<layout>
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical">

        <com.google.android.material.tabs.TabLayout
            android:id="@+id/tabLayout"
            android:layout_width="match_parent"
            android:layout_height="wrap_content" />

        <androidx.viewpager2.widget.ViewPager2
            android:id="@+id/viewPager"
            android:layout_width="match_parent"
            android:layout_height="0dp"
            android:layout_weight="1" />

    </LinearLayout>
</layout>
```

> **约定**：布局中 `TabLayout` 的 id 必须为 `tabLayout`，`ViewPager2` 的 id 必须为 `viewPager`。如需自定义 id，覆写 `tabLayoutId` / `viewPagerId` 属性即可。

### 4. 完成

不需要手写 Adapter、TabLayoutMediator、Fragment 实例化、生命周期管理——全部自动完成。

---

## TabItem — Tab 声明

```kotlin
data class TabItem<F : Fragment>(
    val title: String? = null,              // Tab 标题（与 titleRes 二选一）
    val titleRes: Int? = null,              // Tab 标题资源 ID
    val icon: Drawable? = null,             // Tab 图标 Drawable
    val iconRes: Int? = null,               // Tab 图标资源 ID
    val fragmentClass: Class<F>,            // Fragment::class.java（必填）
    val arguments: Bundle? = null,          // 传给 Fragment 的参数
    val badge: TabBadge? = null,            // 初始角标
    val tag: String = ...                   // 唯一标识，默认用类名
)
```

### 动态 Tab 场景

```kotlin
// 从服务端下发的分类列表动态生成 Tab
class CategoryActivity : TabHostActivity<ActivityCategoryBinding, CategoryModel>() {

    override fun onCreateTabs(): List<TabItem<*>> {
        val categories = viewModel.categories.value ?: emptyList()
        return categories.map { cat ->
            TabItem(
                title = cat.name,
                fragmentClass = CategoryListFragment::class.java,
                arguments = bundleOf("categoryId" to cat.id),
            )
        }
    }
}
```

---

## TabConfig — 行为配置

```kotlin
override fun onCreateTabConfig() = TabConfig(
    tabMode = TabLayout.MODE_SCROLLABLE,  // MODE_FIXED / MODE_SCROLLABLE
    tabGravity = TabLayout.GRAVITY_FILL,  // GRAVITY_FILL / GRAVITY_CENTER
    defaultPosition = 0,                  // 默认选中
    swipeable = true,                     // 是否可滑动切换
    offscreenPageLimit = 2,               // 离屏保留页数
    autoTitle = true,                     // 是否自动设置 Tab 标题
    lazyLoadOnFirstSelect = true,         // 首次选中时是否触发 onTabFirstVisible
    style = ...,                          // 视觉样式（见下方）
    onPageSelected = { pos, fragment -> },// 页面切换回调（额外逻辑，可见性回调已自动处理）
)
```

> **`lazyLoadOnFirstSelect`**：设为 `false` 时禁用懒加载机制，`onTabFirstVisible()` 不会被自动触发。**注意**：此时 `onTabSelected()` 仍然会在首次选中和后续每次选中时触发。适合所有 Tab 可以在创建时立即加载数据的场景。

---

## TabStyle — 样式定制

样式系统按**四个维度**拆分为独立子配置，每个子配置中的字段都可选（`null` = 保持 Material 默认）：

```
TabStyle
├── indicator: TabIndicatorStyle  → 指示器
├── textStyle: TabTextStyle       → 文字
├── itemStyle: TabItemStyle       → Tab 项布局
└── customTabViewProvider         → 完全自定义 View（可选）
```

### 指示器 `TabIndicatorStyle`

```kotlin
TabIndicatorStyle(
    color = 0xFFFF5722.toInt(),        // 颜色（null = colorPrimary）
    heightDp = 3,                       // 高度（null = 2dp）
    widthDp = 24,                       // 固定宽度（null = 自动跟随 Tab 文字宽度）
    cornerRadiusDp = 1.5f,              // 圆角（null = 直角）
    horizontalPaddingDp = 4f,           // 指示器左右内边距，缩小相对 Tab 的宽度
    distanceFromTextDp = 8,             // 指示器与文字的垂直间距
    gravity = TabLayout.INDICATOR_GRAVITY_BOTTOM,  // BOTTOM / TOP / STRETCH
    animationEnabled = true,            // 是否启用切换动画
    animationDurationMs = 300,          // 动画时长（ms）
    animationInterpolator = LinearOutSlowInInterpolator(),  // 动画插值器
    // 高级：完全自定义
    drawable = myCustomDrawable,        // 自定义 Drawable（覆盖 color/height/cornerRadius）
)
```

| 场景 | 代码 |
|------|------|
| 红色指示器 | `TabIndicatorStyle(color = 0xFFFF0000.toInt())` |
| 圆角指示器 3dp 高 | `TabIndicatorStyle(heightDp = 3, cornerRadiusDp = 1.5f)` |
| 指示器比文字窄 | `TabIndicatorStyle(horizontalPaddingDp = 8f)` |
| 隐藏指示器 | `TabIndicatorStyle(color = Color.TRANSPARENT, heightDp = 0)` |
| 顶部指示器 | `TabIndicatorStyle(gravity = TabLayout.INDICATOR_GRAVITY_TOP)` |
| 快速动画 | `TabIndicatorStyle(animationDurationMs = 150)` |

### 文字 `TabTextStyle`

```kotlin
TabTextStyle(
    selectedColor = 0xFFFF5722.toInt(),        // 选中颜色
    unselectedColor = 0xFF9E9E9E.toInt(),      // 未选中颜色
    selectedSizeSp = 16f,                       // 选中字号（null = 不变）
    unselectedSizeSp = 14f,                     // 未选中字号（null = 不变）
    selectedTypeface = Typeface.DEFAULT_BOLD,   // 选中加粗
    typeface = Typeface.DEFAULT,                // 全局字体
    allCaps = false,                            // 取消大写
    letterSpacing = 0.05f,                      // 字母间距
)
```

| 场景 | 代码 |
|------|------|
| 选中变色 | `TabTextStyle(selectedColor = 0xFF1976D2.toInt(), unselectedColor = 0xFF757575.toInt())` |
| 选中加粗 + 变大 | `TabTextStyle(selectedSizeSp = 16f, unselectedSizeSp = 13f, selectedTypeface = DEFAULT_BOLD)` |
| 自定义字体 | `TabTextStyle(typeface = Typeface.createFromAsset(assets, "fonts/myfont.ttf"))` |

### Tab 项 `TabItemStyle`

```kotlin
TabItemStyle(
    // 尺寸
    padding = TabPadding.horizontal(16f),      // Tab 内边距
    minWidthDp = 80,                            // Tab 最小宽度（null = Material 默认）
    // 背景（同时支持 @ColorInt 和 @DrawableRes）
    selectedBackground = R.drawable.bg_tab_active,   // 选中背景（drawable 或颜色值）
    unselectedBackground = Color.TRANSPARENT,        // 未选中背景
    rippleColor = 0x40FF5722.toInt(),                 // 水波纹颜色
    // 图标
    iconGravity = 1,  // 1=ICON_GRAVITY_TOP（图标在上），0=左 2=右 3=下
    iconSizeDp = 24,                            // 图标大小（null = 24dp）
    iconTint = ColorStateList.valueOf(0xFF1976D2.toInt()), // 图标着色
    iconTextGapDp = 8,                          // 图标与文字间距（null = Material 默认）
)
```

> **背景值智能解析**：`selectedBackground` / `unselectedBackground` 传入的 `Int` 值会自动判断类型——以 `0x7F`/`0x01` 开头的视为 `@DrawableRes`，通过 `context.getDrawable()` 解析；其他值视为 `@ColorInt`，包装为 `ColorDrawable`。

### `TabPadding` 辅助类

```kotlin
TabPadding.all(8f)           // 四边 8dp
TabPadding.horizontal(16f)   // 左右 16dp，上下 0
TabPadding.vertical(12f)     // 上下 12dp，左右 0
TabPadding(16f, 8f, 16f, 8f) // 手动指定四向
```

### 完整示例：新闻 App 红底白字风格

```kotlin
class NewsActivity : TabHostActivity<ActivityNewsBinding, NewsModel>() {

    override fun onCreateTabConfig() = TabConfig(
        tabMode = TabLayout.MODE_SCROLLABLE,
        style = TabStyle(
            tabBackground = 0xFFD32F2F.toInt(),       // TabLayout 红色背景
            tabElevationDp = 4f,                       // 阴影高度（可选）
            tabDividerDrawable = dividerDrawable,      // Tab 之间分割线（可选）
            indicator = TabIndicatorStyle(
                color = Color.WHITE,
                heightDp = 3,
            ),
            textStyle = TabTextStyle(
                selectedColor = Color.WHITE,
                unselectedColor = 0xCCFFFFFF.toInt(),  // 半透明白色
                unselectedSizeSp = 14f,
                selectedSizeSp = 16f,
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
```

### 完整示例：底部导航四 Tab（图标 + 小字 + 无指示器）

```kotlin
override fun onCreateTabConfig() = TabConfig(
    tabMode = TabLayout.MODE_FIXED,
    swipeable = false,
    style = TabStyle(
        indicator = TabIndicatorStyle(color = Color.TRANSPARENT, heightDp = 0),
        textStyle = TabTextStyle(
            selectedColor = 0xFF1976D2.toInt(),
            unselectedColor = 0xFF757575.toInt(),
            selectedSizeSp = 11f,
            unselectedSizeSp = 11f,
            allCaps = false,
        ),
        itemStyle = TabItemStyle(
            padding = TabPadding.vertical(8f),
            iconGravity = 1,  // 图标在文字上方
            iconSizeDp = 24,
        ),
    ),
)

override fun onCreateTabs() = listOf(
    TabItem("首页", iconRes = R.drawable.ic_home, fragmentClass = HomeFragment::class.java),
    TabItem("发现", iconRes = R.drawable.ic_discover, fragmentClass = DiscoverFragment::class.java),
    TabItem("消息", iconRes = R.drawable.ic_msg, fragmentClass = MessageFragment::class.java,
            badge = TabBadge(count = 99)),
    TabItem("我的", iconRes = R.drawable.ic_me, fragmentClass = ProfileFragment::class.java),
)
```

---

## BaseTabFragment — Tab 内容页

每个 Tab 的内容 Fragment 继承 `BaseTabFragment<VB, VM>`，在 `AutoBindingBaseFragment` 的全部能力之上增加三个生命周期回调：

```kotlin
abstract class BaseTabFragment<VB : ViewBinding, VM : ItgModel>
    : AutoBindingBaseFragment<VB, VM>() {

    /** 当前 Tab 是否可见 */
    val isTabVisible: Boolean

    /** Tab 首次可见 —— 只触发一次，用于懒加载 */
    protected open fun onTabFirstVisible()  // 默认调用 viewModel.loadData()

    /** 每次切回该 Tab 时触发 */
    protected open fun onTabSelected()

    /** 每次切走时触发 */
    protected open fun onTabUnselected()
}
```

### 典型实现

```kotlin
class HomeFragment : BaseTabFragment<FragmentHomeBinding, HomeModel>() {

    override fun onTabFirstVisible() {
        viewModel.loadHomeData()          // 懒加载：只请求一次
        Analytics.track("home_tab_view")  // 首次曝光埋点
    }

    override fun onTabSelected() {
        viewModel.refreshIfStale()        // 每次切回来检查数据是否过期
        binding.recyclerView.smoothScrollToPosition(0)
    }

    override fun onTabUnselected() {
        binding.recyclerView.stopScroll() // 暂停滚动，释放资源
    }
}
```

### 多个懒加载 Fragment 的默认行为

```
Activity 启动
  ├─ Tab 0 (HomeFragment)     → onTabFirstVisible() 立即触发（默认选中）
  ├─ Tab 1 (DiscoverFragment) → 未触发（未选中过）
  ├─ Tab 2 (MessageFragment)  → 未触发
  └─ Tab 3 (ProfileFragment)  → 未触发

用户滑动到 Tab 2
  └─ MessageFragment → onTabFirstVisible() 触发，loadData() 执行

用户滑回 Tab 0
  └─ HomeFragment → onTabSelected() 触发，但 onTabFirstVisible 不再触发
```

---

## 角标（Badge）

### 静态角标（初始化时声明）

```kotlin
TabItem("消息", fragmentClass = MessageFragment::class.java,
        badge = TabBadge(count = 99))           // 未读数 "99"

TabItem("动态", fragmentClass = FeedFragment::class.java,
        badge = TabBadge(showAsDot = true))      // 纯红点
```

```kotlin
data class TabBadge(
    val count: Int = 0,          // 角标数字（≤0 配合 showAsDot 展示纯红点）
    val showAsDot: Boolean = false, // 纯红点模式
    val backgroundColor: Int? = null, // 背景色（null = Material 红色）
    val maxNumber: Int = 99,      // 最大显示数字，超过显示 "99+"
)
```

### 动态角标（运行时更新）

```kotlin
// 在 Fragment 中通过宿主更新自己的角标
class MessageFragment : BaseTabFragment<FragmentMessageBinding, MessageModel>() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.unreadCount.observe(viewLifecycleOwner) { count ->
            // 方式一：通过 Activity
            (requireActivity() as? TabHostActivity<*, *>)
                ?.tabViewPager?.updateBadge(2, TabBadge(count = count))

            // 方式二：通过父 Fragment（嵌套 Tab 场景）
            (parentFragment as? TabHostFragment<*, *>)
                ?.tabViewPager?.updateBadge(2, TabBadge(count = count))
        }
    }
}

// 也可以在 Activity/Fragment 中直接操作
tabViewPager.updateBadge(0, TabBadge(showAsDot = true))   // 给第 1 个 Tab 设红点
tabViewPager.updateBadge(1, null)                          // 清除第 2 个 Tab 的角标
```

---

## 完全自定义 Tab View

当 `TabTextStyle` + `TabItemStyle` + `TabItem.icon` 的组合无法满足需求时（如需要在图标右上角叠加角标气泡、显示两级文字等），使用 `CustomTabViewProvider`：

```kotlin
override fun onCreateTabConfig() = TabConfig(
    style = TabStyle(
        customTabViewProvider = object : CustomTabViewProvider {

            // 1. 创建自定义 View（每个 Tab 调用一次）
            override fun createView(
                inflater: LayoutInflater, parent: TabLayout,
                position: Int, item: TabItem<*>
            ): View {
                return inflater.inflate(R.layout.custom_tab_badge, parent, false)
            }

            // 2. 绑定数据
            override fun bindView(view: View, position: Int, item: TabItem<*>) {
                view.findViewById<TextView>(R.id.tabTitle).text = item.title
                view.findViewById<ImageView>(R.id.tabIcon)
                    .setImageResource(item.iconRes ?: R.drawable.ic_default)
            }

            // 3. 选中/未选中状态变化（每次切换触发）
            override fun onSelectedChanged(view: View, selected: Boolean) {
                val title = view.findViewById<TextView>(R.id.tabTitle)
                val indicator = view.findViewById<View>(R.id.activeIndicator)
                title.setTextColor(
                    if (selected) view.context.getColor(R.color.tab_active)
                    else view.context.getColor(R.color.tab_inactive)
                )
                indicator.visibility = if (selected) View.VISIBLE else View.GONE
            }

            // 4. 角标更新（当调用 updateBadge() 时触发）
            override fun onBadgeChanged(view: View, badge: TabBadge?) {
                val bubble = view.findViewById<View>(R.id.badgeBubble)
                val countText = view.findViewById<TextView>(R.id.badgeCount)
                if (badge != null && (badge.count > 0 || badge.showAsDot)) {
                    bubble.visibility = View.VISIBLE
                    countText.visibility = if (badge.showAsDot) View.GONE else View.VISIBLE
                    countText.text = "${badge.count}"
                } else {
                    bubble.visibility = View.GONE
                }
            }
        }
    )
)
```

> **注意**：使用 `customTabViewProvider` 后，`TabTextStyle` 和 `TabItemStyle` 中的大部分字段将不再对自定义 View 生效——需要自己在 `onSelectedChanged` 中处理。

---

## 嵌套 Tab（Fragment 内嵌 TabLayout）

当一个 Tab 页面内部还需要二级 Tab 时，使用 `TabHostFragment`：

```kotlin
// 外层 Tab 的内容 Fragment 本身就是一个 Tab 宿主
class CategoryPageFragment : TabHostFragment<FragmentCategoryBinding, CategoryModel>() {

    override fun onCreateTabs(): List<TabItem<*>> = listOf(
        TabItem("全部", fragmentClass = AllFragment::class.java),
        TabItem("上衣", fragmentClass = TopFragment::class.java),
        TabItem("裤子", fragmentClass = PantsFragment::class.java),
        TabItem("鞋子", fragmentClass = ShoesFragment::class.java),
    )

    override fun onCreateTabConfig() = TabConfig(
        tabMode = TabLayout.MODE_SCROLLABLE,
        style = TabStyle(
            indicator = TabIndicatorStyle(heightDp = 3, cornerRadiusDp = 1.5f),
            textStyle = TabTextStyle(selectedSizeSp = 16f, unselectedSizeSp = 14f,
                                     selectedTypeface = Typeface.DEFAULT_BOLD, allCaps = false),
            itemStyle = TabItemStyle(padding = TabPadding.horizontal(16f)),
        ),
    )
}
```

布局与 Activity 版完全相同（`tabLayout` + `viewPager`）。子 Tab Fragment 的 ViewModel 与宿主 Activity 共享。

---

## 不使用 TabHostActivity 的手动拼装

如果不想继承 `TabHostActivity`（比如已经继承了其他基类），可以手动使用 `TabViewPagerAbility`：

```kotlin
class MyCustomActivity : SomeOtherBaseActivity() {

    private val tabViewPager = TabViewPagerAbility()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_with_tabs)

        tabViewPager.inject(this)  // 注入生命周期

        tabViewPager.bind(
            tabLayout = findViewById(R.id.tabLayout),
            viewPager = findViewById(R.id.viewPager),
            tabs = listOf(
                TabItem("Tab A", fragmentClass = FragmentA::class.java),
                TabItem("Tab B", fragmentClass = FragmentB::class.java),
            ),
            config = TabConfig(),
        )
    }
}
```

---

## 不使用 BaseTabFragment 的手动拼装

如果某个 Tab 的 Fragment 不需要懒加载和可见性回调，可以直接继承 `AutoBindingBaseFragment`：

```kotlin
// 不需要懒加载的简单 Fragment
class SimpleFragment : AutoBindingBaseFragment<FragmentSimpleBinding, SimpleModel>() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 直接加载数据（不需要等首次可见）
        viewModel.loadData()
    }
}
```

`TabViewPagerAbility` 的页面切换回调对 `BaseTabFragment` 和 `AutoBindingBaseFragment` 都安全——非 `BaseTabFragment` 子类的 Fragment 会被安全跳过。

---

## 能力清单

### TabHostActivity / TabHostFragment 继承自 itg-base，自动拥有：

| 能力 | 访问方式 | 说明 |
|------|---------|------|
| binding | `binding` | 布局绑定，泛型直出 |
| viewModel | `viewModel` | ViewModel，泛型直出 |
| Tab 绑定 | `tabViewPager` | 切换 Tab、更新角标、获取 Fragment |
| 边到边 | `systemBars` | 系统栏 insets 处理 |
| Toast/Snackbar | `messages` | 生命周期安全的消息提示 |
| 权限 | `permissions` | 运行时权限请求 |
| ActivityResult | `launcher` | 选图/拍照/跳转回调 |
| UI 状态 | `uiState` | Loading/Empty/Error 三态，自动观察 viewModel.loading |

### TabViewPagerAbility 公开方法：

| 方法 | 说明 |
|------|------|
| `inject(activity)` | 注入宿主 Activity 生命周期（bind 前必须调用） |
| `bind(tabLayout, viewPager, tabs, hostFragment?, config?)` | 绑定 TabLayout + ViewPager2，应用样式和配置 |
| `unbind()` | 解绑并清理所有资源（detach Mediator、移除回调、清空缓存）。调用后可重新 `bind()` |
| `selectTab(position, smoothScroll)` | 编程式切换 Tab |
| `currentPosition()` | 获取当前选中位置 |
| `updateBadge(position, badge)` | 更新指定 Tab 角标（传 null 清除） |
| `getFragmentAt(position)` | 获取指定位置的 Fragment 实例（可能为 null） |

> **`bind()` 调用限制**：重复调用 `bind()` 会抛出 `IllegalStateException`。如需切换 Tab 列表，先调用 `unbind()` 再 `bind()`。

---

## XML 样式 vs Kotlin 样式

`TabConfig.style` 和布局 XML 中的 Material 属性可以**共存**，优先级规则如下：

```
如果 TabConfig(style = null)：
    → 完全不介入，全部由 XML 中的 app:tabIndicatorColor 等属性控制

如果 TabConfig(style = TabStyle(...))：
    → Kotlin 配置的字段覆盖对应属性
    → 未配置的字段（null）保持 Material 默认，不受 XML 影响
    → 例如：style.indicator.color = RED 覆盖 XML 中的 app:tabIndicatorColor，
      但 style.indicator.height = null，所以高度仍取 Material 默认 2dp
```

**推荐做法**：

| 场景 | 方式 |
|------|------|
| 全局统一的 Tab 风格 | 在 `themes.xml` 中定义 `Widget.Material3.TabLayout` 样式 |
| 单个页面的独特风格 | 在 `onCreateTabConfig()` 中配置 `TabStyle` |
| 完全不自定义、与系统一致 | 不覆写 `onCreateTabConfig()`（默认 `TabConfig()`，style = null） |

---

## 自定义 id

如果布局中的 id 不是默认的 `tabLayout` / `viewPager`：

```kotlin
class MyActivity : TabHostActivity<ActivityMyBinding, MyModel>() {
    override val tabLayoutId = R.id.myCustomTabLayout
    override val viewPagerId = R.id.myCustomViewPager
    // ...
}
```

---

## 内存安全

| 机制 | 说明 |
|------|------|
| FragmentStateAdapter | 不可见页面自动保存/恢复状态，不会一次性创建所有 Fragment |
| LifeAbility 自动解绑 | `onDestroy` → `unbind()` 自动 detach TabLayoutMediator、移除 PageChangeCallback |
| Event 一次性消费 | Tab 切换事件配置变更后不重复触发 |
| viewLifecycleOwner | Fragment 视图重建时自动解绑 LiveData 观察者 |
| WeakReference 缓存 | `GenericTabAdapter` 内部使用 `WeakReference<Fragment>` 缓存，不阻止 FragmentManager 回收 |
| post Runnable 清理 | `TabHostActivity.onDestroy` / `TabHostFragment.onDestroyView` 中取消待执行的 bind Runnable |
| config 引用释放 | `unbind()` 时重置 `config = TabConfig()`，解除 `customTabViewProvider` 隐式 Activity 引用 |

---

## 三级定制模式

遵循 itg-base 建立的模式：

| 级别 | 方式 | 适用 | 示例 |
|------|------|------|------|
| **L1 Config** | 覆写 `onCreateTabConfig()` | 改行为、改样式 | `TabConfig(swipeable = false, style = TabStyle(...))` |
| **L2 Protected open** | 覆写 `onTabFirstVisible()` 等 | Tab 可见性逻辑 | `onTabSelected() { resumeAnimation() }` |
| **L3 整体替换** | 子类化 `TabViewPagerAbility` + 覆写 `createTabViewPager()` | 完全不同的 Tab 行为 | 见下方示例 |

**L3 示例**——替换 TabViewPagerAbility：

```kotlin
// 自定义 Ability
class MyTabAbility : TabViewPagerAbility() {
    // 覆写 bind() 增加自定义逻辑
    override fun bind(...) {
        super.bind(...)
        // 额外初始化
    }
}

// 在 Activity 中注入
class MyActivity : TabHostActivity<ActivityMainBinding, MainModel>() {
    // 方式一：覆写工厂方法（推荐——保持 lazy 语义）
    override fun createTabViewPager(): TabViewPagerAbility = MyTabAbility()

    // 方式二：直接覆写属性
    // override val tabViewPager by lazy { MyTabAbility() }

    override fun onCreateTabs() = listOf(...)
}
```

---

## 常见问题

### Q: 如何在 Activity 启动时不立即加载任何 Tab？

将 `TabConfig.defaultPosition` 设为所有 Tab 之外的值，然后在合适的时机手动调用 `tabViewPager.selectTab(0)`。但通常建议使用 `onTabFirstVisible()` 懒加载机制，让每个 Fragment 各自决定何时加载。

### Q: 如何禁用某个 Tab？

在 `onCreateTabs()` 中动态过滤列表：

```kotlin
override fun onCreateTabs(): List<TabItem<*>> {
    val tabs = mutableListOf<TabItem<*>>()
    tabs.add(TabItem("首页", ...))
    if (userIsVip) {
        tabs.add(TabItem("VIP", ...))
    }
    tabs.add(TabItem("我的", ...))
    return tabs
}
```

### Q: 如何实现 Tab 切换动画？

```kotlin
// 在 TabHostActivity 的 onCreate 中
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    findViewById<ViewPager2>(R.id.viewPager).setPageTransformer(
        DepthPageTransformer()  // 或自己实现 ViewPager2.PageTransformer
    )
}
```

### Q: BaseTabFragment 中如何拿到宿主 TabViewPagerAbility？

```kotlin
// Activity 宿主
(requireActivity() as? TabHostActivity<*, *>)?.tabViewPager

// Fragment 宿主
(parentFragment as? TabHostFragment<*, *>)?.tabViewPager
```

### Q: 如何动态切换 Tab 列表（如登录前后显示不同 Tab）？

调用 `unbind()` 解绑，然后重新 `bind()`：

```kotlin
class MainActivity : TabHostActivity<ActivityMainBinding, MainModel>() {

    override fun onCreateTabs() = buildTabs()

    private fun buildTabs(): List<TabItem<*>> {
        val tabs = mutableListOf(TabItem("首页", fragmentClass = HomeFragment::class.java))
        if (userIsLoggedIn) {
            tabs.add(TabItem("消息", fragmentClass = MessageFragment::class.java))
        }
        tabs.add(TabItem("我的", fragmentClass = ProfileFragment::class.java))
        return tabs
    }

    fun onLoginStateChanged() {
        tabViewPager.unbind()  // 清理旧绑定
        tabViewPager.bind(     // 重新绑定新列表
            tabLayout = findViewById(R.id.tabLayout),
            viewPager = findViewById(R.id.viewPager),
            tabs = buildTabs(),
            config = onCreateTabConfig(),
        )
    }
}
```

### Q: 不用反射能实例化 Fragment 吗？

当前 `GenericTabAdapter` 内部使用 `Class.newInstance()` 反射实例化 Fragment，这与 itg-base 的 `ViewBindingAbility` 反射 inflate 风格一致。Fragment 必须提供无参构造器——这是 Android Fragment 的标准要求。

---

## 许可

Internal use.
