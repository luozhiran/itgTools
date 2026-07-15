package com.itg.itg_web_cache

/**
 * WebView 缓存预热与容器缓存策略的总配置数据类。
 *
 * 配置来源优先级（由 [WebCacheProviders] 负责合并）：
 *   1. 远程动态配置（服务端下发，可实时调整）
 *   2. 本地 Assets 配置（内置兜底）
 *   3. 硬编码默认值（本类的字段默认值）
 *
 * 配置生效时机：
 *   - 预热开关类字段（preloadEnable / killSwitch / URL 规则）：下次冷启动生效
 *   - 容器缓存策略字段（containerCacheMode / 白名单等）：立即生效于下次 applyToContainer 调用
 *   - 缓存清除版本号（clearCacheVersion / disableClearVersion）：下次冷启动时检测并执行一次性清理
 *
 * @see PreloadUrlRule 预热 URL 规则模型
 * @see CacheModeOption 缓存模式枚举
 * @see WebCacheClearPolicy 关闭预热后的缓存清除策略枚举
 */
data class WebCacheConfig(

    // ========================================================================
    // 预热总开关
    // ========================================================================

    /**
     * 预热功能总开关。
     *
     * - false：不执行任何后台预热（默认值，需灰度后远程开启）
     * - true：允许预热，但还需通过 killSwitch、URL 白名单、网络/登录态等条件校验
     *
     * 对应远程配置键：`web_cache_preload_enable`
     */
    val preloadEnable: Boolean = false,

    /**
     * 紧急熔断开关，优先级最高。
     *
     * 当该值为 true 时：
     *   - 立即停止所有预热任务（包括正在执行的）
     *   - 已排队的预热任务全部取消
     *   - 容器对外接口 [WebCacheRuntimeApi.applyToContainer] 也不启用缓存增强策略
     *
     * 适用场景：线上发现预热导致崩溃、内存异常或其他严重问题时紧急关闭。
     * 对应远程配置键：`web_cache_preload_kill_switch`
     */
    val killSwitch: Boolean = false,

    // ========================================================================
    // 预热 URL 配置
    // ========================================================================

    /**
     * 简单场景下的预热 URL 精确列表。
     *
     * 适用于只需要预热少量固定 URL 的场景。与 [preloadUrlRules] 合并使用：
     * 两者配置的 URL 都会被纳入预热候选队列。
     *
     * 对应远程配置键：`web_cache_preload_urls`
     */
    val preloadUrls: List<String> = emptyList(),

    /**
     * 复杂场景下的预热 URL 规则列表。
     *
     * 每条规则可独立配置优先级、冷却时间、网络条件、登录态要求等。
     * 与 [preloadUrls] 合并后，按 priority 降序排列，取前 [preloadMaxUrlCount] 条进入预热队列。
     *
     * 对应远程配置键：`web_cache_preload_url_rules`
     *
     * @see PreloadUrlRule
     */
    val preloadUrlRules: List<PreloadUrlRule> = emptyList(),

    /**
     * 预热 URL 黑名单，优先级高于 [preloadUrls] 和 [preloadUrlRules]。
     *
     * 命中黑名单的 URL 直接跳过预热，不进入队列。
     * 适合放入黑名单的页面：支付页、登录页、订单创建页、一次性 token 页面、
     * 隐私授权页、进入即触发副作用的页面。
     *
     * 支持完整 URL 精确匹配，也支持 host + pathPrefix 规则匹配。
     * 对应远程配置键：`web_cache_preload_url_blacklist`
     */
    val preloadUrlBlacklist: List<String> = emptyList(),

    /**
     * 单次 App 启动周期内最多预热的 URL 数量。
     *
     * 预热队列按 priority 排序后取前 N 条。该值越小，资源消耗越少，
     * 建议灰度初期设为 1，稳定后可逐步放宽到 3~5。
     *
     * 对应远程配置键：`web_cache_preload_max_url_count`
     */
    val preloadMaxUrlCount: Int = 1,

    // ========================================================================
    // 预热时序控制
    // ========================================================================

    /**
     * 首页进入后延迟多久开始预热（毫秒）。
     *
     * 目的：确保首页首屏渲染完成、核心数据加载完毕后再启动预热，
     * 避免预热 WebView 的创建和网络请求与首页关键任务争抢资源。
     *
     * 对应远程配置键：`web_cache_preload_delay_ms`
     */
    val preloadDelayMs: Long = 2_000L,

    /**
     * 单个 URL 预热的加载超时时间（毫秒）。
     *
     * 从 loadUrl 开始计时，超时后强制调用 stopLoading() 并 destroy() 隐藏 WebView。
     * 建议 10~15 秒，需大于目标页面典型加载耗时。
     *
     * 对应远程配置键：`web_cache_preload_timeout_ms`
     */
    val preloadTimeoutMs: Long = 15_000L,

    /**
     * onPageFinished 回调后的额外等待时间（毫秒），用于捕获异步资源加载。
     *
     * 原理：WebView 的 onPageFinished 仅表示 HTML 文档及同步资源加载完毕，
     * 图片懒加载、异步 CSS 字体、JS 延迟加载的资源可能在此之后才发起请求。
     * 等待这段时间可让更多资源写入 HTTP 缓存。
     *
     * 注意：等待时间过长会延长隐藏 WebView 的存活时间，增加内存占用。
     * 建议 2~5 秒，按目标页面特性调整。
     *
     * 对应远程配置键：`web_cache_preload_post_finish_delay_ms`
     */
    val preloadPostFinishDelayMs: Long = 3_000L,

    /**
     * 同一 URL 的最小预热间隔（冷却时间，毫秒）。
     *
     * 同一个 URL 在一次预热完成后，在该冷却时间内不会再次进入预热队列，
     * 避免短时间内反复预热消耗资源。默认 30 分钟。
     *
     * 对应远程配置键：`web_cache_preload_min_interval_ms`
     */
    val preloadMinIntervalMs: Long = 30 * 60 * 1_000L,

    // ========================================================================
    // 预热条件约束
    // ========================================================================

    /**
     * 是否仅在 WiFi 网络下执行串行预热。
     *
     * 默认 false，建议生产环境设为 true，避免消耗用户移动数据流量。
     * 注意：此字段仅控制串行预热（[preloadParallelEnable] = false 时生效），
     * 并行预热有独立的 [preloadParallelWifiOnly] 控制。
     *
     * 对应远程配置键：`web_cache_preload_wifi_only`
     */
    val preloadWifiOnly: Boolean = false,

    /**
     * 是否要求用户已登录后才允许预热。
     *
     * 原因：部分 H5 页面可能依赖登录态才能正确加载和缓存，
     * 未登录时预热可能导致缓存空壳页面或错误页。
     *
     * 对应远程配置键：`web_cache_preload_login_required`
     */
    val preloadLoginRequired: Boolean = true,

    // ========================================================================
    // 并行预热（实验性）
    // ========================================================================

    /**
     * 是否启用并行预热模式（实验性功能）。
     *
     * 并行模式下可同时创建多个隐藏 WebView 预热不同 URL，加快预热速度。
     * 但会显著增加内存峰值（N × ~100MB）和 CPU 负载。
     *
     * 注意：
     *   - 仅建议在高端设备（RAM ≥ 6GB）上开启
     *   - 必须配合 [preloadParallelHighMemoryOnly] 使用
     *   - 灰度阶段不建议开启
     *
     * 对应远程配置键：`web_cache_preload_parallel_enable`
     */
    val preloadParallelEnable: Boolean = false,

    /**
     * 并行预热模式下同时预热的 WebView 数量上限。
     *
     * 仅在 [preloadParallelEnable] = true 时生效。
     * 每增加一个并发 WebView，内存峰值增加约 80~150MB。
     * 建议值：2~3，不建议超过 3。
     *
     * 对应远程配置键：`web_cache_preload_parallel_count`
     */
    val preloadParallelCount: Int = 1,

    /**
     * 并行预热模式下是否仅 WiFi 下执行。
     *
     * 并行预热会产生更大流量消耗（N × 单页流量），
     * 因此默认 true，比串行预热更严格。
     *
     * 对应远程配置键：`web_cache_preload_parallel_wifi_only`
     */
    val preloadParallelWifiOnly: Boolean = true,

    /**
     * 并行预热是否仅在高端内存设备上开启。
     *
     * 当该值为 true 时，低内存设备即使 [preloadParallelEnable] = true
     * 也不会执行并行预热，自动退化为串行模式。
     *
     * 对应远程配置键：`web_cache_preload_parallel_high_memory_only`
     */
    val preloadParallelHighMemoryOnly: Boolean = true,

    // ========================================================================
    // 缓存清除
    // ========================================================================

    /**
     * 通用 WebView 缓存清除版本号。
     *
     * 机制：远端下发版本号 → 客户端与本地已处理的版本比较 →
     * 不一致时执行一次性 HTTP 缓存清除 → 写入本地版本号。
     *
     * 适用场景：H5 发布异常导致缓存污染、CDN 缓存头配置错误已修复、
     * 灰度出问题需要强制用户重新拉取资源。
     *
     * 注意：[clearCache] 是应用级操作，会影响本应用所有 WebView 的 HTTP 缓存，
     * 不只是预热产生的缓存。因此不建议频繁触发。
     *
     * 对应远程配置键：`web_cache_preload_clear_cache_version`
     */
    val clearCacheVersion: String? = null,

    /**
     * 关闭预热功能后，对已产生的历史缓存的处理策略。
     *
     * 默认 [WebCacheClearPolicy.NONE]：关闭预热但保留历史缓存，
     * 因为这些缓存对正常浏览仍然有用，且重新开启预热时无需重新下载。
     *
     * 对应远程配置键：`web_cache_preload_disable_clear_policy`
     *
     * @see WebCacheClearPolicy
     */
    val disableClearPolicy: WebCacheClearPolicy = WebCacheClearPolicy.NONE,

    /**
     * 关闭预热后触发一次性缓存清除的独立版本号。
     *
     * 机制与 [clearCacheVersion] 相同：远端版本号变化时执行一次清除。
     * 独立于 [clearCacheVersion] 的原因：
     *   - 职责分离：关闭预热清缓存 vs 通用缓存清除
     *   - 避免运维误以为"关闭预热 = 清缓存"
     *
     * 对应远程配置键：`web_cache_preload_disable_clear_version`
     */
    val disableClearVersion: String? = null,

    // ========================================================================
    // 正式容器缓存策略
    // ========================================================================

    /**
     * 正式 WebView 容器缓存策略总开关。
     *
     * 控制 [WebCacheRuntimeApi.applyToContainer] 是否改写容器的 cacheMode。
     * false 时接口快速返回，不改写容器原有的 WebSettings。
     *
     * 注意：此开关与预热开关 [preloadEnable] 独立。
     * 预热关闭不代表容器缓存策略也要关闭（历史缓存仍然可用）。
     *
     * 对应远程配置键：`web_cache_container_cache_enable`
     */
    val containerCacheEnable: Boolean = false,

    /**
     * 正式容器加载时的缓存模式。
     *
     * - [CacheModeOption.DEFAULT]：LOAD_DEFAULT（推荐默认值），按 HTTP 协议处理缓存
     * - [CacheModeOption.CACHE_ELSE_NETWORK]：LOAD_CACHE_ELSE_NETWORK，优先展示缓存，后台异步更新
     * - [CacheModeOption.NO_CACHE]：LOAD_NO_CACHE，完全跳过缓存，始终拉取网络
     *
     * 推荐：
     *   - 低风险页面（首页、列表页）使用 CACHE_ELSE_NETWORK 加速展示
     *   - 实时页面（行情、支付、订单）使用 DEFAULT 或 NO_CACHE
     *
     * 对应远程配置键：`web_cache_container_cache_mode`
     *
     * @see CacheModeOption
     */
    val containerCacheMode: CacheModeOption = CacheModeOption.DEFAULT,

    /**
     * 允许应用缓存策略的正式容器 URL 白名单。
     *
     * 只有 URL 命中该白名单时，[WebCacheRuntimeApi.applyToContainer] 才会改写 cacheMode。
     * URL 不在白名单内 → 接口快速返回，不改写容器原有 WebSettings。
     *
     * 支持完整 URL 和前缀匹配。
     * 对应远程配置键：`web_cache_container_url_whitelist`
     */
    val containerUrlWhitelist: List<String> = emptyList(),

    /**
     * 允许应用缓存策略的业务场景白名单。
     *
     * 按 scene 维度控制哪些业务场景可以使用缓存策略。
     * 为空时不做 scene 限制。与 [containerUrlWhitelist] 的关系是 AND：
     * URL 和 scene 都必须满足才会应用缓存策略。
     *
     * 对应远程配置键：`web_cache_container_scene_whitelist`
     */
    val containerSceneWhitelist: List<String> = emptyList(),

    /**
     * 禁止应用缓存策略的业务场景黑名单。
     *
     * 优先级高于 [containerSceneWhitelist]。
     * 适合放入黑名单的场景：支付流程、订单创建、登录、隐私授权。
     *
     * 对应远程配置键：`web_cache_container_scene_blacklist`
     */
    val containerSceneBlacklist: List<String> = emptyList(),

    /**
     * 是否允许覆盖业务代码已显式设置的 cacheMode。
     *
     * 默认 false（保守策略）：如果业务代码已经手动设置了特殊的 cacheMode，
     * 接口不覆盖，避免破坏业务意图。
     *
     * 设为 true 时，接口以 [containerCacheMode] 强制覆盖。
     *
     * 对应远程配置键：`web_cache_container_force_override`
     */
    val containerForceOverride: Boolean = false,

    // ========================================================================
    // 安全校验
    // ========================================================================

    /**
     * 允许预热的业务域名白名单。
     *
     * 安全校验：预热 URL 的 host 必须在此列表中，防止远程配置被篡改后
     * 预热恶意域名。同时也是 [UrlRuleMatcher] 域名匹配的依据。
     *
     * 为空时不允许任何 URL 通过校验，生产环境必须配置业务域名。
     * 对应远程配置键：`web_cache_allowed_hosts`
     */
    val allowedHosts: List<String> = emptyList()
)
