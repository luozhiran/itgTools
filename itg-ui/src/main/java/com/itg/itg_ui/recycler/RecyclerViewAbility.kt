package com.itg.itg_ui.recycler

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * RecyclerView 绑定能力。直接观察传入的 LifecycleOwner，因此 Fragment 应传 viewLifecycleOwner。
 */
class RecyclerViewAbility(
    private val config: RecyclerConfig = RecyclerConfig(),
) : DefaultLifecycleObserver {
    private var recyclerView: RecyclerView? = null
    private var lifecycleOwner: LifecycleOwner? = null
    private var controller: RecyclerController<*>? = null
    private val itemObserverCleanups = mutableListOf<() -> Unit>()

    fun <A : Any> bind(
        recyclerView: RecyclerView,
        lifecycleOwner: LifecycleOwner,
        adapter: ItgRecyclerAdapter<A>,
    ): RecyclerController<A> {
        check(lifecycleOwner.lifecycle.currentState != Lifecycle.State.DESTROYED) {
            "不能绑定到已销毁的 LifecycleOwner。"
        }
        unbind()

        this.recyclerView = recyclerView
        this.lifecycleOwner = lifecycleOwner
        lifecycleOwner.lifecycle.addObserver(this)

        if (recyclerView.layoutManager == null || config.layoutManagerFactory != null) {
            recyclerView.layoutManager = config.layoutManagerFactory?.invoke(recyclerView.context)
                ?: LinearLayoutManager(recyclerView.context)
        }
        config.itemAnimatorFactory?.let { recyclerView.itemAnimator = it(recyclerView.context) }
        recyclerView.setHasFixedSize(config.hasFixedSize)
        recyclerView.isNestedScrollingEnabled = config.nestedScrollingEnabled
        recyclerView.clipToPadding = config.clipToPadding
        config.itemViewCacheSize?.let(recyclerView::setItemViewCacheSize)

        adapter.lifecycleOwner = lifecycleOwner
        recyclerView.adapter = adapter
        return RecyclerController(adapter, recyclerView).also { controller = it }
    }

    /** 自动观察 LiveData 并提交不可变列表快照。 */
    fun <I : Any> observeItems(items: LiveData<List<I>>) {
        val owner = checkNotNull(lifecycleOwner) { "请先调用 bind()。" }
        val boundController = checkNotNull(controller) { "请先调用 bind()。" }
        clearItemObservers()
        val observer = Observer<List<I>> { value ->
            boundController.submitList(value.orEmpty())
        }
        items.observe(owner, observer)
        itemObserverCleanups += { items.removeObserver(observer) }
    }

    fun unbind() {
        clearItemObservers()

        val boundRecyclerView = recyclerView
        val boundAdapter = controller?.adapter
        boundAdapter?.lifecycleOwner = null
        boundRecyclerView?.let {
            if (it.adapter === boundAdapter) it.adapter = null
        }
        lifecycleOwner?.lifecycle?.removeObserver(this)
        controller = null
        lifecycleOwner = null
        recyclerView = null
    }

    private fun clearItemObservers() {
        itemObserverCleanups.forEach { it() }
        itemObserverCleanups.clear()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        if (owner === lifecycleOwner) unbind()
    }
}
