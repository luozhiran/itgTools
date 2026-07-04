package com.itg.itg_base

import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.viewbinding.ViewBinding

import com.example.itg_base.arch.ItgModel
import com.itg.itg_base.ability.ActivityResultAbility
import com.itg.itg_base.ability.MessageAbility
import com.itg.itg_base.ability.PermissionAbility
import com.itg.itg_base.ability.SystemBarAbility
import com.itg.itg_base.ability.UiStateAbility
import com.itg.itg_base.ability.ViewBindingAbility
import com.itg.itg_base.ability.ViewModelAbility
import java.lang.reflect.ParameterizedType

/**
 * 组合式基类 Activity，通过 Ability 组合提供以下能力：
 *
 * | 访问方式 | 类型 | 说明 |
 * |---|---|---|
 * | [binding] | VB | 布局绑定，泛型直出，无需手动 inflate |
 * | [viewModel] | VM | ViewModel，自动创建，泛型直出 |
 * | [systemBars] | SystemBarAbility | 边到边 + insets 处理 |
 * | [launcher] | ActivityResultAbility | 选图 / 拍照 / 跳转回调 |
 * | [permissions] | PermissionAbility | 运行时权限请求 |
 * | [messages] | MessageAbility | Toast / Snackbar |
 * | [uiState] | UiStateAbility | Loading / Empty / Error 状态 |
 *
 * 子类只需声明双泛型：
 * ```
 * class MainActivity : AutoBindingBaseActivity<ActivityMainBinding, MyModel>() {
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *         binding.tvTitle.text = "Hello"
 *         viewModel.loadData()
 *     }
 * }
 * ```
 *
 * @param VB ViewBinding 或 DataBinding 生成类
 * @param VM ViewModel 子类，须继承 [ItgModel]
 */
abstract class AutoBindingBaseActivity<
    VB : ViewBinding,
    VM : ItgModel
> : AppCompatActivity() {

    // ==================== 产物（子类直接使用） ====================

    lateinit var binding: VB
        protected set

    lateinit var viewModel: VM
        protected set

    // ==================== 能力（子类按需调用） ====================

    /** 边到边 + 系统栏 insets。子类可覆写以传入自定义 [SystemBarAbility.Config]。 */
    protected open val systemBars: SystemBarAbility by lazy { SystemBarAbility() }

    /** ActivityResult 启动回调。 */
    val launcher: ActivityResultAbility
        get() = activityResultAbility

    /** 运行时权限。子类可覆写以传入自定义 [PermissionAbility.Config]。 */
    protected open val permissions: PermissionAbility by lazy { PermissionAbility() }

    /** Toast / Snackbar。子类可覆写以传入自定义 [MessageAbility.Config]。 */
    protected open val messages: MessageAbility by lazy { MessageAbility() }

    /** Loading / Empty / Error 状态。子类可覆写以传入自定义 [UiStateAbility.Config]。 */
    protected open val uiState: UiStateAbility by lazy { UiStateAbility() }

    private var viewModelStatesObserved = false

    // ==================== 内部 Ability ====================

    private val activityResultAbility: ActivityResultAbility by lazy { ActivityResultAbility() }
    private val viewModelAbility: ViewModelAbility<VM> by lazy { ViewModelAbility(modelClass) }
    private val bindingAbility: ViewBindingAbility<VB> by lazy { ViewBindingAbility(bindingClass) }

    // ==================== 泛型推导 ====================

    @Suppress("UNCHECKED_CAST")
    private val bindingClass: Class<VB> by lazy { resolveTypeArg(0) as Class<VB> }

    @Suppress("UNCHECKED_CAST")
    private val modelClass: Class<VM> by lazy { resolveTypeArg(1) as Class<VM> }

    /**
     * 安全提取泛型实参，校验失败时给出明确错误信息（而非裸 NPE/ClassCastException）。
     */
    private fun resolveTypeArg(index: Int): Class<*> {
        val superClass = javaClass.genericSuperclass
        check(superClass is ParameterizedType) {
            "${javaClass.simpleName} 必须以具体泛型继承，例如：\n" +
                "  class ${javaClass.simpleName} : ${this::class.java.simpleName}<" +
                "ActivityMainBinding, MyModel>()\n" +
                "当前未声明泛型参数，无法自动推导。"
        }
        val args = superClass.actualTypeArguments
        check(index < args.size) {
            "泛型参数不足：需要 ${index + 1} 个，实际声明了 ${args.size} 个。\n" +
                "请确保同时声明 ViewBinding 和 ViewModel 泛型：" +
                "AutoBindingBaseActivity<VB, VM>"
        }
        check(args[index] is Class<*>) {
            "泛型参数 ${args[index]} 不是具体类。" +
                "请确保传入的是具体类型（如 ActivityMainBinding::class.java），而非类型变量。"
        }
        return args[index] as Class<*>
    }

    // ==================== 生命周期 ====================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. 边到边（必须在 setContentView 前）
        systemBars.inject(this)
        systemBars.useEnableEdgeToEdge()

        // 2. 布局 inflate + setContentView
        bindingAbility.inject(this)
        binding = bindingAbility.binding

        // 3. ViewModel 创建
        viewModelAbility.inject(this)
        viewModel = viewModelAbility.viewModel

        // 4. 其余能力
        activityResultAbility.inject(this)
        permissions.inject(this)
        messages.inject(this)
        uiState.inject(this)

        // 5. UiState 绑定到根布局
        if (binding.root is ViewGroup) {
            uiState.bind(binding.root as ViewGroup)
        } else {
            Log.w("AutoBindingBase", "布局根元素不是 ViewGroup，UiState 绑定跳过。" +
                " 如果使用了 <merge> 标签请改用 ViewGroup 包裹。")
        }

        // 6. 自动桥接 ViewModel 状态 → UI
        observeViewModelStates()

        // 7. insets 处理（必须在 setContentView 后）
        systemBars.updateWindowInsets(this)
    }

    /**
     * 将 ViewModel 的 [ItgModel.loading]、[ItgModel.error] 自动桥接到 UI 能力。
     *
     * ┌──────────────┐      ┌─────────────────────┐
     * │  ItgModel    │      │  AutoBindingBase     │
     * │              │      │                      │
     * │ showLoading()│ ────→│ uiState.showLoading() │
     * │ hideLoading()│ ────→│ uiState.showContent() │
     * │ postError()  │ ────→│ messages.toast(msg)   │
     * └──────────────┘      └─────────────────────┘
     *
     * 子类可覆写以禁用或自定义行为：
     * ```
     * override fun observeViewModelStates() {
     *     // 不调用 super，自己手动处理
     *     viewModel.loading.observe(this) { ... }
     * }
     * ```
     */
    protected open fun observeViewModelStates() {
        if (viewModelStatesObserved) return
        viewModelStatesObserved = true

        viewModel.loading.observe(this) { isLoading ->
            if (isLoading) uiState.showLoading() else uiState.showContent()
        }

        viewModel.error.observe(this) { event ->
            event.getContentIfNotHandled()?.let { messages.toast(it) }
        }
    }
}
