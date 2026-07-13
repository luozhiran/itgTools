# ITG UI — Android UI 组件库

提供 RecyclerView 通用适配器和 Tab 导航组件。

## 组件

### RecyclerView (recycler 包)

基于 `ListAdapter` + `DiffUtil` 的通用 RecyclerView 适配方案，支持 ViewBinding/DataBinding。

| 类 | 说明 |
|------|------|
| `ItgRecyclerAdapter` | 通用 RecyclerView Adapter |
| `ItemRenderer` | 单类型 Item 渲染器 |
| `ItemRendererRegistry` | 多类型 Item 渲染注册表 |
| `RendererDsl` | DSL 构建器（`viewBinding {}`） |
| `RecyclerController` | RecyclerView 控制器 |
| `RecyclerViewAbility` | RecyclerView 能力封装 |
| `ItgListItem` | 列表项接口 |
| `RecyclerConfig` | 配置类 |

```kotlin
// 快速使用
registry.viewBinding<MyItem, ItemBinding, MyActions>(
    inflate = ItemBinding::inflate,
    bind = { item, actions -> /* 绑定数据 */ }
)
```

### Tab (tab 包)

基于 ViewPager2 + TabLayout 的 Tab 导航方案，支持自定义样式和角标。

| 类 | 说明 |
|------|------|
| `TabHostActivity` | Tab 宿主 Activity |
| `TabHostFragment` | Tab 宿主 Fragment（内嵌二级 Tab） |
| `BaseTabFragment` | Tab 页面基类 |
| `GenericTabAdapter` | Tab FragmentStateAdapter |
| `TabViewPagerAbility` | Tab 切换/角标能力 |
| `TabConfig` / `TabItem` | 配置/数据类 |
| `TabBadge` | 角标 |
| `TabStyle` / `TabItemStyle` / `TabIndicatorStyle` / `TabTextStyle` | 样式配置 |
| `CustomTabViewProvider` | 自定义 Tab View |

```kotlin
// 使用 TabHostActivity
class MainActivity : TabHostActivity<ActivityMainBinding, MainViewModel>() {
    override fun onCreateTabs() = listOf(
        TabItem(HomeFragment::class, "首页", R.drawable.ic_home),
        TabItem(ProfileFragment::class, "我的", R.drawable.ic_profile)
    )
}
```

## 依赖

- `itg-base` — 基础架构
- `androidx.viewpager2` — ViewPager2
- `androidx.recyclerview` — RecyclerView
- `androidx.fragment` — Fragment
- DataBinding + ViewBinding
