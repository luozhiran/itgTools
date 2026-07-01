package com.itg.itg_base.ability

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
        val inflateMethod = try {
            bindingClass.getMethod("inflate", LayoutInflater::class.java)
        } catch (e: NoSuchMethodException) {
            throw IllegalArgumentException(
                "${bindingClass.name} 不是有效的 ViewBinding/DataBinding 生成类。" +
                    "请确认该类由 Android Gradle Plugin 自动生成，" +
                    "且 buildFeatures { viewBinding = true } 或 dataBinding = true 已开启。",
                e
            )
        }
        binding = inflateMethod.invoke(null, activity.layoutInflater) as VB
        if (setContent) {
            activity.setContentView(binding.root)
        }
    }
}
