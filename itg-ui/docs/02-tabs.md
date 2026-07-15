# 02. Tab 宿主

本节说明 TabHost Activity/Fragment 和 TabViewPagerAbility。

## 适用条件

- 页面由多个 Fragment Tab 组成。
- 使用 Material `TabLayout` 和 ViewPager2。
- 需要 Activity 级 Tab 或 Fragment 内嵌 Tab。

## 推荐做法

```kotlin
class MainActivity : TabHostActivity<ActivityMainBinding, MainModel>() {
    override fun onCreateTabs() = listOf(TabItem(fragmentClass = HomeFragment::class.java, title = "首页"))
}
```

## 可复制 Demo

```kotlin
import com.itg.itg_ui.tab.TabHostActivity
import com.itg.itg_ui.tab.TabItem

class MainTabsActivity : TabHostActivity<ActivityMainBinding, MainModel>() {
    override fun onCreateTabs(): List<TabItem> = listOf(
        TabItem(fragmentClass = HomeFragment::class.java, title = "首页"),
        TabItem(fragmentClass = ProfileFragment::class.java, title = "我的")
    )

    override fun onCreateTabConfig() = super.onCreateTabConfig().copy(
        defaultPosition = 0,
        swipeable = true,
        offscreenPageLimit = 1
    )
}
```

## 关键说明

- `TabItem` 持有 Fragment class、标题、图标、arguments、角标等信息。
- `TabConfig.autoTitle` 可控制标题是否自动同步到 Tab。
- `BaseTabFragment` 提供 Tab 可见状态，适合懒加载。
- Fragment 内嵌 Tab 使用 `TabHostFragment`。
- 角标和选中事件由 `TabViewPagerAbility` 统一处理。

## 验证方式

- 默认选中位置应符合 `defaultPosition`。
- 滑动关闭时 ViewPager2 不应响应横滑。
- Fragment 第一次选中时再触发懒加载逻辑。

[返回 README](../README.md)