package com.itg.itg_base.ability

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/**
 * UI 状态能力：Loading / Empty / Error 三种状态切换。
 *
 * ## 默认使用
 * ```
 * uiState.bind(binding.root)
 * uiState.showLoading()
 * uiState.showError("网络异常") { retry() }
 * uiState.showEmpty("暂无数据")
 * uiState.showContent()
 * ```
 *
 * ## 自定义（Config）
 * ```
 * UiStateAbility(
 *     UiStateAbility.Config(
 *         loadingViewProvider = { ctx -> MyLottieView(ctx) },
 *         emptyViewProvider = { ctx, msg -> MyEmptyView(ctx, msg) },
 *         errorViewProvider = { ctx, msg, retry -> MyErrorView(ctx, msg, retry) },
 *     )
 * )
 * ```
 *
 * ## 自定义（子类覆写）
 * ```
 * object : UiStateAbility() {
 *     override fun createLoadingView(ctx: Context) = MyLottieView(ctx)
 *     override fun createEmptyView(ctx: Context, msg: String) = MyEmptyView(ctx, msg)
 * }
 * ```
 *
 * **自定义 View 注意事项**：如果你使用 Config 或覆写方法提供自定义 View，
 * `showEmpty`/`showError` 中将跳过默认的文本/按钮更新逻辑（默认依赖 tag 查找）。
 * 自定义 View 应自行管理内部状态。
 */
class UiStateAbility(
    private val config: Config = Config()
) : LifeAbility() {

    data class Config(
        val loadingViewProvider: ((Context) -> View)? = null,
        val emptyViewProvider: ((Context, String) -> View)? = null,
        val errorViewProvider: ((Context, String, (() -> Unit)?) -> View)? = null,
    )

    private var container: ViewGroup? = null
    private var stateOverlay: FrameLayout? = null

    private var loadingView: View? = null
    private var emptyView: View? = null
    private var errorView: View? = null

    /** 当前视图是否由默认实现创建（用于判断是否可走 tag 约定）。 */
    private var isCustomLoading = false
    private var isCustomEmpty = false
    private var isCustomError = false

    /** 上次传入的参数，自定义 View 参数变化时需重建。 */
    private var lastEmptyMessage: String? = null
    private var lastErrorMessage: String? = null
    private var lastRetryAction: (() -> Unit)? = null

    /** 绑定到容器。容器中第一个子 View 被视为"内容"，会被状态页遮挡/显示。 */
    fun bind(container: ViewGroup) {
        this.container = container
    }

    fun showLoading() {
        val ctx = container?.context ?: return
        ensureOverlay(ctx)
        hideAllStates()
        if (loadingView == null) {
            isCustomLoading = true  // 预设为自定义，默认路径会改回 false
            loadingView = createLoadingView(ctx)
            stateOverlay?.addView(loadingView)
        }
        loadingView?.visibility = View.VISIBLE
    }

    fun showEmpty(message: String = "暂无数据") {
        val ctx = container?.context ?: return
        ensureOverlay(ctx)
        hideAllStates()
        // 自定义 View 无法通过 tag 更新文本，参数变化时重建
        if (isCustomEmpty && emptyView != null && lastEmptyMessage != message) {
            stateOverlay?.removeView(emptyView)
            emptyView = null
        }
        lastEmptyMessage = message
        if (emptyView == null) {
            isCustomEmpty = true
            emptyView = createEmptyView(ctx, message)
            stateOverlay?.addView(emptyView)
        }
        if (!isCustomEmpty) {
            emptyView?.findViewWithTag<TextView>("msg")?.text = message
        }
        emptyView?.visibility = View.VISIBLE
    }

    fun showError(message: String, retryAction: (() -> Unit)? = null) {
        val ctx = container?.context ?: return
        ensureOverlay(ctx)
        hideAllStates()
        // 自定义 View 参数变化时重建
        if (isCustomError && errorView != null &&
            (lastErrorMessage != message || lastRetryAction !== retryAction)
        ) {
            stateOverlay?.removeView(errorView)
            errorView = null
        }
        lastErrorMessage = message
        lastRetryAction = retryAction
        if (errorView == null) {
            isCustomError = true
            errorView = createErrorView(ctx, message, retryAction)
            stateOverlay?.addView(errorView)
        }
        if (!isCustomError) {
            errorView?.findViewWithTag<TextView>("msg")?.text = message
            errorView?.findViewWithTag<Button>("retry")?.apply {
                visibility = if (retryAction != null) View.VISIBLE else View.GONE
                setOnClickListener { retryAction?.invoke() }
            }
        }
        errorView?.visibility = View.VISIBLE
    }

    fun showContent() {
        hideAllStates()
    }

    // ==================== 可覆写的视图创建方法 ====================

    /** 优先级：Config.loadingViewProvider > 覆写 > 默认 ProgressBar */
    protected open fun createLoadingView(ctx: Context): View {
        config.loadingViewProvider?.let {
            isCustomLoading = true
            return it(ctx)
        }
        isCustomLoading = false
        return defaultLoadingView(ctx)
    }

    /** 默认实现包含 tag="msg" 的 TextView，showEmpty 会更新其文本。 */
    protected open fun createEmptyView(ctx: Context, message: String): View {
        config.emptyViewProvider?.let {
            isCustomEmpty = true
            return it(ctx, message)
        }
        isCustomEmpty = false
        return defaultEmptyView(ctx, message)
    }

    /** 默认实现包含 tag="msg" 的 TextView 和 tag="retry" 的 Button。 */
    protected open fun createErrorView(ctx: Context, message: String, retryAction: (() -> Unit)?): View {
        config.errorViewProvider?.let {
            isCustomError = true
            return it(ctx, message, retryAction)
        }
        isCustomError = false
        return defaultErrorView(ctx, message, retryAction)
    }

    // ==================== 默认视图实现 ====================

    private fun defaultLoadingView(ctx: Context): View {
        return LinearLayout(ctx).apply {
            gravity = Gravity.CENTER
            addView(ProgressBar(ctx))
        }
    }

    private fun defaultEmptyView(ctx: Context, message: String): View {
        return FrameLayout(ctx).apply {
            addView(TextView(ctx).apply {
                text = message
                gravity = Gravity.CENTER
                tag = "msg"
            })
        }
    }

    private fun defaultErrorView(ctx: Context, message: String, retryAction: (() -> Unit)?): View {
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(TextView(ctx).apply {
                text = message
                gravity = Gravity.CENTER
                tag = "msg"
            })
            addView(Button(ctx).apply {
                text = "重试"
                tag = "retry"
                visibility = if (retryAction != null) View.VISIBLE else View.GONE
            })
        }
    }

    // ==================== 内部 ====================

    private fun ensureOverlay(ctx: Context) {
        if (stateOverlay != null) return
        stateOverlay = FrameLayout(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        container?.addView(stateOverlay)
    }

    private fun hideAllStates() {
        loadingView?.visibility = View.GONE
        emptyView?.visibility = View.GONE
        errorView?.visibility = View.GONE
    }

    override fun onDestroy(owner: androidx.lifecycle.LifecycleOwner) {
        container?.removeView(stateOverlay)
        stateOverlay = null
        loadingView = null
        emptyView = null
        errorView = null
        container = null
        lastEmptyMessage = null
        lastErrorMessage = null
        lastRetryAction = null
        super.onDestroy(owner)
    }
}
