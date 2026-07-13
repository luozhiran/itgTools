package com.itg.itg_web_cache

/**
 * WebView 预热模块运行时环境状态快照。
 *
 * 由宿主 App 通过 [WebCacheStateProvider] 定期或事件驱动地提供给预热模块，
 * 用于在预热执行前校验各项前置条件是否满足。
 *
 * 使用方式：
 *   - App 实现 [WebCacheStateProvider.getState] 并注入到 [WebCachePreloadManager]
 *   - 每次预热任务执行前，[WebCachePolicyResolver] 调用 getState() 获取最新状态
 *   - 任一条件不满足即跳过本轮预热，不会阻塞或报错
 *
 * 注意：
 *   - 本类中的所有字段默认值均为"允许预热"的安全值。
 *     宿主 App 必须在正确的时机将对应字段更新为真实状态，否则预热可能在不合适的条件下执行。
 *   - 字段值应为瞬时快照，调用 getState() 的频次由预热管理器内部控制。
 *
 * @see NetworkType 网络类型枚举
 * @see WebCacheStateProvider 状态提供者接口
 * @see WebCachePolicyResolver 策略决策器（消费本状态）
 * @see WebCachePreloadManager 预热管理器（持有 stateProvider 引用）
 */
data class WebCacheRuntimeState(

    // ========================================================================
    // 前后台与首页状态
    // ========================================================================

    /**
     * App 当前是否在前台。
     *
     * 预热仅在 App 前台时执行。进入后台时应立即设为 false，
     * 预热管理器收到 false 后会取消正在执行的预热任务并销毁隐藏 WebView。
     *
     * 建议绑定：
     *   - true  → Activity.onResume / ProcessLifecycleOwner ON_START
     *   - false → Activity.onPause / ProcessLifecycleOwner ON_STOP
     */
    val isForeground: Boolean = true,

    /**
     * 首页是否已渲染完毕（首屏稳定）。
     *
     * 预热应在首页完全展示后延迟启动，避免与首页布局、数据加载、
     * 图片解码等关键任务争抢主线程和网络资源。
     *
     * 建议绑定：
     *   - true  → 首页首帧绘制完成 + 核心接口返回 + 延迟 preloadDelayMs 后
     *   - false → 首页未就绪 / 离开首页
     */
    val isHomeReady: Boolean = true,

    // ========================================================================
    // 用户状态
    // ========================================================================

    /**
     * 用户是否已登录。
     *
     * 部分 H5 页面依赖登录态才能正确加载和渲染。未登录时预热可能缓存到
     * 空壳页面、登录重定向页或错误页，不仅浪费资源，还可能污染缓存。
     *
     * 当 [WebCacheConfig.preloadLoginRequired] = true 且此值为 false 时，
     * 所有登录依赖的预热规则都会被跳过（由规则的 loginRequired 字段控制）。
     *
     * 建议绑定：登录成功 SDK 回调 / Token 有效校验结果
     */
    val isLoggedIn: Boolean = true,

    // ========================================================================
    // 网络状态
    // ========================================================================

    /**
     * 当前网络连接类型。
     *
     * 预热管理器根据此值与 [WebCacheConfig.preloadWifiOnly] 及每条规则的
     * wifiOnly 字段做匹配，决定是否执行预热。
     *
     * 建议绑定：ConnectivityManager.registerDefaultNetworkCallback 回调
     *
     * @see NetworkType
     */
    val networkType: NetworkType = NetworkType.OTHER,

    // ========================================================================
    // 设备资源状态
    // ========================================================================

    /**
     * 系统是否处于低内存状态。
     *
     * 当系统发出低内存警告时（onTrimMemory level ≥ TRIM_MEMORY_RUNNING_LOW），
     * 预热应立即取消并销毁隐藏 WebView，避免触发 OOM 或系统杀进程。
     *
     * 预热管理器收到 true 后会：
     *   - 取消所有排队的预热任务
     *   - 立即 destroy 当前隐藏 WebView
     *   - 本次启动不再尝试恢复预热
     *
     * 建议绑定：ComponentCallbacks2.onTrimMemory(level)
     *   当 level >= TRIM_MEMORY_RUNNING_LOW 时设为 true
     */
    val isLowMemory: Boolean = false,

    /**
     * 设备是否处于低电量模式。
     *
     * 低电量模式下预热会增加 CPU 和网络消耗，加速电量下降。
     * 建议在此模式下跳过预热以节省电量。
     *
     * 建议绑定：PowerManager.isPowerSaveMode / ACTION_POWER_SAVE_MODE_CHANGED 广播
     */
    val isLowPowerMode: Boolean = false,

    /**
     * 当前设备是否为高内存设备。
     *
     * 用于判断是否允许执行并行预热（[WebCacheConfig.preloadParallelHighMemoryOnly]）。
     * 低内存设备即使 [WebCacheConfig.preloadParallelEnable] = true 也会被降级为串行模式。
     *
     * 建议阈值：
     *   - true  → 设备 RAM ≥ 6GB（highMemoryDevice）
     *   - false → 设备 RAM < 6GB
     *
     * 判定方式：ActivityManager.isLowRamDevice / ActivityManager.MemoryInfo.totalMem
     */
    val isHighMemoryDevice: Boolean = true
)
