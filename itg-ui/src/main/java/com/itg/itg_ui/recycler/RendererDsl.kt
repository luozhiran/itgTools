package com.itg.itg_ui.recycler

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.LifecycleOwner
import androidx.viewbinding.ViewBinding

inline fun <reified I : ItgListItem, VB : ViewBinding, A : Any>
    ItemRendererRegistryBuilder<A>.viewBinding(
    noinline inflate: (LayoutInflater, ViewGroup, Boolean) -> VB,
    noinline areContentsTheSame: (I, I) -> Boolean = { old, new -> old == new },
    noinline getChangePayload: (I, I) -> Any? = { _, _ -> null },
    noinline onRecycled: (VB) -> Unit = {},
    noinline bindPayload: (VB, I, A, List<Any>) -> Boolean = { _, _, _, _ -> false },
    noinline bind: VB.(I, A) -> Unit,
) {
    renderer(object : ItemRenderer<I, VB, A>(I::class.java) {
        override fun createBinding(inflater: LayoutInflater, parent: ViewGroup): VB =
            inflate(inflater, parent, false)

        override fun bind(
            binding: VB,
            item: I,
            actions: A,
            lifecycleOwner: LifecycleOwner?,
            payloads: List<Any>,
        ) {
            if (payloads.isEmpty() || !bindPayload(binding, item, actions, payloads)) {
                binding.bind(item, actions)
            }
        }

        override fun areContentsTheSame(oldItem: I, newItem: I): Boolean =
            areContentsTheSame.invoke(oldItem, newItem)

        override fun getChangePayload(oldItem: I, newItem: I): Any? =
            getChangePayload.invoke(oldItem, newItem)

        override fun onViewRecycled(binding: VB) = onRecycled.invoke(binding)
    })
}

inline fun <reified I : ItgListItem, VB : ViewBinding, A : Any>
    ItemRendererRegistryBuilder<A>.viewBindingWithPayloads(
    noinline inflate: (LayoutInflater, ViewGroup, Boolean) -> VB,
    noinline areContentsTheSame: (I, I) -> Boolean = { old, new -> old == new },
    noinline getChangePayload: (I, I) -> Any? = { _, _ -> null },
    noinline onRecycled: (VB) -> Unit = {},
    noinline bindPayload: (VB, I, A, List<Any>) -> Boolean = { _, _, _, _ -> false },
    noinline bind: VB.(I, A, List<Any>) -> Unit,
) {
    renderer(object : ItemRenderer<I, VB, A>(I::class.java) {
        override fun createBinding(inflater: LayoutInflater, parent: ViewGroup): VB =
            inflate(inflater, parent, false)

        override fun bind(
            binding: VB,
            item: I,
            actions: A,
            lifecycleOwner: LifecycleOwner?,
            payloads: List<Any>,
        ) {
            if (payloads.isEmpty() || !bindPayload(binding, item, actions, payloads)) {
                binding.bind(item, actions, payloads)
            }
        }

        override fun areContentsTheSame(oldItem: I, newItem: I): Boolean =
            areContentsTheSame.invoke(oldItem, newItem)

        override fun getChangePayload(oldItem: I, newItem: I): Any? =
            getChangePayload.invoke(oldItem, newItem)

        override fun onViewRecycled(binding: VB) = onRecycled.invoke(binding)
    })
}

inline fun <reified I : ItgListItem, A : Any>
    ItemRendererRegistryBuilder<A>.dataBinding(
    layoutId: Int,
    itemVariableId: Int,
    actionsVariableId: Int? = null,
    noinline areContentsTheSame: (I, I) -> Boolean = { old, new -> old == new },
    noinline getChangePayload: (I, I) -> Any? = { _, _ -> null },
) {
    require(layoutId != 0) { "layoutId 不能为 0。" }
    require(itemVariableId != 0) { "itemVariableId 不能为 0。" }
    require(actionsVariableId == null || actionsVariableId != 0) {
        "actionsVariableId 不能为 0。"
    }
    renderer(object : ItemRenderer<I, ViewDataBinding, A>(I::class.java) {
        override fun createBinding(inflater: LayoutInflater, parent: ViewGroup): ViewDataBinding =
            requireNotNull(DataBindingUtil.inflate(inflater, layoutId, parent, false)) {
                "布局 $layoutId 未生成 ViewDataBinding，请确认布局根节点为 <layout>。"
            }

        override fun bind(
            binding: ViewDataBinding,
            item: I,
            actions: A,
            lifecycleOwner: LifecycleOwner?,
            payloads: List<Any>,
        ) {
            binding.lifecycleOwner = lifecycleOwner
            check(binding.setVariable(itemVariableId, item)) {
                "布局 ${binding.javaClass.name} 不包含 itemVariableId=$itemVariableId。"
            }
            actionsVariableId?.let { variableId ->
                check(binding.setVariable(variableId, actions)) {
                    "布局 ${binding.javaClass.name} 不包含 actionsVariableId=$variableId。"
                }
            }
            binding.executePendingBindings()
        }

        override fun areContentsTheSame(oldItem: I, newItem: I): Boolean =
            areContentsTheSame.invoke(oldItem, newItem)

        override fun getChangePayload(oldItem: I, newItem: I): Any? =
            getChangePayload.invoke(oldItem, newItem)

        override fun onViewRecycled(binding: ViewDataBinding) {
            binding.lifecycleOwner = null
        }
    })
}

fun <A : Any> itgRecyclerAdapter(
    actions: A,
    block: ItemRendererRegistryBuilder<A>.() -> Unit,
): ItgRecyclerAdapter<A> = ItgRecyclerAdapter(
    registry = ItemRendererRegistryBuilder<A>().apply(block).build(),
    actions = actions,
)

fun itgRecyclerAdapter(
    block: ItemRendererRegistryBuilder<Unit>.() -> Unit,
): ItgRecyclerAdapter<Unit> = itgRecyclerAdapter(Unit, block)
