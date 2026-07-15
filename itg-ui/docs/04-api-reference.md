# 04. API 速查

本节汇总 `itg-ui` 的核心类型。

## Recycler API

| 类型 | 说明 |
| --- | --- |
| `ItgRecyclerAdapter<A>` | 通用 ListAdapter |
| `ItemRenderer<I, VB, A>` | 单类型渲染器 |
| `ItemRendererRegistry<A>` | renderer 注册表 |
| `ItemRendererRegistryBuilder<A>` | DSL 注册构建器 |
| `RecyclerController<A>` | submitList、clear、scrollToPosition |
| `RecyclerViewAbility` | 绑定 RecyclerView 和 LiveData |
| `RecyclerConfig` | LayoutManager、Animator、缓存等配置 |
| `ItgListItem` | 可提供 stableId 的 item 接口 |

## Tab API

| 类型 | 说明 |
| --- | --- |
| `TabHostActivity` | Activity Tab 宿主 |
| `TabHostFragment` | Fragment Tab 宿主 |
| `BaseTabFragment` | Tab 页面基类和可见状态 |
| `TabViewPagerAbility` | ViewPager2 + TabLayout 绑定能力 |
| `GenericTabAdapter` | FragmentStateAdapter |
| `TabConfig` | Tab 模式、默认位置、懒加载、样式 |
| `TabItem` | 单个 Tab 页定义 |
| `TabBadge` | 角标定义 |
| `TabStyle` 系列 | 文字、padding、indicator、自定义 view 样式 |

## 可复制 Demo

```kotlin
// Recycler 和 Tab 都是运行时能力；复杂样板代码可配合 itg-ksp 自动生成。
```

## 关键说明

- Recycler 的关键是 item 类型到 renderer 的一一映射。
- Tab 的关键是 `TabItem` 列表和宿主生命周期。
- `itg-ui` 暴露 `api(project(":itg-base"))`，页面基类能力可直接复用。

## 验证方式

- Recycler 列表刷新时无重复 viewType 冲突。
- Tab 页面销毁重建后 Fragment 状态符合 ViewPager2 预期。

[返回 README](../README.md)