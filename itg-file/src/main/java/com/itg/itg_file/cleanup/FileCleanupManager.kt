package com.itg.itg_file.cleanup

import android.app.Activity
import android.app.Application
import android.content.Context
import com.itg.itg_file.core.FileUtils
import com.itg.itg_thread_pools.executor.TaskExecutor
import com.itg.itg_thread_pools.manager.ThreadPoolManager
import java.lang.ref.WeakReference
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * 文件清理调度器。
 *
 * 设计目标：
 * - 通过 key-value 配置多条清理规则
 * - 支持应用冷启动时立即清理
 * - 支持延迟/定时清理
 * - 支持清空文件夹内容或直接删除目标
 * - 通过回调返回执行结果
 * - 线程安全，允许重复注册和取消
 */
@Suppress("unused")
object FileCleanupManager {

    enum class CleanupAction {
        CLEAR_DIRECTORY,
        DELETE_TARGET
    }

    sealed class CleanupTrigger {
        object OnAppStart : CleanupTrigger()
        object OnAppBackground : CleanupTrigger()
        data class AfterDelay(val delayMs: Long) : CleanupTrigger()
        data class AfterDays(val days: Long, val persistAcrossRestarts: Boolean = true) : CleanupTrigger()
        data class AtTimeMillis(val triggerAtMillis: Long) : CleanupTrigger()
    }

    data class CleanupRule(
        val key: String,
        val path: String,
        val action: CleanupAction = CleanupAction.CLEAR_DIRECTORY,
        val trigger: CleanupTrigger = CleanupTrigger.OnAppStart,
        val enabled: Boolean = true
    ) {
        init {
            require(key.isNotBlank()) { "key must not be blank" }
            require(path.isNotBlank()) { "path must not be blank" }
        }
    }

    data class CleanupConfig(
        val rules: Map<String, CleanupRule>
    ) {
        init {
            require(rules.isNotEmpty()) { "rules must not be empty" }
        }
    }

    data class CleanupResult(
        val key: String,
        val path: String,
        val action: CleanupAction,
        val trigger: CleanupTrigger,
        val success: Boolean,
        val deletedEntries: Int,
        val existedBefore: Boolean,
        val existedAfter: Boolean,
        val message: String,
        val startedAtMillis: Long,
        val finishedAtMillis: Long
    )

    class Builder {
        private val rules = LinkedHashMap<String, CleanupRule>()

        fun clearOnAppStart(key: String, path: String, enabled: Boolean = true): Builder {
            return put(
                CleanupRule(
                    key = key,
                    path = path,
                    action = CleanupAction.CLEAR_DIRECTORY,
                    trigger = CleanupTrigger.OnAppStart,
                    enabled = enabled
                )
            )
        }

        fun deleteOnAppStart(key: String, path: String, enabled: Boolean = true): Builder {
            return put(
                CleanupRule(
                    key = key,
                    path = path,
                    action = CleanupAction.DELETE_TARGET,
                    trigger = CleanupTrigger.OnAppStart,
                    enabled = enabled
                )
            )
        }

        fun clearOnAppBackground(key: String, path: String, enabled: Boolean = true): Builder {
            return put(
                CleanupRule(
                    key = key,
                    path = path,
                    action = CleanupAction.CLEAR_DIRECTORY,
                    trigger = CleanupTrigger.OnAppBackground,
                    enabled = enabled
                )
            )
        }

        fun deleteOnAppBackground(key: String, path: String, enabled: Boolean = true): Builder {
            return put(
                CleanupRule(
                    key = key,
                    path = path,
                    action = CleanupAction.DELETE_TARGET,
                    trigger = CleanupTrigger.OnAppBackground,
                    enabled = enabled
                )
            )
        }

        fun clearAfterDelay(key: String, path: String, delayMs: Long, enabled: Boolean = true): Builder {
            return put(
                CleanupRule(
                    key = key,
                    path = path,
                    action = CleanupAction.CLEAR_DIRECTORY,
                    trigger = CleanupTrigger.AfterDelay(delayMs),
                    enabled = enabled
                )
            )
        }

        fun clearAfterDays(
            key: String,
            path: String,
            days: Long,
            enabled: Boolean = true,
            persistAcrossRestarts: Boolean = true
        ): Builder {
            require(days >= 0L) { "days must be non-negative" }
            return put(
                CleanupRule(
                    key = key,
                    path = path,
                    action = CleanupAction.CLEAR_DIRECTORY,
                    trigger = CleanupTrigger.AfterDays(days, persistAcrossRestarts),
                    enabled = enabled
                )
            )
        }

