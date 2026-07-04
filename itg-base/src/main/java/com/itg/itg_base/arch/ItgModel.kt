package com.example.itg_base.arch

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

/**
 * ViewModel 基类，内置常用状态：
 * - [loading]  加载状态，UI 层观察后自动控制 Loading 显隐
 * - [event]    一次性事件（导航、操作提示等），配置变更不重复触发
 * - [error]    错误信息，消费一次后自动消失
 *
 * 子类使用：
 * ```
 * class MyModel(app: Application) : ItgModel(app) {
 *     fun loadData() {
 *         showLoading()
 *         launch {
 *             try { ...; hideLoading() }
 *             catch(e: Exception) { postError("加载失败: ${e.message}") }
 *         }
 *     }
 * }
 * ```
 */
open class ItgModel(application: Application) : AndroidViewModel(application) {

    // ==================== Loading ====================

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    protected fun showLoading() {
        _loading.postValue(true)
    }

    protected fun hideLoading() {
        _loading.postValue(false)
    }

    // ==================== Event（一次性事件） ====================

    private val _event = MutableLiveData<Event<Any>>()
    val event: LiveData<Event<Any>> = _event

    /**
     * 发送一次性事件。子类应定义自己的 Event 密封类并调用此方法发送。
     * 示例：postEvent(NavigationEvent.GoToHome)
     */
    protected fun postEvent(value: Any) {
        _event.postValue(Event(value))
    }

    // ==================== Error ====================

    private val _error = MutableLiveData<Event<String>>()
    val error: LiveData<Event<String>> = _error

    protected fun postError(message: String) {
        _error.postValue(Event(message))
    }
}
