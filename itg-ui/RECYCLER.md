# itg-ui RecyclerView 框架

## 1. 定义 UI Item 和事件

```kotlin
data class UserRow(
    override val stableId: Long,
    val name: String,
) : ItgListItem

data class BannerRow(
    override val stableId: Long,
    val imageUrl: String,
) : ItgListItem

interface FeedActions {
    fun onUserClick(id: Long)
    fun onBannerClick(id: Long)
}
```

`stableId` 必须在整个列表中唯一，不能只在相同 Item 类型内唯一。

## 2. ViewBinding 多 Item

```kotlin
val adapter = itgRecyclerAdapter<FeedActions>(actions = this) {
    viewBinding<UserRow, ItemUserBinding, FeedActions>(
        inflate = ItemUserBinding::inflate,
    ) { item, actions ->
        name.text = item.name
        root.setOnClickListener { actions.onUserClick(item.stableId) }
    }

    viewBinding<BannerRow, ItemBannerBinding, FeedActions>(
        inflate = ItemBannerBinding::inflate,
    ) { item, actions ->
        root.setOnClickListener { actions.onBannerClick(item.stableId) }
    }
}
```

需要局部刷新时配置 `getChangePayload` 和 `bindPayload`；`bindPayload` 返回 false 会自动回退为完整绑定。

## 3. DataBinding 自动绑定

Item XML 使用 `<layout>` 并声明变量：

```xml
<data>
    <variable name="item" type="com.example.UserRow" />
    <variable name="actions" type="com.example.FeedActions" />
</data>
```

注册时只需声明布局和变量：

```kotlin
val adapter = itgRecyclerAdapter<FeedActions>(this) {
    dataBinding<UserRow, FeedActions>(
        layoutId = R.layout.item_user,
        itemVariableId = BR.item,
        actionsVariableId = BR.actions,
    )
}
```

框架自动设置 `item`、`actions`、`lifecycleOwner` 并执行 pending bindings。

## 4. 生命周期和自动提交数据

```kotlin
private val recyclerAbility = RecyclerViewAbility(
    RecyclerConfig(hasFixedSize = true)
)

override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    val controller = recyclerAbility.bind(
        recyclerView = binding.recyclerView,
        lifecycleOwner = viewLifecycleOwner,
        adapter = adapter,
    )
    recyclerAbility.observeItems(viewModel.rows)

    // 或手动提交
    controller.submitList(rows)
}
```

Fragment 必须使用 `viewLifecycleOwner`。生命周期销毁后框架自动移除 LiveData Observer、清空 Adapter 引用和 DataBinding lifecycleOwner。
