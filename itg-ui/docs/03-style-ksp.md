# 03. 样式与 KSP

本节说明 Tab 样式、角标以及与 KSP 自动生成能力的关系。

## 适用条件

- TabLayout 默认样式不满足设计。
- 需要文字、padding、indicator、角标或自定义 Tab View。
- 项目已接入 `itg-ksp-annotations/runtime/compiler`。

## 推荐做法

```kotlin
TabConfig(style = TabStyle(...))
```

## 可复制 Demo

```kotlin
import com.itg.itg_ui.tab.TabBadge
import com.itg.itg_ui.tab.TabConfig
import com.itg.itg_ui.tab.style.TabItemStyle
import com.itg.itg_ui.tab.style.TabStyle

val config = TabConfig(
    defaultPosition = 0,
    style = TabStyle(
        itemStyle = TabItemStyle(
            normalTextColor = 0xFF666666.toInt(),
            selectedTextColor = 0xFF111111.toInt()
        )
    )
)

val tab = TabItem(
    fragmentClass = MessageFragment::class.java,
    title = "消息",
    badge = TabBadge(count = 8)
)
```

## KSP 关系

- `itg-ui` 提供运行时 UI 能力。
- `itg-ksp-annotations` 提供注解声明。
- `itg-ksp-compiler` 在编译期生成 Registry 或 TabHost 代码。
- `itg-ksp-runtime` 提供把生成 Registry 转为 Adapter 的运行时辅助函数。

## 关键说明

- 样式配置集中放在 `TabConfig.style`，避免分散修改 View。
- 自定义 Tab View 通过 `CustomTabViewProvider` 实现。
- KSP 不是 `itg-ui` 的必需依赖；手写注册也可以正常使用。

## 验证方式

- 切换 Tab 时选中/未选中文字颜色应正确变化。
- KSP 生成代码后，编译产物中应出现对应 Registry/Adapter/TabHost 类。

[返回 README](../README.md)