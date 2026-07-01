package com.itg.itg_base.ability

import android.content.Context
import android.view.View
import android.widget.Toast
import com.google.android.material.snackbar.Snackbar

/**
 * 消息提示能力：Toast 和 Snackbar，生命周期感知（Activity 销毁后不弹）。
 *
 * ## 默认使用
 * ```
 * messages.toast("操作成功")
 * messages.snackbar("已删除", "撤销") { undoDelete() }
 * ```
 *
 * ## 自定义（Config）
 * ```
 * MessageAbility(
 *     MessageAbility.Config(
 *         onToast = { ctx, msg, _ -> MyToast.show(ctx, msg) },
 *         onSnackbar = { anchor, msg, actionText, action ->
 *             showTopBanner(msg, actionText, action)
 *         },
 *     )
 * )
 * ```
 *
 * ## 自定义（子类覆写）
 * ```
 * object : MessageAbility() {
 *     override fun showToast(context: Context, message: String, duration: Int) { ... }
 *     override fun showSnackbar(anchor: View, message: String, actionText: String?, action: (() -> Unit)?) { ... }
 * }
 * ```
 */
class MessageAbility(
    private val config: Config = Config()
) : LifeAbility() {

    /**
     * @param onToast    替换 Toast。null = 使用系统 Toast
     * @param onSnackbar 替换 Snackbar。null = 使用 Material Snackbar（缺则退化为 Toast）
     */
    data class Config(
        val onToast: ((Context, String, Int) -> Unit)? = null,
        val onSnackbar: ((View, String, String?, (() -> Unit)?) -> Unit)? = null,
    )

    fun toast(message: String) {
        if (!isActivityAlive()) return
        showToast(ownerActivity, message, Toast.LENGTH_SHORT)
    }

    fun toastLong(message: String) {
        if (!isActivityAlive()) return
        showToast(ownerActivity, message, Toast.LENGTH_LONG)
    }

    fun snackbar(message: String) {
        val anchor = findAnchorView()
        if (anchor == null) { toast(message); return }
        showSnackbar(anchor, message, null, null)
    }

    fun snackbarAction(message: String, actionText: String, action: () -> Unit) {
        val anchor = findAnchorView()
        if (anchor == null) { toast(message); return }
        showSnackbar(anchor, message, actionText, action)
    }

    // ==================== 可覆写的方法 ====================

    /** 优先级：Config.onToast > 覆写 > 系统 Toast */
    protected open fun showToast(context: Context, message: String, duration: Int) {
        config.onToast?.let { it(context, message, duration); return }
        Toast.makeText(context, message, duration).show()
    }

    /**
     * 优先级：Config.onSnackbar > 覆写 > Material Snackbar（缺则退化为 Toast）。
     * [actionText] 和 [action] 同时为 null 时仅显示消息。
     */
    protected open fun showSnackbar(
        anchor: View,
        message: String,
        actionText: String?,
        action: (() -> Unit)?
    ) {
        config.onSnackbar?.let { it(anchor, message, actionText, action); return }
        try {
            showMaterialSnackbar(anchor, message, actionText, action)
        } catch (_: NoClassDefFoundError) {
            val fullMsg = if (actionText != null) "$message [$actionText]" else message
            Toast.makeText(anchor.context, fullMsg, Toast.LENGTH_SHORT).show()
        }
    }

    // ==================== 内部 ====================

    /** 隔离 Material Snackbar 引用，延迟类加载到 try 块内 */
    private fun showMaterialSnackbar(
        anchor: View,
        message: String,
        actionText: String?,
        action: (() -> Unit)?
    ) {
        val sb = Snackbar.make(anchor, message, Snackbar.LENGTH_SHORT)
        if (actionText != null && action != null) {
            sb.setAction(actionText) { action() }
        }
        sb.show()
    }

    private fun findAnchorView(): View? {
        if (!isActivityAlive()) return null
        return ownerActivity.window?.decorView?.findViewById(android.R.id.content)
    }
}
