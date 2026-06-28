package com.itg.itg_file.cleanup

import android.app.Activity
import android.app.Application
import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.itg.itg_thread_pools.executor.TaskExecutor
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * 按应用生命周期或时间条件调度本地文件清理。
 *
 * 回调在 I/O 线程执行；更新 UI 时请切换到主线程。
 */
object FileCleanupManager {

    private const val PREFS_NAME = "itg_file_cleanup"
    private const val DEADLINE_PREFIX = "deadline_"
    private val immediateTasks = ConcurrentHashMap<String, Future<*>>()
    private val immediateRules = ConcurrentHashMap<String, RegisteredRule>()
    private val scheduledTasks = ConcurrentHashMap<String, Future<*>>()
    private val activeTimedRules = ConcurrentHashMap<String, RegisteredRule>()
    private val backgroundRules = ConcurrentHashMap<String, RegisteredRule>()
    private val runningBackgroundRules = ConcurrentHashMap.newKeySet<String>()
    private val pendingPermissionRules = ConcurrentHashMap<String, PendingPermissionRule>()
    private val activityRuleKeys = WeakHashMap<Activity, MutableSet<String>>()
    private val fragmentRuleKeys = WeakHashMap<Fragment, MutableSet<String>>()
    private val fragmentObservers = WeakHashMap<Fragment, DefaultLifecycleObserver>()

    @Volatile
    private var application: Application? = null

    @Volatile
    private var lifecycleRegistered = false

    @JvmStatic
    fun builder(): Builder = Builder()

    /**
     * 注册不依赖应用后台事件的规则。
     *
     * 需要后台触发或跨重启按天计时，应使用 [register] 的 Application 重载。
     */
    @JvmStatic
    @JvmOverloads
    fun register(
        config: CleanupConfig,
        onResult: (CleanupResult) -> Unit = {}
    ) {
        registerInternal(null, config, CleanupCallbacks(onResult))
    }

    @JvmStatic
    fun register(config: CleanupConfig, callbacks: CleanupCallbacks) {
        registerInternal(null, config, callbacks)
    }

    /**
     * 注册全部规则，并启用应用切到后台和跨重启计时能力。
     */
    @JvmStatic
    @JvmOverloads
    fun register(
        application: Application,
        config: CleanupConfig,
        onResult: (CleanupResult) -> Unit = {}
    ) {
        register(application, config, CleanupCallbacks(onResult))
    }

    @JvmStatic
    fun register(
        application: Application,
        config: CleanupConfig,
        callbacks: CleanupCallbacks
    ) {
        this.application = application
        ensureLifecycleCallbacks(application)
        registerInternal(application, config, callbacks)
    }

    /**
     * 将清理规则绑定到 Activity。Activity 销毁时自动取消并释放全部关联资源。
     */
    @JvmStatic
    @JvmOverloads
    fun register(
        activity: Activity,
        config: CleanupConfig,
        onResult: (CleanupResult) -> Unit = {}
    ) {
        register(activity, config, CleanupCallbacks(onResult))
    }

    @JvmStatic
    fun register(
        activity: Activity,
        config: CleanupConfig,
        callbacks: CleanupCallbacks
    ) {
        require(!activity.isDestroyed) { "Activity 已销毁，不能注册清理任务" }
        val app = activity.application
        application = app
        ensureLifecycleCallbacks(app)
        registerInternal(app, config, callbacks, activity)
    }

    /**
     * 将规则绑定到 Fragment 或其宿主 Activity 的生命周期。
     */
    @JvmStatic
    @JvmOverloads
    fun register(
        fragment: Fragment,
        config: CleanupConfig,
        lifecycleScope: CleanupLifecycleScope = CleanupLifecycleScope.FRAGMENT,
        onResult: (CleanupResult) -> Unit = {}
    ) {
        register(fragment, config, lifecycleScope, CleanupCallbacks(onResult))
    }

