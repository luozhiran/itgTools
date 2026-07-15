# 02. Tab 注解

本节说明 TabHost 和 TabItem 自动生成注解。

## 可复制 Demo

```kotlin
import com.itg.itg_ksp.annotations.ItgTabHost
import com.itg.itg_ksp.annotations.ItgTabItem

@ItgTabHost(
    groupName = "main",
    defaultPosition = 0,
    swipeable = true,
    autoTitle = true
)
class MainTabSpec

@ItgTabItem(
    groupName = "main",
    title = "首页",
    order = 0
)
class HomeFragment : Fragment()

@ItgTabItem(
    groupName = "main",
    title = "我的",
    order = 1
)
class ProfileFragment : Fragment()
```

## 关键说明

- `groupName` 是 TabHost 和 TabItem 的聚合键，必须一致。
- `order` 控制 Tab 顺序。
- `titleResExpression`、`iconResExpression`、`argumentsExpression` 允许写生成代码可引用的表达式。
- `badgeCount` 和 `badgeDot` 可生成初始角标。

## 验证方式

- groupName 不一致时 TabItem 不会进入目标宿主。
- 生成宿主类中的 Tab 顺序应按 `order` 排列。

[返回 README](../README.md)