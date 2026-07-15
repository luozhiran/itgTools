# 01. Recycler 注解

本节说明 Recycler 自动生成相关注解。

## 可复制 Demo

```kotlin
import com.itg.itg_ksp.annotations.ItgBind
import com.itg.itg_ksp.annotations.ItgPayload
import com.itg.itg_ksp.annotations.ItgViewBindingItem

@ItgViewBindingItem(
    bindingClassName = "com.example.databinding.ItemUserBinding",
    actionsClassName = "com.example.UserActions",
    itemKeyProperty = "id"
)
data class UserItem(val id: Long, val name: String) {
    @ItgBind
    fun bind(binding: ItemUserBinding, actions: UserActions) {
        binding.nameText.text = name
        binding.root.setOnClickListener { actions.onUserClick(this) }
    }

    @ItgPayload
    fun payload(old: UserItem): List<String> = buildList {
        if (old.name != name) add("name")
    }
}
```

## 关键说明

- `bindingClassName` 和 `actionsClassName` 要写完整类名字符串。
- `itemKeyProperty` 指向稳定身份字段。
- `@ItgBind` 标记绑定函数。
- `@ItgPayload` 用于生成局部刷新 payload。
- DataBinding 使用 `@ItgDataBindingItem`，通过 layout 和 variable 表达式绑定。

## 验证方式

- 编译后应生成对应 Registry/Adapter 代码。
- 注解参数写错会导致 KSP 编译期报错。

[返回 README](../README.md)