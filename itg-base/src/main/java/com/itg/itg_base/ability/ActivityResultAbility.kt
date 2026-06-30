package com.example.itg_base.ability

import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/**
 * Activity Result 能力组件，封装常见的启动-回调场景。
 *
 * 注册时机：inject() 在 Activity.onCreate() 内调用，早于 STARTED 状态，
 *          满足 registerForActivityResult 必须在 STARTED 前注册的要求。
 *
 * 使用示例：
 * ```
 * class MainActivity : AutoBindingBaseActivity<MyModel>() {
 *     private val resultAbility: ActivityResultAbility by lazy { ActivityResultAbility() }
 *
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *         resultAbility.inject(this)
 *
 *         // 选择图片
 *         resultAbility.pickImage { uri -> /* 更新 UI */ }
 *
 *         // 请求权限
 *         resultAbility.requestPermission(Manifest.permission.CAMERA) { granted -> /* ... */ }
 *
 *         // 跳转等结果
 *         resultAbility.startForResult(Intent(this, OtherActivity::class.java)) { code, data -> /* ... */ }
 *     }
 * }
 * ```
 */
class ActivityResultAbility : LifeAbility() {

    // ==================== 回调暂存（每次 launch 前写入，结果回来时消费） ====================

    private var activityResultCallback: ((Int, Intent?) -> Unit)? = null
    private var imagePickCallback: ((Uri?) -> Unit)? = null
    private var permissionCallback: ((Boolean) -> Unit)? = null
    private var photoCallback: ((Boolean) -> Unit)? = null

    // ==================== Launcher 实例（inject 时注册） ====================

    private lateinit var activityLauncher: ActivityResultLauncher<Intent>
    private lateinit var imagePickerLauncher: ActivityResultLauncher<String>
    private lateinit var permissionLauncher: ActivityResultLauncher<String>
    private lateinit var photoLauncher: ActivityResultLauncher<Uri>

    // ==================== 注入 & 注册 ====================

    override fun inject(activity: AppCompatActivity): ActivityResultAbility {
        super.inject(activity)
        registerLaunchers()
        return this
    }

    /**
     * 必须在 Activity STARTED 之前完成注册。
     * inject() 在 onCreate() 内调用，此时 lifecycle 尚处于 CREATED 状态，满足条件。
     */
    private fun registerLaunchers() {
        activityLauncher = ownerActivity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val callback = activityResultCallback
            activityResultCallback = null   // 消费后清空，避免泄漏
            callback?.invoke(result.resultCode, result.data)
        }

        imagePickerLauncher = ownerActivity.registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            val callback = imagePickCallback
            imagePickCallback = null
            callback?.invoke(uri)
        }

        permissionLauncher = ownerActivity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            val callback = permissionCallback
            permissionCallback = null
            callback?.invoke(granted)
        }

        photoLauncher = ownerActivity.registerForActivityResult(
            ActivityResultContracts.TakePicture()
        ) { success ->
            val callback = photoCallback
            photoCallback = null
            callback?.invoke(success)
        }
    }

    // ==================== 公开 API ====================

    /**
     * 启动 Activity 并等待结果返回。
     *
     * @param intent   要启动的 Intent
     * @param callback 结果回调：resultCode → Intent(data)?
     */
    fun startForResult(intent: Intent, callback: (resultCode: Int, data: Intent?) -> Unit) {
        activityResultCallback = callback
        activityLauncher.launch(intent)
    }


    /**
     * 打开系统文件选择器选择图片。
     *
     * @param mimeType 筛选类型，默认 "image‘/’*"
     * @param callback 选中文件的 Uri，取消则为 null
     */
    fun pickImage(mimeType: String = "image/*", callback: (uri: Uri?) -> Unit) {
        imagePickCallback = callback
        imagePickerLauncher.launch(mimeType)
    }

    /**
     * 请求单个运行时权限。
     *
     * @param permission 权限字符串，如 Manifest.permission.CAMERA
     * @param callback   true=授权，false=拒绝
     */
    fun requestPermission(permission: String, callback: (granted: Boolean) -> Unit) {
        permissionCallback = callback
        permissionLauncher.launch(permission)
    }

    /**
     * 调用系统相机拍照。
     *
     * @param uri      照片保存路径（需通过 FileProvider 生成 content:// URI）
     * @param callback true=拍照成功并保存，false=取消或失败
     */
    fun takePhoto(uri: Uri, callback: (success: Boolean) -> Unit) {
        photoCallback = callback
        photoLauncher.launch(uri)
    }
}
