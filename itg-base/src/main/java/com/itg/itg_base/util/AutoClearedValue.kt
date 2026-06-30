package com.example.itg_base.util

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * 可显式清理的引用包装器：在 onDestroy 时调用 [clear] 切断引用，防止内存泄漏。
 *
 * 方式一：包装器（需要 .get() / .set() / .clear()）：
 * ```
 * private val adapterHolder = AutoClearedValue<MyAdapter>()
 * override fun onCreate(...) { adapterHolder.set(MyAdapter()) }
 * override fun onDestroy()  { adapterHolder.clear() }
 * ```
 *
 * 方式二：属性委托（一行声明，自动管理）：
 * ```
 * private var adapter: MyAdapter? by autoCleared()
 * override fun onDestroy() { adapter = null }  // 赋 null 即清理
 * ```
 */
class AutoClearedValue<T : Any> {
    private var _value: T? = null

    val isInitialized: Boolean get() = _value != null

    fun get(): T? = _value

    fun set(value: T) {
        _value = value
    }

    fun clear() {
        _value = null
    }

    fun require(): T =
        _value ?: throw IllegalStateException("AutoClearedValue 尚未设置或已被清理")
}

/**
 * 属性委托版本：声明一个可赋 null 来清理的引用。
 *
 * ```
 * private var adapter: MyAdapter? by autoCleared()
 *
 * override fun onCreate(...) {
 *     adapter = MyAdapter()          // 设置
 *     recyclerView.adapter = adapter // 使用
 * }
 *
 * override fun onDestroy() {
 *     adapter = null                  // 切断引用
 * }
 * ```
 */
fun <T : Any> autoCleared(): ReadWriteProperty<Any?, T?> = AutoClearedDelegate()

private class AutoClearedDelegate<T : Any> : ReadWriteProperty<Any?, T?> {
    private var _value: T? = null

    override operator fun getValue(thisRef: Any?, property: KProperty<*>): T? = _value

    override operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T?) {
        _value = value
    }
}
