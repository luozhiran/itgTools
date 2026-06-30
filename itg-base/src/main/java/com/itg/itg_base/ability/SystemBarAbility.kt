package com.example.itg_base.ability

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * 边到边 + 系统栏 insets 能力。
 *
 * 使用：
 * ```
 * systemBars.inject(this)
 * systemBars.useEnableEdgeToEdge()         // 安全：内部防重复
 * systemBars.updateWindowInsets(this)       // 必须在 setContentView 后
 * ```
 */
class SystemBarAbility : LifeAbility() {

    private var edgeToEdgeEnabled = false
    private var originalPadding: IntArray? = null

    /**
     * 开启边到边显示。内部防重复调用。
     */
    fun useEnableEdgeToEdge() {
        if (edgeToEdgeEnabled) return
        edgeToEdgeEnabled = true
        ownerActivity.enableEdgeToEdge()
    }

    /**
     * 为 Activity 的根 View 设置系统栏 insets padding。
     * 会自动查找 `android.R.id.content` 的第一个子 View。
     */
    fun updateWindowInsets(activity: AppCompatActivity) {
        val rootView = findActivityRootView(activity)
        updateWindowInsets(rootView)
    }

    /**
     * 为任意 View 设置系统栏 insets padding。
     * 保存原始 padding，后续 restore 时可恢复。
     */
    fun updateWindowInsets(rootView: View?) {
        if (rootView == null || !edgeToEdgeEnabled) return
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val padding = originalPadding ?: intArrayOf(
                v.paddingLeft, v.paddingTop, v.paddingRight, v.paddingBottom
            ).also { originalPadding = it }

            v.setPadding(
                padding[0] + systemBars.left,
                padding[1] + systemBars.top,
                padding[2] + systemBars.right,
                padding[3] + systemBars.bottom
            )
            insets
        }
    }

    /**
     * 恢复原始 padding（如有保存）。
     */
    fun restorePadding(view: View?) {
        view ?: return
        originalPadding?.let {
            view.setPadding(it[0], it[1], it[2], it[3])
        }
        originalPadding = null
    }

    /**
     * 查找 Activity 的 content 根 View。
     */
    fun findActivityRootView(activity: Activity): View? {
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return null
        return if (content.childCount > 0) content.getChildAt(0) else null
    }
}
