package com.example.itg_base.ability

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isNotEmpty

/**
 * UI 状态能力：Loading / Empty / Error 三种状态切换，布局纯代码创建，无需额外资源文件。
 *
 * 会对容器内的原有内容自动隐藏/显示，确保状态页覆盖在内容之上。
 *
 * 使用：
 * ```
 * uiState.bind(binding.root)
 * uiState.showLoading()
 * uiState.showError("网络异常") { retry() }
 * uiState.showEmpty("暂无数据")
 * uiState.showContent()
 * ```
 */
class UiStateAbility : LifeAbility() {

    private var container: ViewGroup? = null
    private var contentView: View? = null
    private var stateOverlay: FrameLayout? = null

    private var loadingView: View? = null
    private var emptyView: View? = null
    private var errorView: View? = null

    /**
     * 绑定到容器。容器中第一个子 View 被视为"内容"，会被状态页遮挡/显示。
     */
    fun bind(container: ViewGroup) {
        this.container = container
        if (container.isNotEmpty()) {
            contentView = container.getChildAt(0)
        }
    }

    fun showLoading() {
        val ctx = container?.context ?: return
        ensureOverlay(ctx)
        hideAllStates()
        if (loadingView == null) {
            loadingView = createLoadingView(ctx)
            stateOverlay?.addView(loadingView)
        }
        loadingView?.visibility = View.VISIBLE
    }

    fun showEmpty(message: String = "暂无数据") {
        val ctx = container?.context ?: return
        ensureOverlay(ctx)
        hideAllStates()
        if (emptyView == null) {
            emptyView = createMessageView(ctx, message)
            stateOverlay?.addView(emptyView)
        }
        (emptyView?.findViewWithTag<TextView>("msg"))?.text = message
        emptyView?.visibility = View.VISIBLE
    }

    fun showError(message: String, retryAction: (() -> Unit)? = null) {
        val ctx = container?.context ?: return
        ensureOverlay(ctx)
        hideAllStates()
        if (errorView == null) {
            errorView = createErrorView(ctx, message, retryAction)
            stateOverlay?.addView(errorView)
        }
        (errorView?.findViewWithTag<TextView>("msg"))?.text = message
        val retryBtn = errorView?.findViewWithTag<Button>("retry")
        retryBtn?.visibility = if (retryAction != null) View.VISIBLE else View.GONE
        retryBtn?.setOnClickListener { retryAction?.invoke() }
        errorView?.visibility = View.VISIBLE
    }

    fun showContent() {
        hideAllStates()
    }

    // ==================== 内部实现 ====================

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

    private fun createLoadingView(ctx: Context): View {
        return LinearLayout(ctx).apply {
            gravity = Gravity.CENTER
            addView(ProgressBar(ctx))
        }
    }

    private fun createMessageView(ctx: Context, message: String): View {
        return FrameLayout(ctx).apply {
            addView(TextView(ctx).apply {
                text = message
                gravity = Gravity.CENTER
                tag = "msg"
            })
        }
    }

    private fun createErrorView(ctx: Context, message: String, retryAction: (() -> Unit)?): View {
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

    override fun onDestroy(owner: androidx.lifecycle.LifecycleOwner) {
        container?.removeView(stateOverlay)
        stateOverlay = null
        loadingView = null
        emptyView = null
        errorView = null
        container = null
        contentView = null
        super.onDestroy(owner)
    }
}
