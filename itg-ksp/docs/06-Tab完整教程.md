# 06 Tab 完整教程

## 1. Activity 宿主

```kotlin
@ItgTabHost(
    groupName = "MainTabs",
    tabMode = TabLayout.MODE_FIXED,
    tabGravity = TabLayout.GRAVITY_FILL,
    defaultPosition = 0,
    swipeable = true,
    offscreenPageLimit = 1,
    autoTitle = true,
    lazyLoadOnFirstSelect = true,
)
class MainTabsActivity :
    TabHostActivity<ActivityMainTabsBinding, MainModel>() {

    override fun onCreateTabs(): List<TabItem<*>> =
        createMainTabsTabItems()

    override fun onCreateTabConfig(): TabConfig =
        createMainTabsTabConfig()
}
```

布局必须包含 `tabLayout` 和 `viewPager`，或者覆写宿主的 id 属性。

## 2. Fragment / 嵌套 Tab 宿主

`@ItgTabHost` 只要求普通 class；同样可以用于 `TabHostFragment`：

```kotlin
@ItgTabHost(groupName = "CategoryTabs")
class CategoryTabsFragment :
    TabHostFragment<FragmentCategoryTabsBinding, CategoryModel>() {

    override fun onCreateTabs() = createCategoryTabsTabItems()
    override fun onCreateTabConfig() = createCategoryTabsTabConfig()
}
```

`TabHostFragment` 会使用 child FragmentManager，并在 `onDestroyView` 解绑。

## 3. 最小 Tab 页面

```kotlin
@ItgTabItem(
    groupName = "MainTabs",
    title = "首页",
    order = 0,
)
class HomeFragment : Fragment()
```

页面可以直接或间接继承 `androidx.fragment.app.Fragment`。继承 `BaseTabFragment` 可获得首次可见、选中和取消选中回调。

## 4. 标题资源

```kotlin
@ItgTabItem(
    groupName = "MainTabs",
    titleResExpression = "com.example.R.string.tab_home",
    order = 0,
)
```

`title` 与 `titleResExpression` 建议二选一。它们都是空时允许生成，但自动标题会为空。

## 5. 图标

```kotlin
@ItgTabItem(
    groupName = "MainTabs",
    title = "消息",
    iconResExpression = "com.example.R.drawable.ic_message",
    order = 1,
)
```

注解生成器支持资源 id，不支持直接生成 Drawable 对象。需要动态图标时，对生成列表执行 `map/copy` 或手写 TabItem。

## 6. Fragment arguments

表达式会原样写入生成文件：

```kotlin
@ItgTabItem(
    groupName = "MainTabs",
    title = "详情",
    argumentsExpression = "android.os.bundleOf(\"source\" to \"tabs\")",
)
```

表达式必须是可在生成文件中以完整限定名解析的 Kotlin 表达式。不要依赖业务文件中的 import。

## 7. 数字角标与红点

数字角标：

```kotlin
@ItgTabItem(
    groupName = "MainTabs",
    title = "通知",
    badgeCount = 8,
)
```

红点：

```kotlin
@ItgTabItem(
    groupName = "MainTabs",
    title = "通知",
    badgeDot = true,
)
```

注解只生成 `count` 和 `showAsDot`。背景色、最大数字等高级 `TabBadge` 参数需要在生成列表上 `copy`：

```kotlin
override fun onCreateTabs() = createMainTabsTabItems().mapIndexed { index, item ->
    if (index == 1) item.copy(
        badge = item.badge?.copy(backgroundColor = Color.BLUE, maxNumber = 999)
    ) else item
}
```

运行时更新：

```kotlin
tabViewPager.updateBadge(1, TabBadge(count = 12))
tabViewPager.updateBadge(1, null)
```

## 8. 顺序与分组

- Host 与 Item 的 `groupName` 必须完全一致。
- Item 按 `order` 升序排列。
- `order` 相同的相对顺序不应作为业务约定；请使用唯一 order。
- `groupName` 会进入生成文件名和函数名，应使用合法 Kotlin 标识符片段，例如 `MainTabs`，不要使用空格、短横线或中文标点。
- 同包内不要声明两个相同 group 的 Host，否则会生成同名文件。

## 9. 配置参数

| 参数 | 说明 |
|---|---|
| `tabMode` | FIXED 或 SCROLLABLE |
| `tabGravity` | FILL 或 CENTER |
| `defaultPosition` | 0-based，必须在 Tab 范围内 |
| `swipeable` | 是否允许 ViewPager2 手势切换 |
| `offscreenPageLimit` | `-1` 或至少 `1` |
| `autoTitle` | 是否自动把 TabItem 标题应用到 TabLayout |
| `lazyLoadOnFirstSelect` | 是否触发 BaseTabFragment 首次选中回调 |

## 10. 样式和回调

注解生成的 `TabConfig` 不包含 `style` 和 `onPageSelected`。用 `copy` 扩展：

```kotlin
override fun onCreateTabConfig() = createMainTabsTabConfig().copy(
    style = TabStyle(
        textStyle = TabTextStyle(
            selectedColor = Color.BLACK,
            unselectedColor = Color.GRAY,
        ),
    ),
    onPageSelected = { position, fragment ->
        // 统计或业务回调
    },
)
```

## 11. 生命周期回调

```kotlin
class HomeFragment : BaseTabFragment<FragmentHomeBinding, MainModel>() {
    override fun onTabFirstVisible() { /* 仅首次选中 */ }
    override fun onTabSelected() { /* 每次选中 */ }
    override fun onTabUnselected() { /* 离开当前页 */ }
}
```

`lazyLoadOnFirstSelect=false` 时不会触发首次懒加载回调，但选中/取消选中仍按 runtime 行为分发。

