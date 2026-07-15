# ITG KSP Annotations

`itg-ksp-annotations` 是 JVM 注解模块，定义 Recycler 和 Tab 自动生成所需的注解。模块使用 Java/Kotlin JVM，目标 Java 17。

## 使用场景总览

| 使用场景 | 推荐注解 | 适用条件/支持范围 | 什么情况下使用 | 为什么可以用 |
| --- | --- | --- | --- | --- |
| [生成 ViewBinding Recycler renderer](./docs/01-annotations.md) | `@ItgViewBindingItem`、`@ItgBind` | item 类 + ViewBinding | 不想手写 renderer 注册 | compiler 根据 item、binding、actions 生成注册代码 |
| [生成 DataBinding Recycler renderer](./docs/01-annotations.md) | `@ItgDataBindingItem` | DataBinding layout/BR 表达式 | XML DataBinding 列表项 | 注解提供 layout 和 variable 表达式 |
| [生成 payload 差异](./docs/01-annotations.md) | `@ItgPayload`、`@ItgAutoTextField`、`@ItgContentsSame` | Recycler diff/payload | 只刷新变化字段 | processor 读取注解生成 payload 逻辑 |
| [生成 TabHost 和 TabItem](./docs/02-tabs.md) | `@ItgTabHost`、`@ItgTabItem` | Tab 页面 Fragment | Tab 列表固定且适合编译期生成 | processor 按 groupName 聚合 Tab 配置 |

## 文档目录

| 文档 | 内容 |
| --- | --- |
| [01. Recycler 注解](./docs/01-annotations.md) | ViewBinding/DataBinding item、bind、payload |
| [02. Tab 注解](./docs/02-tabs.md) | TabHost、TabItem、groupName、排序 |

## 依赖

```kotlin
dependencies {
    implementation(project(":itg-ksp-annotations"))
}
```