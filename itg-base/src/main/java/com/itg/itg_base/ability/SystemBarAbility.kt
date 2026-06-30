package com.itg.itg_base.ability

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.itg_base.ability.LifeAbility

/**
 * 边到边 + 系统栏 insets 能力。
 *
 * ## 默认使用（零配置）
 * ```
 * systemBars.inject(this)
 * systemBars.useEnableEdgeToEdge()
 * systemBars.updateWindowInsets(this)
 * ```
 *
 * ## 自定义配置
 * ```
 * val systemBars = SystemBarAbility(
 *     config = SystemBarAbility.Config(
 *         edgeToEdge = true,
 *         insetsTypes = WindowInsetsCompat.Type.statusBars(), // 只处理状态栏
 *         onApplyInsets = { view, insets ->
 *             // 自定义 insets 处理逻辑
 *             view.setPadding(0, insets.top, 0, 0)
 *             insets
 *         }
 *     )
 * )
 * ```
 */
class SystemBarAbility(
    private val config: Config = Config()
) : LifeAbility() {

    /**
     * @param edgeToEdge       是否开启边到边显示，默认 true
     * @param insetsTypes      需要处理的系统栏类型，默认 systemBars() = 状态栏 + 导航栏
     *                         可选：[WindowInsetsCompat.Type.statusBars] /
     *                         [WindowInsetsCompat.Type.navigationBars] /
     *                         [WindowInsetsCompat.Type.systemBars]
     *                         等，支持位运算组合
     * @param onApplyInsets    自定义 insets 应用方式。默认 null = 叠加到 padding（保留原始值）
     *                         传入非 null 则完全接管 insets→View 的映射逻辑，
     *                         需返回 insets（通常直接返回参数即可）
     */
    data class Config(
        val edgeToEdge: Boolean = true,
        val insetsTypes: Int = WindowInsetsCompat.Type.systemBars(),
        val onApplyInsets: ((View, WindowInsetsCompat) -> WindowInsetsCompat)? = null,
    )

    private var edgeToEdgeApplied = false
    private var originalPadding: IntArray? = null

    // ==================== 公开 API ====================

    /**
     * 开启边到边显示。内部防重复调用。由 [Config.edgeToEdge] 控制是否实际执行。
     */
    fun useEnableEdgeToEdge() {
        if (!config.edgeToEdge || edgeToEdgeApplied) return
        edgeToEdgeApplied = true
        ownerActivity.enableEdgeToEdge()
    }

    /**
     * 为 Activity 的根 View 设置系统栏 insets。
     * 自动查找 `android.R.id.content` 的第一个子 View。
     */
    fun updateWindowInsets(activity: AppCompatActivity) {
        val rootView = findActivityRootView(activity)
        updateWindowInsets(rootView)
    }

    /**
     * 为任意 View 设置系统栏 insets。
     * - 如果 [Config.onApplyInsets] 不为 null，委托给自定义逻辑
     * - 否则使用默认行为：保存原始 padding + 叠加系统栏 insets
     */
    fun updateWindowInsets(rootView: View?) {
        if (rootView == null || !config.edgeToEdge) return

        val customHandler = config.onApplyInsets
        if (customHandler != null) {
            // 自定义模式：用户完全控制
            ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
                customHandler(v, insets)
            }
        } else {
            // 默认模式：叠加到 padding
            ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
                val bars = insets.getInsets(config.insetsTypes)
                val padding = originalPadding ?: intArrayOf(
                    v.paddingLeft, v.paddingTop, v.paddingRight, v.paddingBottom
                ).also { originalPadding = it }

                v.setPadding(
                    padding[0] + bars.left,
                    padding[1] + bars.top,
                    padding[2] + bars.right,
                    padding[3] + bars.bottom
                )
                insets
            }
        }
    }

    /**
     * 恢复原始 padding（如有保存）。仅在默认模式下有效。
     */
    fun restorePadding(view: View?) {
        view ?: return
        originalPadding?.let {
            view.setPadding(it[0], it[1], it[2], it[3])
        }
        originalPadding = null
    }

    /**
     * 查找 Activity 的 content 根 View（`android.R.id.content` 的第一个子 View）。
     */
    fun findActivityRootView(activity: Activity): View? {
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return null
        return if (content.childCount > 0) content.getChildAt(0) else null
    }
}
