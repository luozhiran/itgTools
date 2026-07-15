# 01. 接入与生成流程

本节说明如何接入 compiler 并生成 Recycler 代码。

## 适用条件

- 模块已应用 KSP 插件。
- 业务代码依赖 annotations 和 runtime。
- item 注解参数能被编译期解析。

## 可复制 Demo

```kotlin
plugins {
    id("com.google.devtools.ksp")
}

dependencies {
    implementation(project(":itg-ksp-annotations"))
    implementation(project(":itg-ksp-runtime"))
    ksp(project(":itg-ksp-compiler"))
}
```

```kotlin
@ItgViewBindingItem(
    bindingClassName = "com.example.databinding.ItemUserBinding",
    actionsClassName = "com.example.UserActions",
    itemKeyProperty = "id"
)
data class UserItem(val id: Long, val name: String)
```

## 关键说明

- compiler 自身不应作为 runtime 依赖打进 APK。
- `bindingClassName/actionsClassName` 要写完整类名。
- 同一 actions 类型下的 item 会聚合生成一组 Registry/Adapter。
- 生成文件位置由 KSP 管理，通常在 build/generated/ksp 下。

## 验证方式

- 执行模块 Kotlin 编译任务后，应能 import 生成类。
- 修改 item 注解后重新编译，生成类应更新。

[返回 README](../README.md)