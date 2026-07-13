# ITG KSP Compiler — KSP 编译器插件

基于 Google KSP (Kotlin Symbol Processing) 的注解处理器，为 `itg-ui` 自动生成模板代码。

## 处理器

| 类 | 说明 |
|------|------|
| `ItgRecyclerProcessor` | 处理 `@ItgBind`/`@ItgTabHost` 等注解，生成 ItemRenderer 注册代码 |
| `ItgRecyclerProcessorProvider` | KSP Processor Provider |

## 功能

- `@ItgBind` → 自动生成 RecyclerView ItemRenderer 绑定代码
- `@ItgTabHost` → 自动生成 Tab 配置代码
- `@ItgDataBindingItem` / `@ItgViewBindingItem` → 生成 ViewBinding/DataBinding 适配代码

## 依赖

- `itg-ksp-annotations` — 注解定义
- `com.google.devtools.ksp:symbol-processing-api` — KSP API

> 注意：当前分支 KSP Gradle 插件暂未启用 (`ksp-classpath` 被注释)。此模块可正常编译，但不会在构建过程中执行注解处理。
