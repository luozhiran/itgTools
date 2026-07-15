# 01. Runtime 使用

本节说明如何使用 KSP 生成的 Recycler Registry。

## 适用条件

- 项目已接入 `itg-ksp-annotations` 和 `itg-ksp-compiler`。
- 编译后已有生成的 Registry 类。
- 页面需要创建 `ItgRecyclerAdapter`。

## 可复制 Demo

```kotlin
import com.itg.itg_ksp.runtime.adapter

val actions = UserActions(onClick = { item -> println(item.id) })
val adapter = GeneratedUserRegistry().adapter(actions)
recyclerView.adapter = adapter
```

## 关键说明

- `GeneratedRecyclerRegistry<A>` 只有一个 `register(builder)` 方法。
- `adapter(actions)` 是扩展函数，会返回 `ItgRecyclerAdapter<A>`。
- runtime 依赖 `itg-ui`，因此页面可直接使用 UI 模块的 Adapter。
- Registry 类名由 compiler 根据 actions 类型生成，以实际编译产物为准。

## 验证方式

- IDE 能解析生成的 Registry 类。
- Adapter 设置到 RecyclerView 后，多类型 item 能正确渲染。

[返回 README](../README.md)