    @JvmStatic
    fun register(
        fragment: Fragment,
        config: CleanupConfig,
        lifecycleScope: CleanupLifecycleScope,
        callbacks: CleanupCallbacks
    ) {
        val activity = fragment.requireActivity()
        if (lifecycleScope == CleanupLifecycleScope.ACTIVITY) {
            register(activity, config, callbacks)
            return
        }
        require(fragment.lifecycle.currentState != Lifecycle.State.DESTROYED) {
            "Fragment 已销毁，不能注册清理任务"
        }
        val app = activity.application
        application = app
        ensureLifecycleCallbacks(app)
        if (config.rules.any { it.enabled }) ensureFragmentObserver(fragment)
        registerInternal(app, config, callbacks, ownerFragment = fragment)
    }

    /**
     * 忽略触发条件，立即在 I/O 线程执行全部已启用规则。
     */
    @JvmStatic
    @JvmOverloads
    fun runNow(
        config: CleanupConfig,
        onResult: (CleanupResult) -> Unit = {}
    ): List<Future<*>> = runNow(config, CleanupCallbacks(onResult))

    @JvmStatic
    fun runNow(
        config: CleanupConfig,
        callbacks: CleanupCallbacks
    ): List<Future<*>> = runNowInternal(config, callbacks)

    /** 立即执行并绑定 Activity；执行完成或 Activity 销毁后释放资源。 */
    @JvmStatic
    @JvmOverloads
    fun runNow(
        activity: Activity,
        config: CleanupConfig,
        onResult: (CleanupResult) -> Unit = {}
    ): List<Future<*>> = runNow(activity, config, CleanupCallbacks(onResult))

    @JvmStatic
    fun runNow(
        activity: Activity,
        config: CleanupConfig,
        callbacks: CleanupCallbacks
    ): List<Future<*>> {
        require(!activity.isDestroyed) { "Activity 已销毁，不能执行清理任务" }
        application = activity.application
        ensureLifecycleCallbacks(activity.application)
        return runNowInternal(config, callbacks, ownerActivity = activity)
    }

    /** 立即执行并绑定指定的 Fragment 或 Activity 生命周期。 */
    @JvmStatic
    @JvmOverloads
    fun runNow(
        fragment: Fragment,
        config: CleanupConfig,
        lifecycleScope: CleanupLifecycleScope = CleanupLifecycleScope.FRAGMENT,
        onResult: (CleanupResult) -> Unit = {}
    ): List<Future<*>> = runNow(
        fragment,
        config,
        lifecycleScope,
        CleanupCallbacks(onResult)
    )

    @JvmStatic
    fun runNow(
        fragment: Fragment,
        config: CleanupConfig,
        lifecycleScope: CleanupLifecycleScope,
        callbacks: CleanupCallbacks
    ): List<Future<*>> {
        val activity = fragment.requireActivity()
        if (lifecycleScope == CleanupLifecycleScope.ACTIVITY) {
            return runNow(activity, config, callbacks)
        }
        require(fragment.lifecycle.currentState != Lifecycle.State.DESTROYED) {
            "Fragment 已销毁，不能执行清理任务"
        }
        application = activity.application
        ensureLifecycleCallbacks(activity.application)
        if (config.rules.any { it.enabled }) ensureFragmentObserver(fragment)
        return runNowInternal(config, callbacks, ownerFragment = fragment)
    }

    private fun runNowInternal(
        config: CleanupConfig,
        callbacks: CleanupCallbacks,
        ownerActivity: Activity? = null,
        ownerFragment: Fragment? = null
    ): List<Future<*>> {
        return config.rules.filter { it.enabled }.map { rule ->
            cancelInternal(rule.key, clearPersistedDeadline = true)
            ownerActivity?.let { attachActivityRule(it, rule.key) }
            ownerFragment?.let { attachFragmentRule(it, rule.key) }
            val registered = RegisteredRule(
                rule = rule,
                callbacks = callbacks,
                ownerActivity = ownerActivity?.let(::WeakReference),
                ownerFragment = ownerFragment?.let(::WeakReference)
            )
            submit(registered) { releaseRegistration(registered) }
        }
    }

