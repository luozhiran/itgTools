# ITG UI

`itg-ui` 是 Android UI 组件模块，提供通用 RecyclerView 渲染体系和基于 ViewPager2 + TabLayout 的 Tab 宿主能力。模块 minSdk 21，启用 ViewBinding/DataBinding，依赖 `itg-base`、Material、ViewPager2 和 RecyclerView。

## 使用场景总览

| 使用场景 | 推荐 API | 适用条件/支持范围 | 什么情况下使用 | 为什么可以用 |
| --- | --- | --- | --- | --- |
| [搭建多类型 RecyclerView](./docs/01-recycler.md) | `itgRecyclerAdapter`、`ItemRendererRegistryBuilder` | RecyclerView + ViewBinding/DataBinding | 列表有多种 item 类型 | 每种类型注册一个 renderer，Adapter 根据 item class 分发 |
| [控制列表提交和滚动](./docs/01-recycler.md) | `RecyclerController`、`RecyclerViewAbility` | Activity/Fragment 页面 | 需要统一 submitList、clear、scrollToPosition | Controller 包装 Adapter 和 RecyclerView 常用操作 |
| [搭建 Tab Activity/Fragment](./docs/02-tabs.md) | `TabHostActivity`、`TabHostFragment` | ViewPager2 + TabLayout | 首页、频道页、二级 Tab | 宿主类封装 TabLayoutMediator 与 FragmentStateAdapter |
| [配置 Tab 样式和角标](./docs/03-style-ksp.md) | `TabConfig`、`TabStyle`、`TabBadge` | Material TabLayout | 需要固定/滚动 Tab、角标、指示器样式 | 样式类集中描述文字、padding、indicator 和自定义 view |
| [配合 KSP 自动生成列表/Tab](./docs/03-style-ksp.md) | `itg-ksp-*` 注解和 runtime | 已接入 KSP | 不想手写 renderer 注册或 Tab 列表 | KSP 编译期生成 Registry/Adapter/TabHost 代码 |
| [查看 API 速查](./docs/04-api-reference.md) | API 表 | 所有使用者 | 查组件类和职责 | 汇总当前源码公开类型 |

## 文档目录

| 文档 | 内容 |
| --- | --- |
| [01. RecyclerView](./docs/01-recycler.md) | 多类型列表、renderer、controller、ability |
| [02. Tab 宿主](./docs/02-tabs.md) | Activity/Fragment Tab、ViewPager2、懒加载 |
| [03. 样式与 KSP](./docs/03-style-ksp.md) | Tab 样式、角标、KSP 集成 |
| [04. API 速查](./docs/04-api-reference.md) | Recycler 和 Tab API 汇总 |

## 依赖

```kotlin
dependencies {
    implementation(project(":itg-ui"))
}
```