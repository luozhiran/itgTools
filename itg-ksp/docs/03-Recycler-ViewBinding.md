# 03 Recycler ViewBinding

## 1. 公共前提

定义 actions：

```kotlin
interface FeedActions {
    fun onUserClick(id: Long)
}
```

每个 Item 必须实现 `ItgListItem`，且同一列表中的 `stableId` 全局唯一：

```kotlin
data class UserRow(
    override val stableId: Long,
    val name: String,
) : ItgListItem
```

`bindingClassName` 和 `actionsClassName` 都是完整限定类名字符串。

## 2. 复杂 Item：完整绑定

```kotlin
@ItgViewBindingItem(
    bindingClassName = "com.example.databinding.ItemUserBinding",
    actionsClassName = "com.example.feed.FeedActions",
)
data class UserRow(
    override val stableId: Long,
    val name: String,
) : ItgListItem {
    @ItgBind
    fun bind(binding: ItemUserBinding, actions: FeedActions) {
        binding.name.text = name
        binding.root.setOnClickListener { actions.onUserClick(stableId) }
    }
}
```

两参数 `@ItgBind` 适合每次都执行完整绑定。参数顺序必须是 binding、actions。

## 3. 复杂 Item：接收 payload

```kotlin
@ItgBind
fun bind(
    binding: ItemUserBinding,
    actions: FeedActions,
    payloads: List<Any>,
) {
    if (payloads.contains("name")) {
        binding.name.text = name
        return
    }
    binding.name.text = name
    binding.root.setOnClickListener { actions.onUserClick(stableId) }
}
```

三参数签名会生成 `viewBindingWithPayloads` 注册。当前处理器只校验参数数量以及前两个参数类型；第三个参数应写成 `List<Any>`。

## 4. 简单文字字段：零 bind 自动绑定

```kotlin
@ItgViewBindingItem(
    bindingClassName = "com.example.databinding.ItemSummaryBinding",
    actionsClassName = "com.example.feed.FeedActions",
)
data class SummaryRow(
    override val stableId: Long,
    @ItgAutoTextField val title: String,
    @ItgAutoTextField(viewName = "description") val detail: String?,
) : ItgListItem
```

生成逻辑等价于：

```kotlin
title.text = item.title.toString()
description.text = item.detail.toString()
```

规则：

- `viewName` 为空时使用属性名。
- 目标 binding 成员必须有可写 `text` 属性，通常是 TextView。
- 值统一调用 `toString()`；可空值会显示字符串 `"null"`，需要空串等特殊格式时使用 `@ItgBind`。
- 自动字段会自动生成字段差异 payload。

## 5. 自动字段与手写 bind 混用

可以同时使用。处理器会生成自动 payload，并调用手写 bind：

```kotlin
data class MixedRow(
    override val stableId: Long,
    @ItgAutoTextField val title: String,
) : ItgListItem {
    @ItgBind
    fun bind(binding: ItemMixedBinding, actions: FeedActions) {
        binding.root.setOnClickListener { actions.onUserClick(stableId) }
        binding.title.text = title
    }
}
```

注意：自动字段存在时生成器仍将绑定工作交给 `@ItgBind`，不会再额外生成字段赋值；自动字段主要贡献 payload。手写 bind 必须自行更新字段。

## 6. 必须避免的声明

- Item 没有实现 `ItgListItem`。
- 既没有 `@ItgBind`，也没有 `@ItgAutoTextField`。
- 一个类同时标注 `@ItgViewBindingItem` 和 `@ItgDataBindingItem`，这会造成同一 Item 类型重复注册。
- binding/actions 使用简单类名字符串；生成文件需要完整限定名。
- 不同 Item 使用同一个 actions 类型但实际属于不应混合的 adapter。此时应拆分 actions 类型，以生成不同 registry。