        fun deleteAfterDelay(key: String, path: String, delayMs: Long, enabled: Boolean = true): Builder {
            return put(
                CleanupRule(
                    key = key,
                    path = path,
                    action = CleanupAction.DELETE_TARGET,
                    trigger = CleanupTrigger.AfterDelay(delayMs),
                    enabled = enabled
                )
            )
        }

        fun deleteAfterDays(
            key: String,
            path: String,
            days: Long,
            enabled: Boolean = true,
            persistAcrossRestarts: Boolean = true
        ): Builder {
            require(days >= 0L) { "days must be non-negative" }
            return put(
                CleanupRule(
                    key = key,
                    path = path,
                    action = CleanupAction.DELETE_TARGET,
                    trigger = CleanupTrigger.AfterDays(days, persistAcrossRestarts),
                    enabled = enabled
                )
            )
        }

        fun clearAtTime(key: String, path: String, triggerAtMillis: Long, enabled: Boolean = true): Builder {
            return put(
                CleanupRule(
                    key = key,
                    path = path,
                    action = CleanupAction.CLEAR_DIRECTORY,
                    trigger = CleanupTrigger.AtTimeMillis(triggerAtMillis),
                    enabled = enabled
                )
            )
        }

        fun deleteAtTime(key: String, path: String, triggerAtMillis: Long, enabled: Boolean = true): Builder {
            return put(
                CleanupRule(
                    key = key,
                    path = path,
                    action = CleanupAction.DELETE_TARGET,
                    trigger = CleanupTrigger.AtTimeMillis(triggerAtMillis),
                    enabled = enabled
                )
            )
        }

        fun put(rule: CleanupRule): Builder {
            rules[rule.key] = rule
            return this
        }

        fun build(): CleanupConfig {
            return CleanupConfig(rules.toMap())
        }
    }

    private val scheduledTasks = ConcurrentHashMap<String, Future<*>>()
    private val backgroundWatchers = ConcurrentHashMap<String, BackgroundWatcher>()
    @Volatile
    private var lastApplicationRef: WeakReference<Application>? = null

    private const val PERSISTED_PREFS_NAME = "itg_file_cleanup_manager"
    private const val KEY_PREFIX_DUE_AT = "cleanup_due_at_"
    private const val KEY_PREFIX_SIGNATURE = "cleanup_signature_"

    @JvmStatic
    fun builder(): Builder = Builder()

    @JvmStatic
    fun register(
        config: CleanupConfig,
        onResult: ((CleanupResult) -> Unit)? = null
    ): List<Future<*>> {
        val futures = mutableListOf<Future<*>>()
        config.rules.values.filter { it.enabled }.forEach { rule ->
            val future = when (val trigger = rule.trigger) {
                CleanupTrigger.OnAppStart -> TaskExecutor.io {
                    val result = executeRule(rule)
                    onResult?.invoke(result)
                    result
                }
                CleanupTrigger.OnAppBackground -> null
                is CleanupTrigger.AfterDelay -> {
                    scheduleRule(rule, trigger.delayMs, onResult)
                }
                is CleanupTrigger.AfterDays -> {
                    scheduleAfterDays(currentApplication(), rule, trigger, onResult)
                }
                is CleanupTrigger.AtTimeMillis -> {
                    val delay = (trigger.triggerAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)
                    scheduleRule(rule, delay, onResult)
                }
            }
            if (future != null) {
                replaceScheduled(rule.key, future)
                futures.add(future)
            }
        }
        return futures
    }

    @JvmStatic
    fun register(
        application: Application,
        config: CleanupConfig,
        onResult: ((CleanupResult) -> Unit)? = null
    ): List<Future<*>> {
        lastApplicationRef = WeakReference(application)
        val futures = mutableListOf<Future<*>>()
        config.rules.values.filter { it.enabled }.forEach { rule ->
            val future: Future<*>? = when (val trigger = rule.trigger) {
                CleanupTrigger.OnAppStart -> TaskExecutor.io {
                    val result = executeRule(rule)
                    onResult?.invoke(result)
                    result
                }
                CleanupTrigger.OnAppBackground -> null
                is CleanupTrigger.AfterDelay -> {
                    scheduleRule(rule, trigger.delayMs, onResult)
                }
                is CleanupTrigger.AfterDays -> {
                    scheduleAfterDays(application, rule, trigger, onResult)
                }
                is CleanupTrigger.AtTimeMillis -> {
                    val delay = (trigger.triggerAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)
                    scheduleRule(rule, delay, onResult)
                }
            }
            if (future != null) {
                replaceScheduled(rule.key, future)
                futures.add(future)
            }
        }
        if (config.rules.values.any { it.enabled && it.trigger is CleanupTrigger.OnAppBackground }) {
            installAppBackgroundWatcher(application, config, onResult)
        }
        return futures
    }

