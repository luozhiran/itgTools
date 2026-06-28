# Android 本地文件清理教程

`FileCleanupManager` 用于清空目录内容或删除整个目标，并支持应用启动、切到后台、延迟、保留 N 天、指定时间和手动立即执行。

管理器通过 `Application` 感知 Android 应用生命周期。建议在 `Application.onCreate()` 中完成一次配置和注册；规则在当前应用进程内保持有效，直到执行完成或通过 `cancel` 取消。其中后台规则会在应用每次从前台进入后台时执行。

## 1. 引入模块

```kotlin
dependencies {
    implementation(project(":itg-file"))
}
```

```kotlin
import com.itg.itg_file.cleanup.FileCleanupManager
import com.itg.itg_file.cleanup.CleanupCallbacks
import com.itg.itg_file.cleanup.CleanupLifecycleScope
import com.itg.itg_file.cleanup.CleanupPermissionRequest
import com.itg.itg_file.cleanup.CleanupScheduleMode
import com.itg.itg_thread_pools.executor.TaskExecutor
```

规则的 `key` 必须唯一，用于覆盖管理和取消任务。回调运行在 I/O 线程，更新 UI 时需要通过 `TaskExecutor.main {}` 切回主线程。

## 2. 清空目录与删除目标的区别

### 清空目录内容，保留目录

适用于缓存目录、日志目录、下载临时目录。

```kotlin
val config = FileCleanupManager.builder()
    .clearOnAppStart("cache_content", cacheDir.absolutePath)
    .build()

FileCleanupManager.register(config) { result ->
    Log.d("Cleanup", "deleted=${result.deletedEntries}, success=${result.success}")
}
```

执行后 `cacheDir` 仍然存在，仅删除其内部文件和子目录。目标不是目录时会返回失败，避免误删单个文件。

### 删除文件或整个目录

适用于一次性安装包、导出文件、会话临时目录。

```kotlin
val tempFile = File(cacheDir, "share/export.zip")
val config = FileCleanupManager.builder()
    .deleteOnAppStart("old_export", tempFile.absolutePath)
    .build()

FileCleanupManager.register(config) { result ->
    Log.d("Cleanup", result.message)
}
```

`deleteOnAppStart` 会删除目标本身。目标不存在时按幂等成功处理，`deletedEntries` 为 `0`。

## 3. 应用启动时清理

适用于每次冷启动都可以丢弃的缓存和上一次运行遗留的临时文件。

```kotlin
val config = FileCleanupManager.builder()
    .clearOnAppStart("startup_cache", cacheDir.absolutePath)
    .deleteOnAppStart(
        "startup_temp",
        File(cacheDir, "pending.tmp").absolutePath
    )
    .build()

FileCleanupManager.register(config) { result ->
    Log.d("Cleanup", "${result.key}: ${result.message}")
}
```

建议在 `Application.onCreate()` 中注册。注册后启动规则会立即提交到 I/O 线程。

## 4. 应用切到后台时清理

适用于预览缓存、分享文件和仅在前台会话期间有效的数据。

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()

        val config = FileCleanupManager.builder()
            .clearOnAppBackground("preview_cache", File(cacheDir, "preview").absolutePath)
            .deleteOnAppBackground("share_file", File(cacheDir, "share.tmp").absolutePath)
            .build()

        FileCleanupManager.register(this, config) { result ->
            Log.d("Cleanup", "${result.key}: ${result.success}")
        }
    }
}
```

后台规则必须调用 `register(application, config)`。管理器通过 `ActivityLifecycleCallbacks` 判断应用切到后台，并忽略配置变更导致的 Activity 重建。后台规则在应用进程生命周期内常驻，每次进入后台都会触发；如果同一规则的上一次清理仍在执行，本次不会重复提交。

## 5. 延迟一段时间后清理

适用于文件使用结束后保留几秒或几分钟，给上传、分享等后续流程留出时间。

```kotlin
val config = FileCleanupManager.builder()
    .clearAfterDelay(
        key = "delayed_cache",
        path = File(cacheDir, "upload_chunks").absolutePath,
        delayMs = 10_000L
    )
    .deleteAfterDelay(
        key = "delayed_apk",
        path = File(cacheDir, "update.apk").absolutePath,
        delayMs = 30_000L
    )
    .build()

FileCleanupManager.register(config) { result ->
    Log.d("Cleanup", result.message)
}
```

默认 `persistAcrossRestarts = false`，每次在应用启动时注册都会从完整的 `delayMs` 重新计时。进程结束后，本轮内存调度随进程释放。

需要跨重启保留首次截止时间时：

```kotlin
val config = FileCleanupManager.builder()
    .deleteAfterDelay(
        key = "persistent_export",
        path = File(cacheDir, "export.zip").absolutePath,
        delayMs = 30 * 60_000L,
        persistAcrossRestarts = true
    )
    .build()

