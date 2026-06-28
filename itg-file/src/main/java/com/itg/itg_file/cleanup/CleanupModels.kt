package com.itg.itg_file.cleanup

import java.util.concurrent.atomic.AtomicBoolean

/**
 * 清理动作。
 */
enum class CleanupAction {
    /** 删除目录内的全部内容，但保留目录本身。 */
    CLEAR_DIRECTORY,

    /** 删除目标文件或整个目录。 */
    DELETE_TARGET
}

/**
 * 时间触发规则执行完成后的调度策略。
 */
enum class CleanupScheduleMode {
    /** 只执行一次，完成后释放任务和持久化计时资源。 */
    ONE_SHOT,

    /** 执行完成后使用原延迟或天数重新计时。 */
    RESTART_AFTER_EXECUTION
}

/** Fragment 注册清理任务时使用的宿主生命周期。 */
enum class CleanupLifecycleScope {
    ACTIVITY,
    FRAGMENT
}

/**
 * 清理触发条件。
 */
sealed class CleanupTrigger {
    data object OnAppStart : CleanupTrigger()
    data object OnAppBackground : CleanupTrigger()
    data class AfterDelay(
        val delayMs: Long,
        val persistAcrossRestarts: Boolean = false
    ) : CleanupTrigger()
    data class AfterDays(
        val days: Int,
        val persistAcrossRestarts: Boolean
    ) : CleanupTrigger()

    data class AtTimeMillis(val triggerAtMillis: Long) : CleanupTrigger()
}

/**
 * 单条文件清理规则。
 */
data class CleanupRule(
    val key: String,
    val path: String,
    val action: CleanupAction,
    val trigger: CleanupTrigger,
    val scheduleMode: CleanupScheduleMode = CleanupScheduleMode.ONE_SHOT,
    val enabled: Boolean = true
)

/**
 * 一组文件清理规则。
 */
class CleanupConfig internal constructor(
    val rules: List<CleanupRule>
)

/**
 * 单条规则的执行结果。
 */
data class CleanupResult(
    val key: String,
    val path: String,
    val action: CleanupAction,
    val success: Boolean,
    val deletedEntries: Int,
    val existedBefore: Boolean,
    val existedAfter: Boolean,
    val message: String,
    val error: Throwable? = null
)

/**
 * 清理过程回调。权限申请由宿主应用负责。
 */
class CleanupCallbacks @JvmOverloads constructor(
    val onResult: (CleanupResult) -> Unit = {},
    val onPermissionRequired: ((CleanupPermissionRequest) -> Unit)? = null
)

/**
 * 清理因权限不足暂停时发送给宿主应用的请求。
 */
class CleanupPermissionRequest internal constructor(
    val key: String,
    val path: String,
    val suggestedPermissions: List<String>,
    val requiresSpecialSettings: Boolean,
    val reason: String,
    private val retryAction: () -> Boolean,
    private val cancelAction: () -> Boolean
) {
    private val resolved = AtomicBoolean(false)

    /** 授权成功后重新执行原清理规则。每个请求最多调用一次。 */
    fun retry(): Boolean {
        return resolved.compareAndSet(false, true) && retryAction()
    }

    /** 放弃授权并把原权限失败结果发送到 onResult。 */
    fun cancel(): Boolean {
        return resolved.compareAndSet(false, true) && cancelAction()
    }
}
