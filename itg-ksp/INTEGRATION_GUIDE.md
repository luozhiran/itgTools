# itg-ksp 接入手册

这份文档只讲落地流程，不重复框架原理。目标是让新模块可以按步骤接入：

完整场景、runtime、生成规则和排错说明请从 [`README.md`](./README.md) 文档索引进入。

1. 加依赖
2. 写注解
3. 生成代码
4. 在 app 里跑起来

## 1. 添加依赖

业务模块同时依赖注解、运行时和编译器：

```kotlin
dependencies {
    implementation(project(":itg-ksp-annotations"))
    implementation(project(":itg-ksp-runtime"))
    ksp(project(":itg-ksp-compiler"))
}
```

如果你的项目还没启用 KSP，需要先按项目现有方式接好 `com.google.devtools.ksp` 插件。

## 2. Recycler 接入流程

### 2.1 定义 actions

先定义业务回调接口：

```kotlin
interface ProfileActions {
    fun onUserClick(id: Long)
    fun onBannerClick(id: Long)
}
```

### 2.2 定义 item

推荐优先用手写绑定处理复杂场景；简单字段展示可以用 `@ItgAutoTextField`。

Item 可以继续实现 `ItgListItem`；如果业务模型不希望增加 `stableId`，则不实现该接口，并在 Item 注解中设置已有非空唯一属性，例如 `itemKeyProperty = "id"`。

```kotlin
@ItgViewBindingItem(
    bindingClassName = "com.example.databinding.ItemProfileBinding",
    actionsClassName = "com.example.ProfileActions",
)
data class ProfileRow(
    override val stableId: Long,
    @ItgAutoTextField val title: String,
    @ItgAutoTextField(viewName = "detail") val description: String,
) : ItgListItem
```

如果要手写绑定：

```kotlin
@ItgViewBindingItem(
    bindingClassName = "com.example.databinding.ItemBannerBinding",
    actionsClassName = "com.example.ProfileActions",
)
data class BannerRow(
    override val stableId: Long,
    val text: String,
) : ItgListItem {
    @ItgBind
    fun bind(binding: ItemBannerBinding, actions: ProfileActions) {
        binding.text.text = text
    }
}
```

### 2.3 运行时接入

```kotlin
private val adapter = createProfileRecyclerAdapter(actions = this)

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    recyclerAbility.bind(binding.recyclerView, this, adapter)
    recyclerAbility.observeItems(viewModel.rows)
}
```

### 2.4 payload 规则

- 自动绑定：KSP 会按字段差异生成 payload
- 手写绑定：可用 `@ItgPayload`
- 如果 item 内容复杂但不想自己写 payload，可以继续只用 `@ItgBind`

## 3. Tab 接入流程

### 3.1 定义宿主

在 `TabHostActivity` 或 `TabHostFragment` 上加 `@ItgTabHost`：

```kotlin
@ItgTabHost(groupName = "ProfileTabs")
class ProfileTabsActivity : TabHostActivity<ActivityProfileTabsBinding, ProfileModel>() {
    override fun onCreateTabs(): List<TabItem<*>> = createProfileTabsTabItems()
    override fun onCreateTabConfig(): TabConfig = createProfileTabsTabConfig()
}
```

### 3.2 定义页面

`@ItgTabItem` 贴在继承链上的 Fragment 上即可，允许中间还有一层抽象基类。

```kotlin
open class BaseProfileTabFragment :
    BaseTabFragment<FragmentProfileTabBinding, ProfileModel>()

@ItgTabItem(groupName = "ProfileTabs", title = "首页", order = 0)
class ProfileHomeFragment : BaseProfileTabFragment()
```

### 3.3 宿主和页面的分组必须一致

`groupName` 决定生成函数的命名和收集范围。

- `@ItgTabHost(groupName = "ProfileTabs")`
- `@ItgTabItem(groupName = "ProfileTabs")`

两边必须相同。

## 4. 生成结果怎么用

### Recycler

生成的入口通常长这样：

```kotlin
val adapter = createKspDemoRecyclerAdapter(actions = this)
```

### Tab

生成的入口通常长这样：

```kotlin
override fun onCreateTabs(): List<TabItem<*>> = createKspDemoTabItems()
override fun onCreateTabConfig(): TabConfig = createKspDemoTabConfig()
```

## 5. 常见错误

### 5.1 KSP 不生成代码

先检查：

- item 是否实现了 `ItgListItem`
- 未实现 `ItgListItem` 时，是否设置了有效的 `itemKeyProperty`
- `@ItgViewBindingItem` / `@ItgDataBindingItem` 的参数是否完整
- `@ItgTabHost` / `@ItgTabItem` 是否在同一个 group
- Tab 页面是否至少间接继承 `Fragment`

### 5.2 字段自动绑定没有效果

检查：

- 目标字段是否真的声明在 item 的主构造参数里
- `viewName` 是否和 binding 里的实际 view 名称一致
- 该字段是否为 `@ItgAutoTextField`

### 5.3 编译期报 Fragment 校验失败

说明目标类没被识别成 Fragment 子类。当前处理器会递归检查继承链，正常情况下只要最终继承 `androidx.fragment.app.Fragment` 就可以。

## 6. 本仓库里的真实示例

- Recycler 手写：[`RecyclerDemoActivity`](../app/src/main/java/com/itg/itgtools/pages/itgui/RecyclerDemoActivity.kt)
- Recycler KSP：[`KspRecyclerDemoActivity`](../app/src/main/java/com/itg/itgtools/pages/itgui/ksp/KspRecyclerDemoActivity.kt)
- Tab 手写：[`BasicTabsActivity`](../app/src/main/java/com/itg/itgtools/pages/itgui/BasicTabsActivity.kt)
- Tab KSP：[`KspTabHostActivity`](../app/src/main/java/com/itg/itgtools/pages/itgui/ksp/KspTabHostActivity.kt)

## 7. 快速清单

如果你只想按勾选项推进，直接看：

- [`INTEGRATION_CHECKLIST.md`](./INTEGRATION_CHECKLIST.md)
