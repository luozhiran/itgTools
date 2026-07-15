# ITG KSP Runtime

`itg-ksp-runtime` 是 KSP 生成代码的 Android 运行时桥接模块，提供 `GeneratedRecyclerRegistry` 和把生成 Registry 转成 `ItgRecyclerAdapter` 的辅助函数。模块 minSdk 21，`api(project(":itg-ui"))`。

## 使用场景总览

| 使用场景 | 推荐 API | 适用条件/支持范围 | 什么情况下使用 | 为什么可以用 |
| --- | --- | --- | --- | --- |
| [使用生成的 Recycler Registry](./docs/01-runtime.md) | `GeneratedRecyclerRegistry.register` | KSP 已生成 Registry 类 | 编译期生成了 renderer 注册逻辑 | runtime 提供统一接口让生成代码注册到 builder |
| [从生成 Registry 创建 Adapter](./docs/01-runtime.md) | `itgGeneratedRecyclerAdapter`、`registry.adapter(actions)` | 依赖 `itg-ui` | 页面要直接拿 Adapter | helper 内部创建 builder、调用 register、再构建 Adapter |
| [查看接入边界](./docs/02-api-reference.md) | API 表 | KSP 生成代码使用者 | 查 runtime 和 UI 的关系 | runtime 只做桥接，不做注解处理 |

## 文档目录

| 文档 | 内容 |
| --- | --- |
| [01. Runtime 使用](./docs/01-runtime.md) | 生成 Registry 到 Adapter 的流程 |
| [02. API 速查](./docs/02-api-reference.md) | runtime API 和边界 |

## 依赖

```kotlin
dependencies {
    implementation(project(":itg-ksp-runtime"))
}
```