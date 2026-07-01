package com.itg.itg_base

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding
import com.example.itg_base.ability.MessageAbility
import com.example.itg_base.ability.PermissionAbility
import com.example.itg_base.ability.UiStateAbility
import com.example.itg_base.ability.ViewBindingAbility
import com.example.itg_base.ability.ViewModelAbility
import com.example.itg_base.arch.ItgModel
import java.lang.reflect.ParameterizedType

/**
 * 组合式基类 Fragment，与 [AutoBindingBaseActivity] 能力对称。
 *
 * 关键区别：LiveData 观察使用 [viewLifecycleOwner]，
 * 确保 Fragment 视图重建时自动解绑旧观察者。
 *
 * | 访问方式 | 类型 | 说明 |
 * |---|---|---|
 * | [binding] | VB | 布局绑定 |
 * | [viewModel] | VM | ViewModel（与宿主 Activity 共享） |
 * | [permissions] | PermissionAbility | 权限请求 |
 * | [messages] | MessageAbility | Toast / Snackbar |
 * | [uiState] | UiStateAbility | Loading / Empty / Error |
 *
 * @param VB ViewBinding 生成类
 * @param VM ViewModel 子类，须继承 [ItgModel]
 */
abstract class AutoBindingBaseFragment<
    VB : ViewBinding,
    VM : ItgModel
> : Fragment() {

    private var _binding: VB? = null
    /** 在 onCreateView 之后可用，onDestroyView 自动置 null。 */
    protected val binding: VB get() = _binding!!

    protected lateinit var viewModel: VM

    /** 仅 Fragment 可用的能力（不需要 Activity 上下文）。 */
    protected open val messages: MessageAbility by lazy { MessageAbility() }
    protected open val uiState: UiStateAbility by lazy { UiStateAbility() }

    /** 需要宿主 Activity 的能力（通过 requireActivity() 注入）。子类可覆写。 */
    protected open val permissions: PermissionAbility by lazy { PermissionAbility() }

    private var viewModelStatesObserved = false

    private val viewModelAbility: ViewModelAbility<VM> by lazy { ViewModelAbility(modelClass) }
    private val bindingAbility: ViewBindingAbility<VB> by lazy {
        ViewBindingAbility(bindingClass, setContent = false)
    }

    // ==================== 泛型推导 ====================

    @Suppress("UNCHECKED_CAST")
    private val bindingClass: Class<VB> by lazy {
        val type = javaClass.genericSuperclass as ParameterizedType
        check(type.actualTypeArguments.size > 0) {
            "${javaClass.simpleName} 须声明泛型：" +
                "AutoBindingBaseFragment<XxxBinding, XxxModel>"
        }
        type.actualTypeArguments[0] as Class<VB>
    }

    @Suppress("UNCHECKED_CAST")
    private val modelClass: Class<VM> by lazy {
        val type = javaClass.genericSuperclass as ParameterizedType
        check(type.actualTypeArguments.size > 1) {
            "缺少 ViewModel 泛型：" +
                "AutoBindingBaseFragment<VB, VM> 需要两个参数"
        }
        type.actualTypeArguments[1] as Class<VM>
    }

    // ==================== 生命周期 ====================

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        bindingAbility.inject(requireActivity() as androidx.appcompat.app.AppCompatActivity)
        _binding = bindingAbility.binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModelAbility.inject(requireActivity() as androidx.appcompat.app.AppCompatActivity)
        viewModel = viewModelAbility.viewModel

        permissions.inject(requireActivity() as androidx.appcompat.app.AppCompatActivity)
        messages.inject(requireActivity() as androidx.appcompat.app.AppCompatActivity)
        uiState.inject(requireActivity() as androidx.appcompat.app.AppCompatActivity)

        if (binding.root is ViewGroup) {
            uiState.bind(binding.root as ViewGroup)
        } else {
            Log.w("AutoBindingBase", "布局根元素不是 ViewGroup，UiState 绑定跳过。" +
                " 如果使用了 <merge> 标签请改用 ViewGroup 包裹。")
        }

        // 关键：使用 viewLifecycleOwner，Fragment 视图重建时自动解绑
        observeViewModelStates()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * 将 ViewModel 状态自动桥接到 UI。子类可覆写以禁用或自定义。
     */
    protected open fun observeViewModelStates() {
        if (viewModelStatesObserved) return
        viewModelStatesObserved = true

        viewModel.loading.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading) uiState.showLoading() else uiState.showContent()
        }

        viewModel.error.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { messages.toast(it) }
        }
    }
}
