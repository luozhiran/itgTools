package com.example.itg_base.ability

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * 运行时权限请求能力。
 *
 * 使用：
 * ```
 * permissions.request(Manifest.permission.CAMERA) { granted ->
 *     if (granted) openCamera() else showDeniedHint()
 * }
 * permissions.requestMultiple(
 *     Manifest.permission.CAMERA,
 *     Manifest.permission.RECORD_AUDIO
 * ) { results -> ... }
 * ```
 */
class PermissionAbility : LifeAbility() {

    private var singleCallback: ((Boolean) -> Unit)? = null
    private var multiCallback: ((Map<String, Boolean>) -> Unit)? = null

    private lateinit var singleLauncher: ActivityResultLauncher<String>
    private lateinit var multiLauncher: ActivityResultLauncher<Array<String>>

    override fun inject(activity: AppCompatActivity): PermissionAbility {
        super.inject(activity)
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

    /**
     * 请求单个权限。
     */
    fun request(permission: String, callback: (granted: Boolean) -> Unit) {
        singleCallback = callback
        singleLauncher.launch(permission)
    }

    /**
     * 请求多个权限。
     */
    fun requestMultiple(
        vararg permissions: String,
        callback: (results: Map<String, Boolean>) -> Unit
    ) {
        multiCallback = callback
        multiLauncher.launch(permissions.toList().toTypedArray())
    }

    // ==================== 工具方法 ====================

    /**
     * 检查权限是否已授予。
     */
    fun isGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            ownerActivity, permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 是否应该展示权限解释说明（用户拒绝过一次但未勾选"不再询问"）。
     */
    fun shouldShowRationale(permission: String): Boolean {
        return ownerActivity.shouldShowRequestPermissionRationale(permission)
    }
}
