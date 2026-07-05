# 02 itg-ksp-runtime 完整教程

## 1. runtime 提供什么

`itg-ksp-runtime` 公开三个 API：

```kotlin
interface GeneratedRecyclerRegistry<A : Any> {
    fun register(builder: ItemRendererRegistryBuilder<A>)
}

fun <A : Any> itgGeneratedRecyclerAdapter(
    actions: A,
    registry: GeneratedRecyclerRegistry<A>,
): ItgRecyclerAdapter<A>

fun <A : Any> GeneratedRecyclerRegistry<A>.adapter(
    actions: A,
): ItgRecyclerAdapter<A>
```

它不负责扫描注解，也不持有 Android 生命周期。它只把 registry 注册内容交给 `itg-ui` 的 `itgRecyclerAdapter`。

## 2. 使用生成的工厂函数（推荐）

处理器会为 `ProfileActions` 生成：

```kotlin
fun createProfileRecyclerAdapter(actions: ProfileActions): ItgRecyclerAdapter<ProfileActions>
```

业务侧直接调用：

```kotlin
private val adapter by lazy {
    createProfileRecyclerAdapter(this)
}
```

这是最短、最不容易引用错 registry 的方式。

## 3. 直接调用 runtime 桥接函数

需要自己封装扩展函数时：

```kotlin
fun ProfileActions.createAdapter() =
    itgGeneratedRecyclerAdapter(this, GeneratedProfileRegistry)
```

适合统一 adapter 创建入口或隐藏生成类名称。

## 4. registry 扩展函数写法

```kotlin
val adapter = GeneratedProfileRegistry.adapter(actions = this)
```

它与 `itgGeneratedRecyclerAdapter(this, GeneratedProfileRegistry)` 完全等价。

## 5. 手写 registry

runtime 不要求 registry 必须由 KSP 生成。迁移旧代码、测试或需要生成器暂不支持的 DSL 参数时，可以手写：

```kotlin
object ManualProfileRegistry : GeneratedRecyclerRegistry<ProfileActions> {
    override fun register(builder: ItemRendererRegistryBuilder<ProfileActions>) {
        with(builder) {
            viewBinding<ProfileRow, ItemProfileBinding, ProfileActions>(
                inflate = ItemProfileBinding::inflate,
            ) { item, actions ->
                title.text = item.title
                root.setOnClickListener { actions.onClick(item.stableId) }
            }
        }
    }
}

val adapter = ManualProfileRegistry.adapter(this)
```

这种方式仍复用 runtime，但不会使用 `itg-ksp-compiler`。

## 6. 与 RecyclerViewAbility 配合

Activity：

```kotlin
private val recyclerAbility = RecyclerViewAbility()
private val adapter by lazy { createProfileRecyclerAdapter(this) }

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    recyclerAbility.bind(binding.recyclerView, this, adapter)
    recyclerAbility.observeItems(viewModel.rows)
}
```

Fragment 必须使用 `viewLifecycleOwner`：

```kotlin
override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    recyclerAbility.bind(binding.recyclerView, viewLifecycleOwner, adapter)
    recyclerAbility.observeItems(viewModel.rows)
}
```

不用 LiveData 时，保留 `bind` 返回的 controller：

```kotlin
val controller = recyclerAbility.bind(binding.recyclerView, this, adapter)
controller.submitList(rows)
controller.clear()
controller.scrollToPosition(0)
```

## 7. 无业务回调的列表

泛型 `A` 必须是非空对象类型。注解方式下建议定义一个无成员对象，不要尝试让生成器输出到 `kotlin` 包：

```kotlin
object NoRowActions
```

然后让所有 Item 的 `actionsClassName` 指向该对象的完整类名，创建 adapter 时传 `NoRowActions`。

## 8. runtime 与 compiler 的边界

- runtime 支持手写 registry、任意 `itg-ui` Renderer DSL。
- compiler 当前只生成 ViewBinding、DataBinding、payload/contents 和 Tab 的固定子集。
- 需要 `onRecycled`、自定义 `bindPayload` 等生成器未暴露能力时，使用手写 registry。

