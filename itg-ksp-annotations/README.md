# ITG KSP Annotations — 编译时注解定义

为 KSP 编译器插件提供注解定义。纯 JVM 模块，无 Android 依赖。

## 注解列表

| 注解 | 目标 | 说明 |
|------|------|------|
| `@ItgBind` | FUNCTION | 标记绑定方法 |
| `@ItgContentsSame` | ANNOTATION_CLASS | 标记内容比较器 |
| `@ItgDataBindingItem` | CLASS | 标记 DataBinding 列表项 |
| `@ItgViewBindingItem` | CLASS | 标记 ViewBinding 列表项 |
| `@ItgTabHost` | CLASS | 自动生成 TabHost 配置 |
| `@ItgTabItem` | CLASS | 标记 Tab 页面 |
| `@ItgPayload` | ANNOTATION_CLASS | 标记 Payload 类型 |
| `@ItgAutoTextField` | FIELD | 自动生成 TextField |

## 使用

```kotlin
@ItgTabHost(groupName = "main", defaultPosition = 0)
class MainActivity { ... }

@ItgTabItem
class HomeFragment { ... }
```

> 注意：KSP 编译器插件 (`itg-ksp-compiler`) 当前未在 Gradle 中启用，注解定义保留供后续使用。