    /**
     * 取消尚未执行的单条规则。
     */
    @JvmStatic
    fun cancel(key: String): Boolean {
        return cancelInternal(key, clearPersistedDeadline = true)
    }

    private fun cancelInternal(key: String, clearPersistedDeadline: Boolean): Boolean {
        val immediateRule = immediateRules.remove(key)
        val immediateCancelled = immediateTasks.remove(key)?.cancel(true) ?: false
        val futureCancelled = scheduledTasks.remove(key)?.cancel(true) ?: false
        val timedRule = activeTimedRules.remove(key)
        val backgroundRule = backgroundRules.remove(key)
        runningBackgroundRules.remove(key)
        val pendingPermission = pendingPermissionRules.remove(key)
        listOfNotNull(
            immediateRule,
            timedRule,
            backgroundRule,
            pendingPermission?.registered
        ).forEach { it.released.set(true) }
        pendingPermission?.onTerminal?.invoke()
        if (clearPersistedDeadline) {
            clearPersistedDeadline(key)
        }
        detachOwnerRule(key)
        return immediateCancelled || futureCancelled || immediateRule != null || timedRule != null ||
            backgroundRule != null || pendingPermission != null
    }

    /**
     * 取消全部尚未执行的规则。
     */
    @JvmStatic
    fun cancelAll() {
        val registrations = buildList {
            addAll(immediateRules.values)
            addAll(activeTimedRules.values)
            addAll(backgroundRules.values)
            addAll(pendingPermissionRules.values.map { it.registered })
        }
        registrations.forEach { it.released.set(true) }
        immediateTasks.values.forEach { it.cancel(true) }
        immediateTasks.clear()
        immediateRules.clear()
        scheduledTasks.values.forEach { it.cancel(true) }
        scheduledTasks.clear()
        activeTimedRules.clear()
        backgroundRules.clear()
        runningBackgroundRules.clear()
        val pendingPermissions = pendingPermissionRules.values.toList()
        pendingPermissionRules.clear()
        pendingPermissions.forEach { it.onTerminal() }
        synchronized(activityRuleKeys) { activityRuleKeys.clear() }
        clearFragmentObservers()
        application?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()
            ?.clear()
            ?.apply()
    }

    /** 手动释放指定 key 的任务、回调、权限等待和生命周期引用。 */
    @JvmStatic
    fun release(key: String): Boolean = cancelInternal(key, clearPersistedDeadline = true)

    /** 手动释放配置中的全部规则，返回实际释放的规则数。 */
    @JvmStatic
    fun release(config: CleanupConfig): Int {
        return config.rules.count { release(it.key) }
    }

    /** 手动释放绑定到 Activity 的全部规则。 */
    @JvmStatic
    fun release(activity: Activity): Int = releaseActivityRules(activity)

    /** 手动释放绑定到 Fragment 或其宿主 Activity 的全部规则。 */
    @JvmStatic
    @JvmOverloads
    fun release(
        fragment: Fragment,
        lifecycleScope: CleanupLifecycleScope = CleanupLifecycleScope.FRAGMENT
    ): Int {
        return if (lifecycleScope == CleanupLifecycleScope.ACTIVITY) {
            fragment.activity?.let(::releaseActivityRules) ?: 0
        } else {
            releaseFragmentRules(fragment)
        }
    }

    /** 手动释放管理器中的全部任务和资源。 */
    @JvmStatic
    fun releaseAll() = cancelAll()

