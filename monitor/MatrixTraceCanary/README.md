# MatrixTraceCanary

基于腾讯 Matrix Trace 2.1.0 的 Android 库，向宿主应用回调 FPS、掉帧、主线程卡顿、疑似 ANR、慢方法、启动耗时和页面切换耗时事件。本模块不自动上传数据。

## 接入

在应用中依赖本模块：

```kotlin
implementation(project(":monitor:MatrixTraceCanary"))
```

要自动检测慢方法，还需在**宿主应用模块**应用配套 Gradle 插件；要覆盖自有 Android 库，也可在那些库模块应用同一插件：

```kotlin
plugins {
    id("com.itg.matrix-slow-method")
}
```

本仓库已在根 `settings.gradle.kts` 中通过 `includeBuild` 提供插件。外部应用可从 Maven 仓库使用插件标记坐标 `com.itg.matrix-slow-method:com.itg.matrix-slow-method.gradle.plugin:0.1.0`。应用插件会对所在模块自身编译的类插入计时探针；仅依赖运行时 AAR 时不会产生慢方法事件。被插桩模块最终需要通过宿主应用依赖本运行时库。

模块也已配置 Maven 发布，坐标为 `com.itg:matrix-trace-canary:0.1.0`。通过 Maven 依赖接入时，Matrix 的传递依赖会随发布元数据提供；单独复制 AAR 不会带上这些依赖。发布插件时还需发布 `gradle-plugin` 子构建。

在主进程的 `Application.onCreate()` 中尽早、且仅在主线程初始化一次：

```kotlin
class DemoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MatrixTraceCanary.install(
            this,
            MatrixTraceConfig.Builder().debug(BuildConfig.DEBUG).build()
        ) { event ->
            // 可读取 kind、scene、fromScene、measurement、costMs、fps、
            // threadStack、methodName、contentJson。
            // 如需上传，请在自己的后台线程处理。
            Log.d("MatrixTrace", event.contentJson)
        }
    }
}
```

还需在应用的 Manifest 中注册这个 `Application`。默认开启 FPS、疑似 ANR、慢方法、启动和 Activity 页面切换监控；可在 `MatrixTraceConfig.Builder` 中分别开关。慢方法默认阈值为 700 ms，可通过 `slowMethodThresholdMs(...)` 调整。`stop()` / `start()` 用于暂停、恢复监控，必须在主线程调用。Matrix 事件的回调线程由 Matrix 决定，更新 UI 时需自行切回主线程。重复调用 `install()` 会保留首次注册的监听器。

ANR 有两类来源：Matrix 在运行中上报的疑似 ANR；Android 11 及以上版本在下次进程启动后提供的系统 ANR 退出记录。后者的 `measurement` 为 `historical_exit_reason_anr`，在后台线程回调，`contentJson` 包含退出时间、进程名和系统描述，不保证包含完整 ANR Trace。Android 10 及以下只能获得 Matrix 的运行中事件。

`STARTUP` 的 `measurement` 为 `process_start_to_first_frame`（进程创建至首个 Activity 第一帧）或 `foreground_start_to_first_frame`（应用从后台返回至第一帧）。`PAGE_SWITCH` 的自动计时口径是 `activity_pause_to_first_frame`（前一 Activity 暂停至目标 Activity 第一帧）。首帧由 Android `Window.OnFrameMetricsAvailableListener` 观察。进程先因后台任务创建、很久后才打开 UI 时，首次启动数值会包含这段等待时间；这两项是本模块的生命周期计时，不是 Matrix 原生 StartupTracer 输出。

Fragment、Compose 或同一 Activity 内切页可手动标记，完成时机由宿主决定：

```kotlin
val transition = MatrixTraceCanary.beginPageTransition("列表", "详情")
// 在详情内容可见后：
transition.finish()
// 如果导航取消，调用 transition.cancel()。
```

手动事件的 `measurement` 为 `manual_mark_to_visible`，不会等待 Activity 首帧。避免对同一次 Activity 跳转同时使用自动和手动计时。

## 宿主已经使用 Matrix

Matrix 是进程级单例。宿主应在调用 `Matrix.init` 之前，将 `MatrixTraceCanary.createPlugin(config)` 返回的插件加入现有 `Matrix.Builder`；在现有 `PluginListener.onReportIssue` 中用 `MatrixTraceEvent.fromIssue(issue)` 转换事件，并在初始化后启动该插件。慢方法监听还需调用 `MatrixTraceCanary.configureSlowMethods(config, listener)`，启动、页面切换和历史 ANR 监听需在 `Application.onCreate()` 中调用 `MatrixTraceCanary.configureActivityTiming(application, config, listener)`。便捷的 `install()` 方法会拒绝接入已初始化的 Matrix 实例。

## 能力边界与兼容性

腾讯 Matrix 原版 Gradle 插件使用已移除的 Android Transform API，无法直接用于本项目的 AGP 9 构建。本模块的配套插件通过 AGP Instrumentation API 检测**被插桩模块自身类的主线程慢方法**，方法完成后回调 `SLOW_METHOD`，提供完整方法名与耗时。它不会自动插桩外部依赖、构造方法、静态初始化器和部分编译器生成方法；耗时包含被调用方法的时间。它独立于 Matrix 的 `EvilMethodTracer`，因此不会生成 Matrix 原生的逐方法 Trace 堆栈或方法映射文件。FPS、卡顿和疑似 ANR 仍由 Matrix 运行时 `TracePlugin` 提供。

Matrix 2.1.0 的传递依赖含有 ARM 原生库 `libtrace-canary.so`，Android lint 提示该库未满足 16 KB 页面对齐。本封装关闭了原生 signal ANR 功能；仅使用此封装 Java 监控功能的应用，可在应用的 `android.packaging.jniLibs.excludes` 中排除 `**/libtrace-canary.so`，并验证最终 APK 在目标设备上的表现。

Matrix 事件可能包含堆栈及应用数据。上传前请按应用的数据策略处理。
