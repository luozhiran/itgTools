# 01. RecyclerView

本节说明通用 RecyclerView 适配器和多类型渲染注册。

## 适用条件

- 列表 item 类型较多，不想维护多个 Adapter。
- item 使用 ViewBinding 或 DataBinding。
- 需要稳定 key、payload 或统一提交列表。

## 推荐做法

```kotlin
val adapter = itgRecyclerAdapter<MyActions> {
    renderer<MyItem, ItemMyBinding>(...)
}
```

## 可复制 Demo

```kotlin
import com.itg.itg_ui.recycler.itgRecyclerAdapter
import com.itg.itg_ui.recycler.viewBinding

class MyActions(val onClick: (UserItem) -> Unit)

data class UserItem(val id: Long, val name: String)

val actions = MyActions { item -> println(item.id) }
val adapter = itgRecyclerAdapter(actions) {
    viewBinding<UserItem, ItemUserBinding, MyActions>(
        inflate = ItemUserBinding::inflate,
        itemKey = { it.id },
        bind = { binding, item, actions ->
            binding.nameText.text = item.name
            binding.root.setOnClickListener { actions.onClick(item) }
        }
    )
}

recyclerView.adapter = adapter
adapter.submitList(listOf(UserItem(1, "Alice")))
```

## 关键说明

- 每个 item class 只能注册一个 renderer。
- `itemKey` 用于 DiffUtil 身份判断，必须稳定。
- `RecyclerController.submitList` 会复制输入列表，避免外部修改影响 diff。
- `RecyclerViewAbility.bind(...)` 后才能 `observeItems`。
- payload 能力适合只刷新部分字段。

## 验证方式

- 同类型重复注册 renderer 应在初始化阶段暴露错误。
- 修改 item 内容但保持 key 不变时，应走内容刷新而不是新 item。

[返回 README](../README.md)