    private fun registerInternal(
        application: Application?,
        config: CleanupConfig,
        callbacks: CleanupCallbacks,
        ownerActivity: Activity? = null,
        ownerFragment: Fragment? = null
    ) {
        config.rules.filter { it.enabled }.forEach { rule ->
            cancelInternal(rule.key, clearPersistedDeadline = false)
            ownerActivity?.let { attachActivityRule(it, rule.key) }
            ownerFragment?.let { attachFragmentRule(it, rule.key) }
            val registered = RegisteredRule(
                rule = rule,
                callbacks = callbacks,
                ownerActivity = ownerActivity?.let(::WeakReference),
                ownerFragment = ownerFragment?.let(::WeakReference)
            )
            when (val trigger = rule.trigger) {
                CleanupTrigger.OnAppStart -> submit(registered) {
                    releaseRegistration(registered)
                }
                CleanupTrigger.OnAppBackground -> {
                    requireNotNull(application) {
                        "OnAppBackground requires register(application, config)"
                    }
                    backgroundRules[rule.key] = registered
                }
                is CleanupTrigger.AfterDelay -> {
                    activeTimedRules[rule.key] = registered
                    val delayMs = resolvePersistedDelay(
                        application,
                        rule.key,
                        trigger.delayMs,
                        trigger.persistAcrossRestarts
                    )
                    schedule(registered, delayMs)
                }
                is CleanupTrigger.AfterDays -> {
                    activeTimedRules[rule.key] = registered
                    val delayMs = resolvePersistedDelay(
                        application,
                        rule.key,
                        TimeUnit.DAYS.toMillis(trigger.days.toLong()),
                        trigger.persistAcrossRestarts
                    )
                    schedule(registered, delayMs)
                }
                is CleanupTrigger.AtTimeMillis -> {
                    activeTimedRules[rule.key] = registered
                    val delayMs = (trigger.triggerAtMillis - System.currentTimeMillis())
                        .coerceAtLeast(0L)
                    schedule(registered, delayMs)
                }
            }
        }
    }

    private fun schedule(
        registered: RegisteredRule,
        delayMs: Long
    ) {
        require(delayMs >= 0L) { "delayMs must be non-negative" }
        val rule = registered.rule
        if (activeTimedRules[rule.key] !== registered) return
        val futureRef = AtomicReference<Future<*>?>()
        val future = TaskExecutor.ioDelayed(
            task = {
                futureRef.get()?.let { scheduledTasks.remove(rule.key, it) }
                if (activeTimedRules[rule.key] !== registered) return@ioDelayed
                executeRegistered(registered) { completeTimedRule(registered) }
            },
            delayMs = delayMs
        )
        futureRef.set(future)
        scheduledTasks[rule.key] = future
        if (activeTimedRules[rule.key] !== registered) {
            future.cancel(true)
            scheduledTasks.remove(rule.key, future)
            return
        }
        if (future.isDone) {
            scheduledTasks.remove(rule.key, future)
        }
    }

    private fun completeTimedRule(registered: RegisteredRule) {
        val rule = registered.rule
        if (rule.scheduleMode == CleanupScheduleMode.RESTART_AFTER_EXECUTION &&
            activeTimedRules[rule.key] === registered
        ) {
            val nextDelayMs = when (val trigger = rule.trigger) {
                is CleanupTrigger.AfterDelay -> trigger.delayMs
                is CleanupTrigger.AfterDays -> TimeUnit.DAYS.toMillis(trigger.days.toLong())
                else -> return releaseTimedRule(registered)
            }
            persistNextDeadlineIfNeeded(rule, nextDelayMs)
            schedule(registered, nextDelayMs)
            return
        }
        releaseTimedRule(registered)
    }

    private fun releaseTimedRule(registered: RegisteredRule) {
        val key = registered.rule.key
        if (activeTimedRules.remove(key, registered)) {
            scheduledTasks.remove(key)
            clearPersistedDeadline(key)
            releaseRegistration(registered)
        }
    }

    private fun persistNextDeadlineIfNeeded(rule: CleanupRule, delayMs: Long) {
        val persistAcrossRestarts = when (val trigger = rule.trigger) {
            is CleanupTrigger.AfterDelay -> trigger.persistAcrossRestarts
            is CleanupTrigger.AfterDays -> trigger.persistAcrossRestarts
            else -> false
        }
        if (!persistAcrossRestarts) return
        val app = application ?: return
        val deadline = saturatingAdd(System.currentTimeMillis(), delayMs)
        app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(DEADLINE_PREFIX + rule.key, deadline)
            .apply()
    }

