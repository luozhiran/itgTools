# 02. 规则与排查

本节说明生成规则和常见问题。

## 适用条件

- 编译期找不到生成类。
- 注解参数写错或 groupName 不匹配。
- 需要确认 Tab 生成规则。

## 可复制 Demo

```kotlin
@ItgTabHost(groupName = "main", defaultPosition = 0)
class MainTabSpec

@ItgTabItem(groupName = "main", title = "首页", order = 0)
class HomeFragment : Fragment()
```

## 关键说明

- `@ItgTabHost.groupName` 与 `@ItgTabItem.groupName` 必须一致。
- `order` 控制生成 Tab 顺序。
- payload 和 contentsSame 函数签名要符合 processor 识别规则。
- 如果 binding/actions 类型字符串拼错，通常会在生成代码编译阶段失败。
- 清理 build 后重新编译可排除旧生成代码干扰。

## 排查路径

| 现象 | 检查项 |
| --- | --- |
| 找不到生成类 | KSP 插件是否启用、compiler 是否放在 `ksp(...)` |
| 生成类编译失败 | 注解里的类名、BR 表达式、layout 表达式是否正确 |
| TabItem 未出现 | groupName 是否一致，Fragment 类型是否可见 |
| payload 不生效 | `@ItgPayload` 或 `@ItgAutoTextField` 是否标在正确成员上 |

## 验证方式

- 使用 `./gradlew :app:compileDebugKotlin` 或对应模块编译任务验证。
- 生成类不稳定时先删除 build 目录再重编译。

[返回 README](../README.md)