    @JvmStatic
    fun installAppBackgroundWatcher(
        application: Application,
        config: CleanupConfig,
        onResult: ((CleanupResult) -> Unit)? = null
    ): BackgroundWatcher {
        val watcher = BackgroundWatcher(application, config, onResult)
        watcher.install()
        backgroundWatchers[watcher.id] = watcher
        return watcher
    }

    @JvmStatic
    fun uninstallAppBackgroundWatcher(watcher: BackgroundWatcher) {
        watcher.uninstall()
        backgroundWatchers.remove(watcher.id)
    }

    @JvmStatic
    fun runNow(
        config: CleanupConfig,
        onResult: ((CleanupResult) -> Unit)? = null
    ): List<Future<*>> {
        val immediate = config.rules.values.filter { it.enabled }
        return immediate.map { rule ->
            val future = TaskExecutor.io {
                val result = executeRule(rule)
                onResult?.invoke(result)
                result
            }
            replaceScheduled(rule.key, future)
            future
        }
    }

    @JvmStatic
    fun cancel(key: String): Boolean {
        val future = scheduledTasks.remove(key) ?: return false
        clearPersistedAfterDaysState(key)
        return future.cancel(true)
    }

    @JvmStatic
    fun cancelAll(): Int {
        val count = scheduledTasks.size
        scheduledTasks.values.forEach { it.cancel(true) }
        scheduledTasks.clear()
        clearAllPersistedAfterDaysState()
        return count
    }

    @JvmStatic
    fun isScheduled(key: String): Boolean {
        val future = scheduledTasks[key] ?: return false
        return !future.isDone && !future.isCancelled
    }

    private fun scheduleRule(
        rule: CleanupRule,
        delayMs: Long,
        onResult: ((CleanupResult) -> Unit)?
    ): Future<*> {
        require(delayMs >= 0L) { "delayMs must be non-negative" }
        return ThreadPoolManager.scheduledPool.schedule({
            val result = executeRule(rule)
            onResult?.invoke(result)
            result
        }, delayMs, TimeUnit.MILLISECONDS)
    }

    private fun replaceScheduled(key: String, future: Future<*>) {
        scheduledTasks.put(key, future)?.cancel(true)
    }

    private fun currentApplication(): Application? = lastApplicationRef?.get()

    private fun scheduleAfterDays(
        application: Application?,
        rule: CleanupRule,
        trigger: CleanupTrigger.AfterDays,
        onResult: ((CleanupResult) -> Unit)?
    ): Future<*> {
        val delayMs = TimeUnit.DAYS.toMillis(trigger.days)
        if (!trigger.persistAcrossRestarts || application == null) {
            return scheduleRule(rule, delayMs, onResult)
        }

        val prefs = application.getSharedPreferences(PERSISTED_PREFS_NAME, Context.MODE_PRIVATE)
        val signature = buildPersistentSignature(rule, trigger)
        val dueAtKey = persistedDueAtKey(rule.key)
        val signatureKey = persistedSignatureKey(rule.key)
        val now = System.currentTimeMillis()
        val storedSignature = prefs.getString(signatureKey, null)

        val dueAtMillis = if (storedSignature == signature) {
            prefs.getLong(dueAtKey, -1L).takeIf { it > 0L } ?: (now + delayMs)
        } else {
            now + delayMs
        }

        prefs.edit()
            .putLong(dueAtKey, dueAtMillis)
            .putString(signatureKey, signature)
            .apply()

        val remainingDelay = (dueAtMillis - now).coerceAtLeast(0L)
        return scheduleRule(rule, remainingDelay) { result ->
            try {
                onResult?.invoke(result)
            } finally {
                clearPersistedAfterDaysState(rule.key)
            }
        }
    }

