# 05 Recycler Diff 与组合场景

## 1. 默认 Diff 行为

Item 身份由以下两项共同决定：

```text
运行时 class + item key
```

旧 `ItgListItem` 的 key 是 `stableId`；普通业务模型的 key 来自 `itemKeyProperty`。同类型 Item 的 key 必须唯一。旧 `ItgListItem` 继续执行 stableId 全列表唯一校验，以保持原有契约。

业务 key 不要求是 Long。Adapter 会按“类型 + key”分配内部 Long ID，并在同一个 Adapter 生命周期内持续复用。

## 2. 自定义内容比较

```kotlin
@ItgContentsSame
fun contentsSame(oldItem: UserRow): Boolean =
    oldItem.name == name && oldItem.avatar == avatar
```

该函数应无副作用，接收旧对象，由当前新对象调用。返回 `true` 表示无需重新绑定。

## 3. 单一 payload

```kotlin
@ItgPayload
fun payload(oldItem: UserRow): Any? = when {
    oldItem.name != name -> "name"
    oldItem.avatar != avatar -> "avatar"
    else -> null
}
```

多个字段同时变化时，上述写法只返回一个键。

## 4. 多字段 payload

```kotlin
@ItgPayload
fun payload(oldItem: UserRow): Any? = buildSet {
    if (oldItem.name != name) add("name")
    if (oldItem.avatar != avatar) add("avatar")
}.takeIf { it.isNotEmpty() }
```

RecyclerView 传给 bind 的 `payloads` 是外层列表。单次返回的 Set 会作为其中一个元素：

```kotlin
val changed = payloads.filterIsInstance<Set<*>>().flatten().toSet()
```

也可以让 `@ItgPayload` 返回一个业务 data class，以获得类型安全。

## 5. 自动字段 payload

每个 `@ItgAutoTextField` 都会比较旧值与新值。默认 payload key 是属性名，可覆盖：

```kotlin
@ItgAutoTextField(
    viewName = "displayName",
    payloadKey = "profile-name",
)
val name: String
```

生成结果返回 `List<String>?`。若同时声明手写 `@ItgPayload`，处理器优先使用手写函数。

## 6. 混合 Item 列表

相同 `actionsClassName` 的所有 ViewBinding/DataBinding Item 会聚合进同一个 registry，因此可以提交混合列表：

```kotlin
listOf(
    HeaderRow(1, "标题"),
    UserRow(2, "Alice"),
    ArticleRow(3, "正文"),
)
```

每个运行时 class 只能注册一次。提交未注册类型会抛出“未注册 ItemRenderer”。

## 7. 一个模块中的多个 Recycler

registry 按 actions 完整类名分组。要生成两个互不影响的 adapter，定义两个 actions 类型：

```kotlin
interface FeedActions
interface SearchActions
```

分别生成 `GeneratedFeedRegistry/createFeedRecyclerAdapter` 和 `GeneratedSearchRegistry/createSearchRecyclerAdapter`。

如果两个页面共用同一个 actions 类型，它们的 Item 会被合并到同一个 registry。这通常可以工作，但会增加无关 renderer；需要严格隔离时拆分 actions 类型。

## 8. Activity、Fragment 和嵌套 Recycler

- Activity：LifecycleOwner 传 `this`。
- Fragment：传 `viewLifecycleOwner`。
- 同页面多个 Recycler：每个 Recycler 使用独立 `RecyclerViewAbility` 实例。
- 嵌套 Recycler：子列表独立创建 adapter/ability；不要复用父 Recycler 的 ability。

## 9. 列表更新要求

使用不可变新对象和新列表：

```kotlin
rows.value = rows.value.orEmpty().map { row ->
    if (row is UserRow && row.stableId == id) row.copy(name = newName) else row
}
```

原地修改同一对象会让 DiffUtil 无法可靠识别旧值与新值。
