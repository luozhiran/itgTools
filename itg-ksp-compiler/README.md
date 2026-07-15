# ITG KSP Compiler

`itg-ksp-compiler` 是 KSP 编译器模块，读取 `itg-ksp-annotations` 中的 Recycler/Tab 注解，在编译期生成 `itg-ui` 可使用的 Registry、Adapter 或 TabHost 相关代码。模块是 JVM/KSP processor，目标 Java 17。

## 使用场景总览

| 使用场景 | 推荐配置/能力 | 适用条件/支持范围 | 什么情况下使用 | 为什么可以用 |
| --- | --- | --- | --- | --- |
| [接入 KSP processor](./docs/01-setup-generation.md) | `ksp(project(":itg-ksp-compiler"))` | Android app/library + KSP 插件 | 需要编译期生成列表或 Tab 代码 | processor 通过 KSP 读取注解并写入生成源文件 |
| [生成 Recycler 注册代码](./docs/01-setup-generation.md) | `@ItgViewBindingItem` / `@ItgDataBindingItem` | item 类注解完整 | 多类型列表不想手写 renderer | compiler 聚合同一 actions 类型生成 Registry/Adapter |
| [生成 TabHost 代码](./docs/02-rules-troubleshooting.md) | `@ItgTabHost` / `@ItgTabItem` | groupName 一致 | Tab 页面固定且适合静态声明 | compiler 按 groupName 聚合 TabItem 并生成宿主配置 |
| [排查生成失败](./docs/02-rules-troubleshooting.md) | 编译错误和生成目录 | KSP 编译阶段 | 找不到生成类或注解参数错误 | KSP 会在编译期暴露符号解析问题 |

## 文档目录

| 文档 | 内容 |
| --- | --- |
| [01. 接入与生成流程](./docs/01-setup-generation.md) | Gradle 配置、Recycler 生成流程 |
| [02. 规则与排查](./docs/02-rules-troubleshooting.md) | Tab 生成、注解规则、常见问题 |

## 依赖

```kotlin
dependencies {
    ksp(project(":itg-ksp-compiler"))
    implementation(project(":itg-ksp-annotations"))
    implementation(project(":itg-ksp-runtime"))
}
```