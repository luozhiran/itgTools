package com.itg.itg_base.ability

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

open class LifeAbility : LifecycleEventObserver {
    protected lateinit var ownerActivity: AppCompatActivity

    private var observerAdded = false

    open fun inject(activity: AppCompatActivity): LifeAbility {
        ownerActivity = activity
        watchActivityLife(activity)
        return this
    }

    private fun watchActivityLife(activity: AppCompatActivity) {
        if (observerAdded) return
        activity.lifecycle.addObserver(this)
        observerAdded = true
    }

    /**
     * 检查 ownerActivity 是否已注入且未被销毁。
     * 子类在执行需要 Activity 存活的操作前应调用此方法做防护。
     */
    protected fun isActivityAlive(): Boolean {
        return ::ownerActivity.isInitialized && !ownerActivity.isDestroyed
    }

    private fun unwatchActivityLife() {
        if (observerAdded && ::ownerActivity.isInitialized) {
            ownerActivity.lifecycle.removeObserver(this)
            observerAdded = false
        }
    }

    // ==================== 生命周期回调（protected，仅供子类覆写） ====================

    protected open fun onCreate(owner: LifecycleOwner) {}

    protected open fun onStart(owner: LifecycleOwner) {}

    protected open fun onResume(owner: LifecycleOwner) {}

    protected open fun onPause(owner: LifecycleOwner) {}

    protected open fun onStop(owner: LifecycleOwner) {}

    protected open fun onDestroy(owner: LifecycleOwner) {
        unwatchActivityLife()
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_CREATE -> onCreate(source)
            Lifecycle.Event.ON_START -> onStart(source)
            Lifecycle.Event.ON_RESUME -> onResume(source)
            Lifecycle.Event.ON_PAUSE -> onPause(source)
            Lifecycle.Event.ON_STOP -> onStop(source)
            Lifecycle.Event.ON_DESTROY -> onDestroy(source)
            else -> {}
        }
    }
}