FileCleanupManager.register(application, config, callbacks)
```

管理器会保存绝对截止时间。应用重启后使用相同 `key` 再次注册时按剩余时间继续；如果截止时间已经过去则立即执行。进程死亡期间不会在后台自行运行，系统级定时需求应使用 WorkManager。

默认使用一次性策略。任务执行完成后会移除规则、Future 引用及相关持久化到期时间：

```kotlin
FileCleanupManager.builder()
    .clearAfterDelay(
        key = "one_shot_cache",
        path = cacheDir.absolutePath,
        delayMs = 10_000L,
        scheduleMode = CleanupScheduleMode.ONE_SHOT
    )
    .build()
```

需要周期清理时使用 `RESTART_AFTER_EXECUTION`。每次清理完成后才按原延迟重新计时，因此不会发生同一规则重叠执行：

```kotlin
FileCleanupManager.builder()
    .clearAfterDelay(
        key = "periodic_cache",
        path = cacheDir.absolutePath,
        delayMs = 10 * 60_000L,
        scheduleMode = CleanupScheduleMode.RESTART_AFTER_EXECUTION
    )
    .build()
```

重新计时策略仅适用于 `AfterDelay` 和 `AfterDays`。`OnAppStart`、`OnAppBackground` 和 `AtTimeMillis` 由各自生命周期或绝对时间语义决定，不接受该策略。

## 6. 保留 N 天后清理

适用于日志保留 7 天、下载缓存保留 3 天等场景。

```kotlin
val config = FileCleanupManager.builder()
    .clearAfterDays(
        key = "logs_7_days",
        path = File(filesDir, "logs").absolutePath,
        days = 7,
        persistAcrossRestarts = true,
        scheduleMode = CleanupScheduleMode.RESTART_AFTER_EXECUTION
    )
    .deleteAfterDays(
        key = "package_3_days",
        path = File(cacheDir, "package.zip").absolutePath,
        days = 3,
        persistAcrossRestarts = true
    )
    .build()

FileCleanupManager.register(application, config) { result ->
    Log.d("Cleanup", "${result.key}: ${result.message}")
}
```

`persistAcrossRestarts = true` 会用 `SharedPreferences` 保存首次计算的到期时间。应用重启后再次注册同一个 `key`，会继续使用原到期时间，而不是重新计时。

```kotlin
val config = FileCleanupManager.builder()
    .clearAfterDays(
        key = "session_cache",
        path = cacheDir.absolutePath,
        days = 1,
        persistAcrossRestarts = false
    )
    .build()
```

`persistAcrossRestarts = false` 表示每次注册都重新计时，进程结束后任务不会恢复。

| 配置 | 重启后的行为 | 注册要求 |
|---|---|---|
| `persistAcrossRestarts = true` | 保留首次截止时间，按剩余时间继续 | `Application`、`Activity` 或 `Fragment` |
| `persistAcrossRestarts = false` | 清除旧截止时间，从当前注册时刻重新计时 | 任意注册入口 |

该参数同时支持 `AfterDelay` 和 `AfterDays`。轮询任务使用 `RESTART_AFTER_EXECUTION` 时，每轮完成后都会保存下一轮截止时间。

## 7. 在指定时间点清理

适用于已知绝对到期时间的导出文件或日志轮转。

```kotlin
val tomorrowAtMidnight = Calendar.getInstance().run {
    add(Calendar.DAY_OF_YEAR, 1)
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
    timeInMillis
}

val config = FileCleanupManager.builder()
    .clearAtTime("midnight_logs", File(filesDir, "logs").absolutePath, tomorrowAtMidnight)
    .deleteAtTime("expired_export", exportFile.absolutePath, tomorrowAtMidnight)
    .build()

FileCleanupManager.register(config) { result ->
    Log.d("Cleanup", result.message)
}
```

时间参数使用 Unix 毫秒时间戳。若时间点已经过去，任务会立即执行。该调度只在当前进程存活期间有效；需要系统级可靠定时任务时应使用 WorkManager。

## 8. 立即执行

适用于用户点击“清除缓存”、退出账号或存储空间不足时主动清理。

```kotlin
val config = FileCleanupManager.builder()
    .clearAfterDays("cache", cacheDir.absolutePath, days = 7)
    .deleteAfterDelay("temp", tempFile.absolutePath, delayMs = 60_000L)
    .build()

