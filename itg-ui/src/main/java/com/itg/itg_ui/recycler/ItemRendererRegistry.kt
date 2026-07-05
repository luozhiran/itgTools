package com.itg.itg_ui.recycler

/** 构建完成后不可变的 Item Renderer 注册表。 */
class ItemRendererRegistry<A : Any> internal constructor(
    renderers: List<ItemRenderer<out Any, out androidx.viewbinding.ViewBinding, A>>,
) {
    private val rendererByClass: Map<Class<out Any>, RegisteredRenderer<A>>
    private val rendererByViewType: Map<Int, RegisteredRenderer<A>>

    init {
        val duplicate = renderers.groupBy { it.itemClass }.entries.firstOrNull { it.value.size > 1 }
        require(duplicate == null) {
            "Item 类型 ${duplicate?.key?.name} 注册了多个 Renderer。"
        }
        val registered = renderers.mapIndexed { index, renderer ->
            RegisteredRenderer(viewType = index + 1, renderer = renderer)
        }
        rendererByClass = registered.associateBy { it.renderer.itemClass }
        rendererByViewType = registered.associateBy { it.viewType }
    }

    val size: Int get() = rendererByClass.size

    internal fun rendererFor(item: Any): RegisteredRenderer<A> =
        rendererByClass[item.javaClass] ?: throw IllegalArgumentException(
            "未注册 ${item.javaClass.name} 的 ItemRenderer。请在 itgRecyclerAdapter DSL 中注册该类型。"
        )

    internal fun rendererForViewType(viewType: Int): RegisteredRenderer<A> =
        rendererByViewType[viewType] ?: throw IllegalArgumentException(
            "未知 viewType=$viewType；RendererRegistry 可能与 Adapter 不匹配。"
        )
}

internal data class RegisteredRenderer<A : Any>(
    val viewType: Int,
    val renderer: ItemRenderer<out Any, out androidx.viewbinding.ViewBinding, A>,
)

class ItemRendererRegistryBuilder<A : Any> {
    private val renderers = mutableListOf<ItemRenderer<out Any, out androidx.viewbinding.ViewBinding, A>>()

    fun <I : Any, VB : androidx.viewbinding.ViewBinding> renderer(
        renderer: ItemRenderer<I, VB, A>,
    ) {
        renderers += renderer
    }

    internal fun build(): ItemRendererRegistry<A> {
        require(renderers.isNotEmpty()) { "至少需要注册一个 ItemRenderer。" }
        return ItemRendererRegistry(renderers.toList())
    }
}
