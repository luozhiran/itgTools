package com.itg.itg_base.ability

import android.content.pm.PackageManager
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle

/**
 * 运行时权限请求能力。
 *
 * ## 默认使用
 * ```
 * permissions.request(Manifest.permission.CAMERA) { granted -> ... }
 * permissions.requestMultiple(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO) { results -> ... }
 * permissions.isGranted(Manifest.permission.CAMERA)
 * permissions.shouldShowRationale(Manifest.permission.CAMERA)
 * ```
 *
 * ## 与 Fragment 共用
 * Fragment 中推荐复用 Activity 的实例，避免重复注册：
 * ```
 * override val permissions: PermissionAbility
 *     get() = (requireActivity() as YourActivity).permissions
 * ```
 *
 * ## 自定义（Config）
 * ```
 * PermissionAbility(
 *     PermissionAbility.Config(
 *         onPermanentlyDenied = { perm -> showGoToSettingsDialog(perm) }
 *     )
 * )
 * ```
 */
class PermissionAbility(
    private val config: Config = Config()
) : LifeAbility() {

    /**
     * @param onPermanentlyDenied 权限被永久拒绝时的回调（用户勾选"不再询问"后再次请求，
     *                            系统返回 denied=true 且 shouldShowRationale=false）。
     *                            参数为被拒绝的权限名。null = 不做特殊处理。
     */
    data class Config(
        val onPermanentlyDenied: ((String) -> Unit)? = null,
    )

    private var pendingPermission: String? = null
    private var singleCallback: ((Boolean) -> Unit)? = null
    private var multiCallback: ((Map<String, Boolean>) -> Unit)? = null

    private var singleLauncher: ActivityResultLauncher<String>? = null
    private var multiLauncher: ActivityResultLauncher<Array<String>>? = null

    override fun inject(activity: AppCompatActivity): PermissionAbility {
        super.inject(activity)
        // 仅在 Activity 尚未 STARTED 时注册 launcher。
        // ViewPager2 预创建 Fragment 时 Activity 已 RESUMED，
        // 此时 Fragment 应复用 Activity 的 PermissionAbility 实例，而非重新 inject。
        if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            return this
        }
        registerLaunchers()
        return this
    }

    private fun registerLaunchers() {
        singleLauncher = ownerActivity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            val cb = singleCallback
            singleCallback = null
            cb?.invoke(granted)

            val perm = pendingPermission
            pendingPermission = null
            if (!granted && perm != null && !shouldShowRationale(perm)) {
                config.onPermanentlyDenied?.invoke(perm)
            }
        }

        multiLauncher = ownerActivity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { results ->
            val cb = multiCallback
            multiCallback = null
            cb?.invoke(results)
        }
    }

    // ==================== 公开 API ====================

    fun request(permission: String, callback: (granted: Boolean) -> Unit) {
        val launcher = singleLauncher
        if (launcher == null) {
            callback(false)
            return
        }
        pendingPermission = permission
        singleCallback = callback
        launcher.launch(permission)
    }

    fun requestMultiple(
        vararg permissions: String,
        callback: (results: Map<String, Boolean>) -> Unit
    ) {
        val launcher = multiLauncher
        if (launcher == null) {
            callback(permissions.associateWith { false })
            return
        }
        multiCallback = callback
        launcher.launch(permissions.toList().toTypedArray())
    }

    fun isGranted(permission: String): Boolean {
        if (!isActivityAlive()) return false
        return ContextCompat.checkSelfPermission(
            ownerActivity, permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun shouldShowRationale(permission: String): Boolean {
        if (!isActivityAlive()) return false
        return ownerActivity.shouldShowRequestPermissionRationale(permission)
    }
}
