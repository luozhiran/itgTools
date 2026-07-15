# 02. API 速查

本节汇总 runtime 的公开 API。

## API 速查

| API | 说明 |
| --- | --- |
| `GeneratedRecyclerRegistry<A>` | 生成 Registry 需要实现的接口 |
| `GeneratedRecyclerRegistry.register(builder)` | 将生成的 renderer 注册到 builder |
| `itgGeneratedRecyclerAdapter(registry, actions)` | 从 Registry 和 actions 创建 Adapter |
| `GeneratedRecyclerRegistry<A>.adapter(actions)` | 扩展函数，语法更短 |

## 可复制 Demo

```kotlin
val adapter = itgGeneratedRecyclerAdapter(GeneratedUserRegistry(), actions)
```

## 关键说明

- runtime 不扫描注解，不参与编译期处理。
- 如果找不到生成类，问题通常在 KSP 配置、注解参数或编译任务。
- actions 类型必须和生成 Registry 的泛型一致。

## 验证方式

- 清理并重新编译后生成类仍能稳定出现。
- 修改注解 item 后，生成 Registry 的注册内容应同步变化。

[返回 README](../README.md)