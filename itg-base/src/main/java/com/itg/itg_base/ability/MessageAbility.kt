package com.example.itg_base.ability

import android.view.View
import android.widget.Toast
import com.google.android.material.snackbar.Snackbar

/**
 * 消息提示能力：Toast 和 Snackbar，生命周期感知（Activity 销毁后不弹）。
 *
 * `snackbar()` 依赖 `com.google.android.material:material`，
 * 如果 classpath 上没有该库，将自动退化为 Toast。
 *
 * 使用：
 * ```
 * messages.toast("操作成功")
 * messages.snackbar("已删除", "撤销") { undoDelete() }
 * ```
 */
class MessageAbility : LifeAbility() {

    fun toast(message: String) {
        if (!isActivityAlive()) return
        Toast.makeText(ownerActivity, message, Toast.LENGTH_SHORT).show()
    }

    fun toastLong(message: String) {
        if (!isActivityAlive()) return
        Toast.makeText(ownerActivity, message, Toast.LENGTH_LONG).show()
    }

    fun snackbar(message: String) {
        val anchor = findAnchorView()
        if (anchor == null) { toast(message); return }
        try {
            showSnackbar(anchor, message, null, null)
        } catch (_: NoClassDefFoundError) {
            toast(message)
        }
    }

    fun snackbarAction(message: String, actionText: String, action: () -> Unit) {
        val anchor = findAnchorView()
        if (anchor == null) { toast(message); return }
        try {
            showSnackbar(anchor, message, actionText, action)
        } catch (_: NoClassDefFoundError) {
            toast("$message [$actionText]")
        }
    }

    // ==================== 内部（Snackbar 引用放在独立方法，延迟类加载） ====================

    /**
     * 实际调用 Snackbar API。独立方法确保 Snackbar 类的加载发生在 try 块内。
     */
    private fun showSnackbar(
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