    private fun submit(
        registered: RegisteredRule,
        onTerminal: () -> Unit = {}
    ): Future<*> {
        val key = registered.rule.key
        immediateRules[key] = registered
        val futureRef = AtomicReference<Future<*>?>()
        val future = try {
            TaskExecutor.io {
                try {
                    executeRegistered(registered, onTerminal)
                } finally {
                    immediateRules.remove(key, registered)
                    futureRef.get()?.let { immediateTasks.remove(key, it) }
                }
            }
        } catch (error: Throwable) {
            immediateRules.remove(key, registered)
            throw error
        }
        futureRef.set(future)
        immediateTasks[key] = future
        if (future.isDone) {
            immediateTasks.remove(key, future)
        }
        return future
    }

    private fun resolvePersistedDelay(
        application: Application?,
        key: String,
        durationMs: Long,
        persistAcrossRestarts: Boolean
    ): Long {
        require(durationMs >= 0L) { "durationMs must be non-negative" }
        if (!persistAcrossRestarts) {
            application?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ?.edit()
                ?.remove(DEADLINE_PREFIX + key)
                ?.apply()
            return durationMs
        }
        val app = requireNotNull(application) {
            "persistAcrossRestarts requires Application, Activity, or Fragment registration"
        }

        val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val preferenceKey = DEADLINE_PREFIX + key
        val storedDeadline = prefs.getLong(preferenceKey, -1L)
        val deadline = if (storedDeadline >= 0L) {
            storedDeadline
        } else {
            val newDeadline = saturatingAdd(System.currentTimeMillis(), durationMs)
            prefs.edit().putLong(preferenceKey, newDeadline).apply()
            newDeadline
        }
        return (deadline - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    private fun saturatingAdd(left: Long, right: Long): Long {
        if (right > 0L && left > Long.MAX_VALUE - right) return Long.MAX_VALUE
        return left + right
    }

    private fun clearPersistedDeadline(key: String) {
        application?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()
            ?.remove(DEADLINE_PREFIX + key)
            ?.apply()
    }

    @Synchronized
    private fun ensureLifecycleCallbacks(application: Application) {
        if (lifecycleRegistered) return
        application.registerActivityLifecycleCallbacks(BackgroundCallbacks)
        lifecycleRegistered = true
    }

    private fun executeBackgroundRules() {
        backgroundRules.values.toList().forEach { registered ->
            val key = registered.rule.key
            if (runningBackgroundRules.add(key)) {
                submitBackground(registered)
            }
        }
    }

    private fun submitBackground(registered: RegisteredRule) {
        submit(registered) {
            runningBackgroundRules.remove(registered.rule.key)
        }
    }

    private fun executeRegistered(
        registered: RegisteredRule,
        onTerminal: () -> Unit = {}
    ) {
        if (!isOwnerAlive(registered)) {
            onTerminal()
            return
        }
        val result = CleanupExecutor.execute(registered.rule)
        if (!isOwnerAlive(registered)) {
            onTerminal()
            return
        }
        val permissionCallback = registered.callbacks.onPermissionRequired
        if (isPermissionFailure(result) && permissionCallback != null) {
            val pending = PendingPermissionRule(registered, result, onTerminal)
            pendingPermissionRules[registered.rule.key] = pending
            val request = createPermissionRequest(pending)
            TaskExecutor.main {
                try {
                    if (isOwnerAlive(registered)) {
                        permissionCallback(request)
                    } else if (pendingPermissionRules.remove(registered.rule.key, pending)) {
                        onTerminal()
                    }
                } catch (_: Throwable) {
                    if (pendingPermissionRules.remove(registered.rule.key, pending)) {
                        deliverResult(registered, result, onTerminal)
                    }
                }
            }
            return
        }
        deliverResult(registered, result, onTerminal)
    }

    private fun isOwnerAlive(registered: RegisteredRule): Boolean {
        if (registered.released.get()) return false
        registered.ownerActivity?.let { reference ->
            val owner = reference.get() ?: return false
            if (owner.isDestroyed) return false
        }
        registered.ownerFragment?.let { reference ->
            val owner = reference.get() ?: return false
            if (owner.lifecycle.currentState == Lifecycle.State.DESTROYED) return false
        }
        return true
    }

    private fun deliverResult(
        registered: RegisteredRule,
        result: CleanupResult,
        onTerminal: () -> Unit
    ) {
        TaskExecutor.main {
            try {
                if (isOwnerAlive(registered)) {
                    registered.callbacks.onResult(result)
                }
            } finally {
                onTerminal()
            }
        }
    }

    private fun createPermissionRequest(pending: PendingPermissionRule): CleanupPermissionRequest {
        val suggestions = permissionSuggestions(pending.registered.rule.path)
        return CleanupPermissionRequest(
            key = pending.registered.rule.key,
            path = pending.registered.rule.path,
            suggestedPermissions = suggestions.first,
            requiresSpecialSettings = suggestions.second,
            reason = pending.failure.message,
            retryAction = { retryPermission(pending.registered.rule.key, pending.token) },
            cancelAction = { cancelPermission(pending.registered.rule.key, pending.token) }
        )
    }

    private fun retryPermission(key: String, token: Any): Boolean {
        val pending = pendingPermissionRules[key] ?: return false
        if (pending.token !== token) return false
        if (!pendingPermissionRules.remove(key, pending)) return false
        submit(pending.registered, pending.onTerminal)
        return true
    }

    private fun cancelPermission(key: String, token: Any): Boolean {
        val pending = pendingPermissionRules[key] ?: return false
        if (pending.token !== token) return false
        if (!pendingPermissionRules.remove(key, pending)) return false
        deliverResult(pending.registered, pending.failure, pending.onTerminal)
        return true
    }

    private fun isPermissionFailure(result: CleanupResult): Boolean {
        var error = result.error
        while (error != null) {
            if (error is SecurityException) return true
            error = error.cause
        }
        return false
    }

    private fun permissionSuggestions(path: String): Pair<List<String>, Boolean> {
        if (isAppSpecificPath(path)) return emptyList<String>() to false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            listOf(Manifest.permission.MANAGE_EXTERNAL_STORAGE) to true
        } else {
            listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE) to false
        }
    }

