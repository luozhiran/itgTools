package com.itg.itg_ksp.runtime

import com.itg.itg_ui.recycler.ItgRecyclerAdapter
import com.itg.itg_ui.recycler.ItemRendererRegistryBuilder
import com.itg.itg_ui.recycler.itgRecyclerAdapter

interface GeneratedRecyclerRegistry<A : Any> {
    fun register(builder: ItemRendererRegistryBuilder<A>)
}

fun <A : Any> itgGeneratedRecyclerAdapter(
    actions: A,
    registry: GeneratedRecyclerRegistry<A>,
): ItgRecyclerAdapter<A> = itgRecyclerAdapter(actions) {
    registry.register(this)
}

fun <A : Any> GeneratedRecyclerRegistry<A>.adapter(actions: A): ItgRecyclerAdapter<A> =
    itgGeneratedRecyclerAdapter(actions, this)
