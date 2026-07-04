package com.example.itg_base.binding

import androidx.databinding.ViewDataBinding
import androidx.lifecycle.LifecycleOwner

/**
 * 为 DataBinding 布局设置 [LifecycleOwner]，
 * 使 XML 中的 LiveData 绑定表达式 `@{vm.xxx}` 能跟随生命周期自动更新/停止。
 */
fun ViewDataBinding.bindLifecycle(owner: LifecycleOwner) {
    lifecycleOwner = owner
}

/**
 * 为 DataBinding 布局设置 ViewModel 变量。
 *
 * 使用：
 * ```
 * binding.bindVariable(BR.viewModel, viewModel)
 * ```
 */
fun <T> ViewDataBinding.bindVariable(variableId: Int, value: T?) {
    setVariable(variableId, value)
    executePendingBindings()
}
