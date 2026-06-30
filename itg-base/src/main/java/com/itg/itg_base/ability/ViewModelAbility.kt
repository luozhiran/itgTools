package com.example.itg_base.ability

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.itg_base.arch.ItgModel

/**
 * ViewModel 能力组件。
 *
 * 泛型 T 的具体 Class 由构造参数传入（从 AutoBindingBaseActivity 的 genericSuperclass 推导），
 * 解决此前 javaClass.genericSuperclass 拿到自身父类 LifeAbility 的错误。
 */
class ViewModelAbility<T : ItgModel>(
    private val modelClass: Class<T>
) : LifeAbility() {

    val viewModel: T by lazy { getViewModel() }

    override fun inject(activity: AppCompatActivity): ViewModelAbility<T> {
        super.inject(activity)
        return this
    }

    private fun getViewModel(): T {
        return ViewModelProvider(ownerActivity).get(modelClass)
    }
}
