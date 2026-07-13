# ITG KSP Runtime — 编译时代码生成运行时

KSP 编译器生成的代码所依赖的运行时类。

## 内容

| 类 | 说明 |
|------|------|
| `GeneratedRecyclerRegistry` | KSP 自动生成的 ItemRenderer 注册表基类 |

## 依赖

- `itg-ui` — RecyclerView/Tab UI 组件

> 注意：需要配合 `itg-ksp-compiler` (KSP 编译器插件) 使用。当前 KSP Gradle 插件未启用。