    private fun isAppSpecificPath(path: String): Boolean {
        val app = application ?: return false
        val target = runCatching { java.io.File(path).canonicalFile }.getOrNull() ?: return false
        val roots = buildList {
            add(app.filesDir)
            add(app.cacheDir)
            app.externalCacheDir?.let(::add)
            addAll(app.getExternalFilesDirs(null).filterNotNull())
        }
        return roots.any { root ->
            val canonicalRoot = runCatching { root.canonicalFile }.getOrNull() ?: return@any false
            target == canonicalRoot || target.path.startsWith(canonicalRoot.path + java.io.File.separator)
        }
    }

    private data class RegisteredRule(
        val rule: CleanupRule,
        val callbacks: CleanupCallbacks,
        val ownerActivity: WeakReference<Activity>? = null,
        val ownerFragment: WeakReference<Fragment>? = null,
        val released: AtomicBoolean = AtomicBoolean(false)
    )

    private data class PendingPermissionRule(
        val registered: RegisteredRule,
        val failure: CleanupResult,
        val onTerminal: () -> Unit,
        val token: Any = Any()
    )

    private fun attachActivityRule(activity: Activity, key: String) {
        synchronized(activityRuleKeys) {
            activityRuleKeys.getOrPut(activity) { mutableSetOf() }.add(key)
        }
    }

    private fun releaseRegistration(registered: RegisteredRule) {
        registered.released.set(true)
        immediateRules.remove(registered.rule.key, registered)
        activeTimedRules.remove(registered.rule.key, registered)
        runningBackgroundRules.remove(registered.rule.key)
        detachOwnerRule(registered.rule.key)
    }

    private fun detachOwnerRule(key: String) {
        detachActivityRule(key)
        detachFragmentRule(key)
    }

