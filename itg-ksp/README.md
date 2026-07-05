# itg-ksp

`itg-ksp` 是 `itg-ui` 的编译期增强层，目标是把 Recycler 注册、字段绑定、payload 计算，以及 Tab 列表生成前移到编译期，减少业务层样板代码。

## 三层结构

### 1. itg-ksp-annotations

只放注解定义，业务代码通过这些注解声明生成规则。

- `@ItgViewBindingItem`
- `@ItgDataBindingItem`
- `@ItgBind`
- `@ItgPayload`
- `@ItgContentsSame`
- `@ItgAutoTextField`
- `@ItgTabHost`
- `@ItgTabItem`

### 2. itg-ksp-runtime

提供生成代码会直接调用的运行时桥接。

- `GeneratedRecyclerRegistry`
- `itgGeneratedRecyclerAdapter(...)`
- `GeneratedRecyclerRegistry<A>.adapter(...)`

### 3. itg-ksp-compiler

KSP 处理器，扫描注解并生成：

- `GeneratedXXXRegistry`
- `createXXXRecyclerAdapter(...)`
- `createXXXTabItems()`
- `createXXXTabConfig()`

生成代码复用 `itg-ui` 现有的 `ItemRendererRegistryBuilder`、`viewBinding`、`dataBinding` 和 `TabItem`。

## Recycler 注解

### 手写绑定

适合复杂 Item，业务逻辑全写在 item 自己身上。

```kotlin
@ItgViewBindingItem(
    bindingClassName = "com.example.databinding.ItemUserBinding",
    actionsClassName = "com.example.UserActions",
)
data class UserRow(
    override val stableId: Long,
    val name: String,
) : ItgListItem {

    @ItgBind
    fun bind(binding: ItemUserBinding, actions: UserActions, payloads: List<Any>) {
        binding.name.text = name
    }

    @ItgPayload
    fun payload(oldItem: UserRow): Any? = if (oldItem.name != name) "name" else null
}
```

### 字段级自动绑定

适合简单 Item。只需要声明字段和目标 View 名称，KSP 会生成：

- View 更新代码
- payload 差异判断
- 对应的 registry 注册

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

上面的写法会生成类似逻辑：

- `titleTextView.text = item.title.toString()`
- `detail.text = item.description.toString()`
- 字段变化时自动生成 payload 列表

### DataBinding

```kotlin
@ItgDataBindingItem(
    layoutExpression = "com.example.R.layout.item_user",
    itemVariableExpression = "com.example.BR.item",
    actionsVariableExpression = "com.example.BR.actions",
    actionsClassName = "com.example.UserActions",
)
data class UserDataRow(
    override val stableId: Long,
    val title: String,
) : ItgListItem
```

## Tab 注解

### Host

把 `TabHostActivity` 或 `TabHostFragment` 当成生成入口。

```kotlin
@ItgTabHost(groupName = "MainTabs")
class MainTabsActivity : TabHostActivity<ActivityMainTabsBinding, MainModel>() {
    override fun onCreateTabs() = createMainTabsTabItems()
    override fun onCreateTabConfig() = createMainTabsTabConfig()
}
```

### Item

`@ItgTabItem` 标记的 Fragment 会被收集到同组 Tab 列表里。

```kotlin
@ItgTabItem(groupName = "MainTabs", title = "首页", order = 0)
class HomeFragment : BaseTabFragment<FragmentHomeBinding, HomeModel>()
```

生成结果：

- `createMainTabsTabItems(): List<TabItem<*>>`
- `createMainTabsTabConfig(): TabConfig`

## 接入方式

```kotlin
dependencies {
    implementation(project(":itg-ksp-annotations"))
    implementation(project(":itg-ksp-runtime"))
    ksp(project(":itg-ksp-compiler"))
}
```

## 业务侧最小使用方式

Recycler：

```kotlin
val adapter = createKspDemoRecyclerAdapter(actions = this)
```

Tab：

```kotlin
override fun onCreateTabs(): List<TabItem<*>> = createKspDemoTabItems()
override fun onCreateTabConfig(): TabConfig = createKspDemoTabConfig()
```

## 可运行 Demo

App 模块里已经放了可直接运行的例子：

- [`KspRecyclerDemoActivity`](../app/src/main/java/com/itg/itgtools/pages/itgui/ksp/KspRecyclerDemoActivity.kt)
- [`KspTabHostActivity`](../app/src/main/java/com/itg/itgtools/pages/itgui/ksp/KspTabHostActivity.kt)

Recycler Demo 覆盖：

- ViewBinding 手写绑定
- ViewBinding 字段自动绑定
- DataBinding 自动注册
- payload / diff
- LiveData 自动提交

Tab Demo 覆盖：

- `@ItgTabHost`
- `@ItgTabItem`
- 继承 `BaseTabFragment` 的间接 Fragment
- 生成式 Tab 列表与配置

## 接入手册

如果要把这套方案接到新模块，直接看：

- [`INTEGRATION_GUIDE.md`](./INTEGRATION_GUIDE.md)
- [`INTEGRATION_CHECKLIST.md`](./INTEGRATION_CHECKLIST.md)
