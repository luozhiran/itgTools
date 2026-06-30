package com.example.itg_base.ability

import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.viewbinding.ViewBinding

/**
 * ViewBinding 能力组件：自动 inflate → 提供泛型 binding 引用。
 *
 * @param bindingClass ViewBinding 生成类
 * @param setContent   true（默认）：inflate 后调用 Activity.setContentView(root)
 *                     false：仅 inflate，不调 setContentView（Fragment 场景）
 */
class ViewBindingAbility<VB : ViewBinding>(
    private val bindingClass: Class<VB>,
    private val setContent: Boolean = true
) : LifeAbility() {

    lateinit var binding: VB
        private set

    override fun inject(activity: AppCompatActivity): ViewBindingAbility<VB> {
        super.inject(activity)
        inflateBinding(activity)
        return this
    }

    @Suppress("UNCHECKED_CAST")
    private fun inflateBinding(activity: AppCompatActivity) {
        val inflateMethod = bindingClass.getMethod("inflate", LayoutInflater::class.java)
        binding = inflateMethod.invoke(null, activity.layoutInflater) as VB
        if (setContent) {
            activity.setContentView(binding.root)
        }
    }
}