    private fun detachActivityRule(key: String) {
        synchronized(activityRuleKeys) {
            val iterator = activityRuleKeys.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                entry.value.remove(key)
                if (entry.value.isEmpty()) iterator.remove()
            }
        }
    }

    private fun releaseActivityRules(activity: Activity): Int {
        val keys = synchronized(activityRuleKeys) {
            activityRuleKeys.remove(activity)?.toList().orEmpty()
        }
        return keys.count { key -> cancelInternal(key, clearPersistedDeadline = true) }
    }

    private fun attachFragmentRule(fragment: Fragment, key: String) {
        synchronized(fragmentRuleKeys) {
            fragmentRuleKeys.getOrPut(fragment) { mutableSetOf() }.add(key)
        }
    }

    private fun ensureFragmentObserver(fragment: Fragment) {
        val observer = synchronized(fragmentRuleKeys) {
            fragmentObservers[fragment]?.let { return }
            val fragmentReference = WeakReference(fragment)
            object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    fragmentReference.get()?.let(::releaseFragmentRules)
                    owner.lifecycle.removeObserver(this)
                }
            }.also { fragmentObservers[fragment] = it }
        }
        fragment.lifecycle.addObserver(observer)
    }

    private fun detachFragmentRule(key: String) {
        val observersToRemove = mutableListOf<Pair<Fragment, DefaultLifecycleObserver>>()
        synchronized(fragmentRuleKeys) {
            val iterator = fragmentRuleKeys.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                entry.value.remove(key)
                if (entry.value.isEmpty()) {
                    iterator.remove()
                    fragmentObservers.remove(entry.key)?.let { observer ->
                        observersToRemove += entry.key to observer
                    }
                }
            }
        }
        observersToRemove.forEach { (fragment, observer) ->
            fragment.lifecycle.removeObserver(observer)
        }
    }

    private fun releaseFragmentRules(fragment: Fragment): Int {
        val (keys, observer) = synchronized(fragmentRuleKeys) {
            fragmentRuleKeys.remove(fragment)?.toList().orEmpty() to
                fragmentObservers.remove(fragment)
        }
        observer?.let { fragment.lifecycle.removeObserver(it) }
        return keys.count { key -> cancelInternal(key, clearPersistedDeadline = true) }
    }

    private fun clearFragmentObservers() {
        val observers = synchronized(fragmentRuleKeys) {
            val snapshot = fragmentObservers.entries.map { it.key to it.value }
            fragmentObservers.clear()
            fragmentRuleKeys.clear()
            snapshot
        }
        observers.forEach { (fragment, observer) ->
            fragment.lifecycle.removeObserver(observer)
        }
    }

    private object BackgroundCallbacks : Application.ActivityLifecycleCallbacks {
        private val startedActivities = AtomicInteger(0)

        override fun onActivityStarted(activity: Activity) {
            startedActivities.incrementAndGet()
        }

        override fun onActivityStopped(activity: Activity) {
            val count = startedActivities.updateAndGet { current ->
                (current - 1).coerceAtLeast(0)
            }
            if (count == 0 && !activity.isChangingConfigurations) {
                executeBackgroundRules()
            }
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) {
            releaseActivityRules(activity)
        }
    }

    class Builder internal constructor() {
        private val rules = linkedMapOf<String, CleanupRule>()

        fun clearOnAppStart(key: String, path: String) =
            add(key, path, CleanupAction.CLEAR_DIRECTORY, CleanupTrigger.OnAppStart)

        fun deleteOnAppStart(key: String, path: String) =
            add(key, path, CleanupAction.DELETE_TARGET, CleanupTrigger.OnAppStart)

        fun clearOnAppBackground(key: String, path: String) =
            add(key, path, CleanupAction.CLEAR_DIRECTORY, CleanupTrigger.OnAppBackground)

        fun deleteOnAppBackground(key: String, path: String) =
            add(key, path, CleanupAction.DELETE_TARGET, CleanupTrigger.OnAppBackground)

        @JvmOverloads
        fun clearAfterDelay(
            key: String,
            path: String,
            delayMs: Long,
            scheduleMode: CleanupScheduleMode = CleanupScheduleMode.ONE_SHOT,
            persistAcrossRestarts: Boolean = false
        ) = add(
            key,
            path,
            CleanupAction.CLEAR_DIRECTORY,
            CleanupTrigger.AfterDelay(delayMs, persistAcrossRestarts),
            scheduleMode
        )

        @JvmOverloads
        fun deleteAfterDelay(
            key: String,
            path: String,
            delayMs: Long,
            scheduleMode: CleanupScheduleMode = CleanupScheduleMode.ONE_SHOT,
            persistAcrossRestarts: Boolean = false
        ) = add(
            key,
            path,
            CleanupAction.DELETE_TARGET,
            CleanupTrigger.AfterDelay(delayMs, persistAcrossRestarts),
            scheduleMode
        )

        @JvmOverloads
        fun clearAfterDays(
            key: String,
            path: String,
            days: Int,
            persistAcrossRestarts: Boolean = true,
            scheduleMode: CleanupScheduleMode = CleanupScheduleMode.ONE_SHOT
        ) = add(
            key,
            path,
            CleanupAction.CLEAR_DIRECTORY,
            CleanupTrigger.AfterDays(days, persistAcrossRestarts),
            scheduleMode
        )

        @JvmOverloads
        fun deleteAfterDays(
            key: String,
            path: String,
            days: Int,
            persistAcrossRestarts: Boolean = true,
            scheduleMode: CleanupScheduleMode = CleanupScheduleMode.ONE_SHOT
        ) = add(
            key,
            path,
            CleanupAction.DELETE_TARGET,
            CleanupTrigger.AfterDays(days, persistAcrossRestarts),
            scheduleMode
        )

        fun clearAtTime(key: String, path: String, triggerAtMillis: Long) =
            add(
                key,
                path,
                CleanupAction.CLEAR_DIRECTORY,
                CleanupTrigger.AtTimeMillis(triggerAtMillis)
            )

        fun deleteAtTime(key: String, path: String, triggerAtMillis: Long) =
            add(
                key,
                path,
                CleanupAction.DELETE_TARGET,
                CleanupTrigger.AtTimeMillis(triggerAtMillis)
            )

        fun add(rule: CleanupRule): Builder {
            validateRule(rule)
            check(!rules.containsKey(rule.key)) {
                "Duplicate cleanup rule key: ${rule.key}"
            }
            rules[rule.key] = rule
            return this
        }

        fun build(): CleanupConfig {
            return CleanupConfig(rules.values.toList())
        }

        private fun add(
            key: String,
            path: String,
            action: CleanupAction,
            trigger: CleanupTrigger,
            scheduleMode: CleanupScheduleMode = CleanupScheduleMode.ONE_SHOT
        ): Builder = add(CleanupRule(key, path, action, trigger, scheduleMode))

        private fun validateRule(rule: CleanupRule) {
            require(rule.key.isNotBlank()) { "Cleanup rule key must not be blank" }
            require(rule.path.isNotBlank()) { "Cleanup path must not be blank" }
            when (val trigger = rule.trigger) {
                is CleanupTrigger.AfterDelay ->
                    require(trigger.delayMs >= 0L) { "delayMs must be non-negative" }
                is CleanupTrigger.AfterDays ->
                    require(trigger.days >= 0) { "days must be non-negative" }
                is CleanupTrigger.AtTimeMillis ->
                    require(trigger.triggerAtMillis >= 0L) {
                        "triggerAtMillis must be non-negative"
                    }
                CleanupTrigger.OnAppStart,
                CleanupTrigger.OnAppBackground -> Unit
            }
            if (rule.scheduleMode == CleanupScheduleMode.RESTART_AFTER_EXECUTION) {
                require(rule.trigger is CleanupTrigger.AfterDelay ||
                    rule.trigger is CleanupTrigger.AfterDays
                ) {
                    "RESTART_AFTER_EXECUTION only supports AfterDelay and AfterDays"
                }
            }
        }
    }
}
