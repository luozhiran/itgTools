package com.example.itg_base.arch

/**
 * 一次性事件包装：解决 LiveData 在配置变更（如旋转屏幕）后重复消费上次事件的问题。
 *
 * 使用：
 * ```
 * // ViewModel 中发送
 * private val _navEvent = MutableLiveData<Event<String>>()
 * val navEvent: LiveData<Event<String>> = _navEvent
 * fun onItemClick(id: String) { _navEvent.value = Event(id) }
 *
 * // Activity 中消费
 * viewModel.navEvent.observe(this) { event ->
 *     event.getContentIfNotHandled()?.let { id -> navigateTo(id) }
 * }
 * ```
 */
class Event<out T>(private val content: T) {

    private var handled = false

    /**
     * 获取内容（仅首次调用返回非 null，之后返回 null）。
     * 保证一次事件只被消费一次。
     */
    fun getContentIfNotHandled(): T? {
        if (handled) return null
        handled = true
        return content
    }

    /**
     * 窥视内容，不影响 handled 状态。
     */
    fun peekContent(): T = content

    /**
     * 是否已被消费。
     */
    val isHandled: Boolean get() = handled
}