FileCleanupManager.runNow(config) { result ->
    TaskExecutor.main {
        Toast.makeText(
            context,
            if (result.success) "已清理 ${result.deletedEntries} 项" else result.message,
            Toast.LENGTH_SHORT
        ).show()
    }
}
```

`runNow` 会忽略规则原有触发条件，并取消同 `key` 的待执行任务后立即清理。

`runNow` 始终是一次性执行：即使规则配置了 `RESTART_AFTER_EXECUTION`，也不会启动下一轮。执行结束后会移除即时 Future、规则状态和持久化到期时间。

在 Activity 中立即执行并绑定页面生命周期：

```kotlin
FileCleanupManager.runNow(
    activity = this,
    config = config,
    callbacks = callbacks
)
```

在 Fragment 中立即执行，并选择 Fragment 或宿主 Activity 作用域：

```kotlin
FileCleanupManager.runNow(
    fragment = this,
    config = config,
    lifecycleScope = CleanupLifecycleScope.FRAGMENT,
    callbacks = callbacks
)
```

如果绑定的 Activity 或 Fragment 在任务完成前销毁，管理器会取消 Future、释放回调和权限等待状态，并禁止结果回调到已销毁页面。

## 9. 取消任务

```kotlin
val cancelled = FileCleanupManager.cancel("logs_7_days")
FileCleanupManager.cancelAll()
```

`cancel(key)` 会取消内存中的延迟或后台规则，并删除该 `key` 保存的按天到期时间。`cancelAll()` 会取消全部待执行任务并清空清理模块的持久化计时。

需要由用户主动释放任务、回调、权限等待和生命周期观察者时，使用 `release` API：

```kotlin
// 释放单条规则
FileCleanupManager.release("preview_polling")

// 释放一整份配置
val releasedCount = FileCleanupManager.release(config)

// 释放 Activity 作用域内全部清理资源
FileCleanupManager.release(requireActivity())

// 释放 Fragment 作用域或宿主 Activity 作用域
FileCleanupManager.release(this, CleanupLifecycleScope.FRAGMENT)
FileCleanupManager.release(this, CleanupLifecycleScope.ACTIVITY)

// 释放管理器中的全部资源
FileCleanupManager.releaseAll()
```

手动释放后，正在执行但无法立即中断的底层文件操作不会再触发结果回调或重新调度。`release(activity)` 和 `release(fragment, scope)` 返回实际释放的规则数，可用于更新界面状态。

## 10. 读取执行结果

```kotlin
FileCleanupManager.register(application, config) { result ->
    Log.d(
        "Cleanup",
        """
        key=${result.key}
        action=${result.action}
        success=${result.success}
        deleted=${result.deletedEntries}
        existedBefore=${result.existedBefore}
        existedAfter=${result.existedAfter}
        message=${result.message}
        """.trimIndent()
    )

    result.error?.let { Log.e("Cleanup", "Cleanup failed", it) }
}
```

`deletedEntries` 同时统计文件和目录。清空一个含有子目录和文件的目录时，保留的根目录不计入删除数量。

## 11. 绑定 Activity 生命周期

使用 Activity 重载注册后，规则只在该 Activity 生命周期内有效。适合页面临时下载、预览缓存、编辑草稿等场景。

```kotlin
class PreviewActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val config = FileCleanupManager.builder()
            // 一次性任务：10 秒后执行一次并释放。
            .deleteAfterDelay(
                key = "preview_once",
                path = File(cacheDir, "preview.tmp").absolutePath,
                delayMs = 10_000L,
                scheduleMode = CleanupScheduleMode.ONE_SHOT
            )
            // 轮询任务：每次执行结束后重新计时。
            .clearAfterDelay(
                key = "preview_polling",
                path = File(cacheDir, "preview_chunks").absolutePath,
                delayMs = 60_000L,
                scheduleMode = CleanupScheduleMode.RESTART_AFTER_EXECUTION
            )
            .build()

        FileCleanupManager.register(
            activity = this,
            config = config,
            callbacks = CleanupCallbacks(
                onPermissionRequired = { request -> handlePermission(request) },
                onResult = { result -> Log.d("Cleanup", result.message) }
            )
        )
    }
}
```

Activity 执行 `onDestroy()` 后，管理器会自动：

- 取消尚未完成的即时和延迟 Future。
- 移除一次性、轮询及后台触发规则。
- 释放等待权限的请求和 Activity 回调引用。
- 禁止已销毁页面收到后续结果回调或重新调度。
- 清除该规则保存的到期时间。

已经进入底层文件系统调用的操作可能无法瞬间中止，但完成后不会回调已销毁的 Activity，也不会启动下一轮。配置变更会销毁旧 Activity，因此新 Activity 应在新的 `onCreate()` 中重新注册；同名 `key` 会替换旧注册。

## 12. 绑定 Fragment 或 Activity 生命周期

Fragment 中注册时通过 `CleanupLifecycleScope` 指定任务跟随 Fragment 还是宿主 Activity。

### 跟随 Fragment

Fragment 销毁后释放一次性任务、轮询任务、Future、权限等待和回调引用：

```kotlin
class DownloadFragment : Fragment(R.layout.fragment_download) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val config = FileCleanupManager.builder()
            .deleteAfterDelay(
                key = "fragment_once",
                path = File(requireContext().cacheDir, "download.tmp").absolutePath,
                delayMs = 10_000L,
                scheduleMode = CleanupScheduleMode.ONE_SHOT
            )
            .clearAfterDelay(
                key = "fragment_polling",
                path = File(requireContext().cacheDir, "chunks").absolutePath,
                delayMs = 60_000L,
                scheduleMode = CleanupScheduleMode.RESTART_AFTER_EXECUTION
            )
            .build()

        FileCleanupManager.register(
            fragment = this,
            config = config,
            lifecycleScope = CleanupLifecycleScope.FRAGMENT,
            callbacks = callbacks
        )
    }
}
```

### 跟随宿主 Activity

Fragment 被替换、移除或重建后任务继续运行，直到宿主 Activity 销毁：

```kotlin
FileCleanupManager.register(
    fragment = this,
    config = config,
    lifecycleScope = CleanupLifecycleScope.ACTIVITY,
    callbacks = callbacks
)
```

| 作用域 | Fragment 销毁 | Activity 销毁 | 适用场景 |
|---|---|---|---|
| `FRAGMENT` | 取消并释放 | 已随 Fragment 释放 | 页面私有临时文件、页面轮询 |
| `ACTIVITY` | 继续运行 | 取消并释放 | 多 Fragment 共享缓存、Activity 级任务 |

Fragment 必须已经附加到 Activity 后才能注册，推荐在 `onCreate()` 或 `onViewCreated()` 中调用。配置变更后，如果选择 `FRAGMENT`，新 Fragment 需要重新注册；同名 `key` 会替换旧任务。

## 13. 权限不足时申请并重试

工具不会直接弹权限框。清理因为写权限不足而暂停时，`onPermissionRequired` 通知宿主应用；授权完成后调用同一个请求的 `retry()`，拒绝授权则调用 `cancel()`。最终成功或失败都通过 `onResult` 返回。

```kotlin
private var pendingCleanupRequest: CleanupPermissionRequest? = null