    private fun clearPersistedAfterDaysState(key: String) {
        val application = lastApplicationRef?.get() ?: return
        val prefs = application.getSharedPreferences(PERSISTED_PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(persistedDueAtKey(key))
            .remove(persistedSignatureKey(key))
            .apply()
    }

    private fun clearAllPersistedAfterDaysState() {
        val application = lastApplicationRef?.get() ?: return
        val prefs = application.getSharedPreferences(PERSISTED_PREFS_NAME, Context.MODE_PRIVATE)
        val edits = prefs.edit()
        prefs.all.keys
            .filter { it.startsWith(KEY_PREFIX_DUE_AT) || it.startsWith(KEY_PREFIX_SIGNATURE) }
            .forEach { edits.remove(it) }
        edits.apply()
    }

    private fun persistedDueAtKey(key: String): String = KEY_PREFIX_DUE_AT + key

    private fun persistedSignatureKey(key: String): String = KEY_PREFIX_SIGNATURE + key

    private fun buildPersistentSignature(rule: CleanupRule, trigger: CleanupTrigger.AfterDays): String {
        return buildString {
            append(rule.key)
            append('|')
            append(rule.path)
            append('|')
            append(rule.action.name)
            append('|')
            append(trigger.days)
            append('|')
            append(trigger.persistAcrossRestarts)
        }
    }

    private fun executeRule(rule: CleanupRule): CleanupResult {
        val startedAt = System.currentTimeMillis()
        val target = File(rule.path)
        val existedBefore = target.exists()
        val deletedEntries = when (rule.action) {
            CleanupAction.CLEAR_DIRECTORY -> if (existedBefore && target.isDirectory) {
                target.listFiles()?.size ?: 0
            } else {
                0
            }
            CleanupAction.DELETE_TARGET -> if (existedBefore) 1 else 0
        }

        val success = when (rule.action) {
            CleanupAction.CLEAR_DIRECTORY -> FileUtils.clearDirectory(rule.path)
            CleanupAction.DELETE_TARGET -> FileUtils.delete(rule.path)
        }
        val existedAfter = target.exists()
        val finishedAt = System.currentTimeMillis()
        val message = buildMessage(rule, existedBefore, existedAfter, success)

        return CleanupResult(
            key = rule.key,
            path = rule.path,
            action = rule.action,
            trigger = rule.trigger,
            success = success,
            deletedEntries = deletedEntries,
            existedBefore = existedBefore,
            existedAfter = existedAfter,
            message = message,
            startedAtMillis = startedAt,
            finishedAtMillis = finishedAt
        )
    }

    private fun buildMessage(
        rule: CleanupRule,
        existedBefore: Boolean,
        existedAfter: Boolean,
        success: Boolean
    ): String {
        return if (success) {
            when (rule.action) {
                CleanupAction.CLEAR_DIRECTORY -> if (existedBefore) {
                    "Cleared directory contents: ${rule.path}"
                } else {
                    "Directory did not exist: ${rule.path}"
                }
                CleanupAction.DELETE_TARGET -> if (existedBefore && !existedAfter) {
                    "Deleted target: ${rule.path}"
                } else if (!existedBefore) {
                    "Target did not exist: ${rule.path}"
                } else {
                    "Deletion reported success but target still exists: ${rule.path}"
                }
            }
        } else {
            when (rule.action) {
                CleanupAction.CLEAR_DIRECTORY -> "Failed to clear directory: ${rule.path}"
                CleanupAction.DELETE_TARGET -> "Failed to delete target: ${rule.path}"
            }
        }
    }

    class BackgroundWatcher internal constructor(
        application: Application,
        private val config: CleanupConfig,
        private val onResult: ((CleanupResult) -> Unit)?
    ) : Application.ActivityLifecycleCallbacks {

        internal val id: String = "bg-${System.identityHashCode(this)}"
        private val appRef = WeakReference(application)
        private val startedCount = AtomicInteger(0)
        private val foreground = AtomicBoolean(false)
        @Volatile
        private var installed = false

        fun install() {
            if (installed) return
            installed = true
            appRef.get()?.registerActivityLifecycleCallbacks(this)
        }

        fun uninstall() {
            val app = appRef.get() ?: return
            if (!installed) return
            installed = false
            app.unregisterActivityLifecycleCallbacks(this)
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: android.os.Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) {
            startedCount.incrementAndGet()
            foreground.set(true)
        }

        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: android.os.Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit

        override fun onActivityStopped(activity: Activity) {
            val remaining = startedCount.decrementAndGet().coerceAtLeast(0)
            if (remaining == 0 && foreground.compareAndSet(true, false)) {
                triggerBackgroundCleanup()
            }
        }

        private fun triggerBackgroundCleanup() {
            config.rules.values
                .filter { it.enabled && it.trigger is CleanupTrigger.OnAppBackground }
                .forEach { rule ->
                    TaskExecutor.io {
                        onResult?.invoke(executeRule(rule))
                    }
                }
        }
    }

}
