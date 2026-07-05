# 07 生成规则与 API 速查

## 1. Recycler 命名

registry 按 `actionsClassName` 聚合。设 actions 简单类名为 `ProfileActions`：

```text
baseName                    Profile
registry                    GeneratedProfileRegistry
adapter 工厂                createProfileRecyclerAdapter(actions)
生成 package                actions 所在 package
```

如果类名不以 `Actions` 结尾，例如 `FeedCallbacks`，baseName 就是完整简单类名 `FeedCallbacks`。

如果类名正好是 `Actions`，移除后为空，处理器回退为 `Actions`。

## 2. Tab 命名

设 `groupName = "MainTabs"`：

```text
文件                         GeneratedMainTabsTab.kt
列表函数                     createMainTabsTabItems()
配置函数                     createMainTabsTabConfig()
生成 package                 Host 所在 package
```

## 3. 注解速查

| 注解 | 目标 | 作用 |
|---|---|---|
| `@ItgViewBindingItem` | class | 注册 ViewBinding Item |
| `@ItgDataBindingItem` | class | 注册 DataBinding Item |
| `@ItgBind` | function | 声明 ViewBinding 完整/局部绑定函数 |
| `@ItgPayload` | function | 自定义 Diff payload |
| `@ItgContentsSame` | function | 自定义内容相等判断 |
| `@ItgAutoTextField` | property | 生成 TextView 赋值和字段 payload |
| `@ItgTabHost` | class | 生成某组 TabConfig 和 TabItems 入口 |
| `@ItgTabItem` | Fragment class | 向某组添加页面 |

所有注解都是 `SOURCE` retention，不应在运行时通过反射查找。

## 4. runtime 速查

| API | 使用场景 |
|---|---|
| `GeneratedRecyclerRegistry<A>` | 生成或手写 renderer 注册表 |
| `itgGeneratedRecyclerAdapter(actions, registry)` | 显式传 registry 创建 adapter |
| `registry.adapter(actions)` | 扩展函数形式创建 adapter |
| `createXxxRecyclerAdapter(actions)` | 生成的推荐快捷入口 |

## 5. 生成器校验

编译器会校验：

- Recycler Item 是普通 class。
- Recycler Item 实现 `ItgListItem`。
- ViewBinding Item 至少有 `@ItgBind` 或自动字段。
- `@ItgBind` 参数数量为 2 或 3，前两项看起来匹配 binding/actions。
- Tab Item 是 Fragment 子类，支持递归继承检查。

当前不会完整校验字符串表达式、函数返回类型、第三个 bind 参数类型、BR id 或 groupName 是否是合法标识符；这些错误会在生成代码编译或运行时暴露。

## 6. 增量生成

Recycler registry 是聚合输出：同一 actions 下任一 Item 变化都可能重建 registry。Tab 也是按 Host 与同组 Item 聚合。不要编辑 `build/generated/ksp` 下的文件，下一次构建会覆盖。