private val runtimePermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
) { grants ->
    val request = pendingCleanupRequest ?: return@registerForActivityResult
    pendingCleanupRequest = null
    if (grants.values.all { it }) request.retry() else request.cancel()
}

private val allFilesAccessLauncher = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult()
) {
    val request = pendingCleanupRequest ?: return@registerForActivityResult
    pendingCleanupRequest = null
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
        Environment.isExternalStorageManager()
    ) {
        request.retry()
    } else {
        request.cancel()
    }
}
```

注册清理任务并处理权限请求：

```kotlin
val callbacks = CleanupCallbacks(
    onPermissionRequired = { request ->
        pendingCleanupRequest = request
        when {
            request.requiresSpecialSettings -> {
                val intent = Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                allFilesAccessLauncher.launch(intent)
            }
            request.suggestedPermissions.isNotEmpty() -> {
                runtimePermissionLauncher.launch(
                    request.suggestedPermissions.toTypedArray()
                )
            }
            else -> {
                // 应用专属目录仍不可写时，系统权限申请通常无法解决。
                request.cancel()
            }
        }
    },
    onResult = { result ->
        if (result.success) {
            Log.d("Cleanup", "已删除 ${result.deletedEntries} 项")
        } else {
            Log.e("Cleanup", result.message, result.error)
        }
    }
)

FileCleanupManager.register(application, config, callbacks)
```

每个 `CleanupPermissionRequest` 只能调用一次 `retry()` 或 `cancel()`。如果授权后仍然没有权限，管理器会再次触发新的权限请求。页面销毁或不再处理请求时，应调用 `request.cancel()`；`FileCleanupManager.cancel(key)` 和 `cancelAll()` 也会释放等待授权的任务。

Android 11 及以上的 `MANAGE_EXTERNAL_STORAGE` 是特殊权限，不通过普通运行时权限对话框申请，并且应用商店对其使用范围有限制。优先清理应用专属目录；共享媒体或用户文档应优先使用 MediaStore、SAF 或 DocumentFile。

## 14. 安全与存储说明

- 空路径和文件系统根目录会被拒绝。
- `CLEAR_DIRECTORY` 只接受目录，避免把单文件误当目录清理。
- 符号链接只删除链接本身，不递归进入链接目标。
- Android 10 及以上受分区存储限制。优先清理 `cacheDir`、`filesDir`、`externalCacheDir` 和应用专属外部目录。
- 对用户通过系统文件选择器授权的 `content://` 文档，应使用 `ContentResolver` 或 `DocumentFile`，本工具的路径 API 不负责删除这类 URI。
- 大目录清理有 I/O 成本，不要在主线程直接调用 `CleanupExecutor.execute`。
