package com.itg.itg_string.intent

import android.content.Intent
import android.os.Bundle
import java.nio.charset.Charset

/**
 * Intent 流式构造器——将多个数据源（URL / Map / Bundle）链式合并到一个 [Intent]。
 *
 * 内置安全与错误捕获机制：
 * - **非致命错误**：所有异常均被捕获并记录到内部错误列表，链式调用不会中断
 * - **静默跳过**：`null` / 空字符串 / 空 Map / 空 Bundle 直接跳过，不视为错误
 * - **错误追溯**：可通过 [errors] / [hasErrors] 查询完整错误列表，或通过 [onError] 注册实时回调
 * - **类型安全**：`fromMap` 精确重载，编译期即可区分 String / List / 异构 Map
 *
 * 典型使用场景：
 *
 * **场景一：多源合并，前缀隔离**
 * ```kotlin
 * val intent = IntentBuilder()
 *     .fromUrl("https://api.com?source=deeplink&campaign=summer", prefix = "url_")
 *     .fromMap(mapOf("userId" to 12345, "isVip" to true), prefix = "cfg_")
 *     .fromBundle(savedState)
 *     .action(Intent.ACTION_VIEW)
 *     .build()
 * ```
 *
 * **场景二：多 URL 聚合 + 错误感知**
 * ```kotlin
 * val builder = IntentBuilder()
 *     .onError { e -> Log.w(TAG, "IntentBuilder: $e") }
 *     .fromUrl(dynamicUrl1, prefix = "a_")
 *     .fromUrl(dynamicUrl2, prefix = "b_")
 *
 * if (builder.hasErrors()) {
 *     // 降级处理：只使用成功解析的部分
 * }
 * val intent = builder.build()
 * ```
 *
 * **场景三：注入已有 Intent**
 * ```kotlin
 * IntentBuilder()
 *     .fromMap(extras, prefix = "extra_")
 *     .into(existingIntent)
 * ```
 *
 * @author ITG Team
 * @since 1.0.0
 */
@Suppress("unused")
class IntentBuilder {

    /**
     * 构建过程中的错误信息。
     *
     * @property source 数据源类型（"fromUrl" / "fromMap" / "fromBundle" / "put"）
     * @property input  输入摘要（截断至 100 字符）
     * @property message 错误描述
     */
    data class BuildError(
        val source: String,
        val input: String,
        val message: String
    ) {
        override fun toString(): String = "[IntentBuilder] $source 失败: $message | input=$input"
    }

    /**
     * 错误回调接口（函数式单方法接口，Kotlin 支持 SAM 转换）。
     *
     * 使用示例：
     * ```kotlin
     * IntentBuilder().onError { error -> crashReporter.log(error.toString()) }
     * ```
     */
    fun interface ErrorHandler {
        fun onError(error: BuildError)
    }

    // ── 内部状态 ──────────────────────────────────────────────

    /** (keyPrefix, Bundle) 有序列表，保持添加顺序 */
    private val sources = mutableListOf<Pair<String, Bundle>>()

    /** 错误日志 */
    private val errorLog = mutableListOf<BuildError>()

    /** 错误回调 */
    private var errorHandler: ErrorHandler? = null

    /** Intent action */
    private var intentAction: String? = null

    // ==================================================================
    // 错误处理
    // ==================================================================

    /**
     * 注册错误回调，每次发生构建错误时实时触发。
     *
     * 多次调用会覆盖前一个 handler，最后一个生效。
     *
     * @param handler 错误回调；传 null 清空
     * @return 自身（链式调用）
     */
    fun onError(handler: ErrorHandler?): IntentBuilder {
        this.errorHandler = handler
        return this
    }

    /**
     * 返回当前累计的所有错误（只读副本）。
     */
    fun errors(): List<BuildError> = errorLog.toList()

    /**
     * 是否存在错误。
     */
    fun hasErrors(): Boolean = errorLog.isNotEmpty()

    // ==================================================================
    // 数据源：URL
    // ==================================================================

    /**
     * 从 URL 查询参数提取键值对，默认 UTF-8 解码。
     *
     * 委托 [UrlIntentBuilder.toBundle] 完成解析。
     * URL 为 null / 空 / 空白 → 静默跳过。
     * URL 非法无法解析 → 记录错误，跳过。
     *
     * @param url    待解析的 URL 字符串
     * @param prefix 追加到该 URL 所有参数 key 前的前缀，空字符串表示无前缀
     * @return 自身（链式调用）
     */
    @JvmOverloads
    fun fromUrl(url: String?, prefix: String = ""): IntentBuilder {
        if (url.isNullOrBlank()) return this
        val bundle = UrlIntentBuilder.toBundle(url)
        return when {
            bundle == null -> report("fromUrl", url.take(100), "URL 解析失败，无法提取查询参数")
            bundle.isEmpty -> this  // URL 合法但无查询参数，静默跳过
            else -> addSource(prefix, bundle)
        }
    }

    /**
     * 从 URL 查询参数提取键值对，使用指定字符集解码。
     *
     * @param url     待解析的 URL 字符串
     * @param charset 解码字符集
     * @param prefix  追加到该 URL 所有参数 key 前的前缀
     * @return 自身（链式调用）
     */
    @JvmOverloads
    fun fromUrl(url: String?, charset: Charset, prefix: String = ""): IntentBuilder {
        if (url.isNullOrBlank()) return this
        val bundle = UrlIntentBuilder.toBundle(url, charset)
        return when {
            bundle == null -> report("fromUrl", url.take(100), "URL 解析失败 (charset=$charset)")
            bundle.isEmpty -> this  // URL 合法但无查询参数，静默跳过
            else -> addSource(prefix, bundle)
        }
    }

    // ==================================================================
    // 数据源：Map
    // ==================================================================

    /**
     * 将单值 [Map] 转换为 Bundle 并加入构建管线。
     *
     * 每个 entry 存储为 Bundle.putString。空 Map 静默跳过。
     *
     * @param params 参数映射（key → value）
     * @param prefix 追加到所有 key 前的前缀
     * @return 自身（链式调用）
     */
    @JvmOverloads
    @JvmName("fromStringMap")
    fun fromMap(params: Map<String, String>, prefix: String = ""): IntentBuilder {
        if (params.isEmpty()) return this
        return addSource(prefix, UrlIntentBuilder.toBundle(params))
    }

    /**
     * 将多值 [Map] 转换为 Bundle 并加入构建管线。
     *
     * 单值 list → putString，多值 list → putStringArrayList。空 Map 静默跳过。
     *
     * @param params 参数映射（key → List<value>）
     * @param prefix 追加到所有 key 前的前缀
     * @return 自身（链式调用）
     */
    @JvmOverloads
    @JvmName("fromListMap")
    fun fromMap(params: Map<String, List<String>>, prefix: String = ""): IntentBuilder {
        if (params.isEmpty()) return this
        return addSource(prefix, UrlIntentBuilder.toBundle(params))
    }

    /**
     * 将异构值 [Map] 转换为 Bundle 并加入构建管线。
     *
     * 类型路由规则与 [UrlIntentBuilder.toBundle]`(Map<String, *>)` 一致：
     * String / Int / Long / Boolean / Float / Double / List<String> / List<Int> → 对应精准类型，
     * null → 跳过，未知类型 → toString() 兜底。
     *
     * @param params 参数映射（key → Any?）
     * @param prefix 追加到所有 key 前的前缀
     * @return 自身（链式调用）
     */
    @JvmOverloads
    @JvmName("fromWildcardMap")
    fun fromMap(params: Map<String, *>, prefix: String = ""): IntentBuilder {
        if (params.isEmpty()) return this
        return addSource(prefix, UrlIntentBuilder.toBundle(params))
    }

    // ==================================================================
    // 数据源：Bundle
    // ==================================================================

    /**
     * 将已有 [Bundle] 加入构建管线。
     *
     * Bundle 为 null 或 isEmpty → 静默跳过。
     * 合并时保留原始 Bundle 中的所有值类型。
     *
     * @param bundle 已有的 Bundle 实例
     * @param prefix 追加到该 Bundle 所有 key 前的前缀
     * @return 自身（链式调用）
     */
    @JvmOverloads
    fun fromBundle(bundle: Bundle?, prefix: String = ""): IntentBuilder {
        if (bundle == null || bundle.isEmpty) return this
        return addSource(prefix, bundle)
    }

    // ==================================================================
    // 便捷方法：单键值对
    // ==================================================================

    /**
     * 添加一个 String 键值对。
     */
    fun put(key: String, value: String): IntentBuilder {
        return addSource("", Bundle().apply { putString(key, value) })
    }

    /**
     * 添加一个 Int 键值对。
     */
    fun put(key: String, value: Int): IntentBuilder {
        return addSource("", Bundle().apply { putInt(key, value) })
    }

    /**
     * 添加一个 Long 键值对。
     */
    fun put(key: String, value: Long): IntentBuilder {
        return addSource("", Bundle().apply { putLong(key, value) })
    }

    /**
     * 添加一个 Boolean 键值对。
     */
    fun put(key: String, value: Boolean): IntentBuilder {
        return addSource("", Bundle().apply { putBoolean(key, value) })
    }

    /**
     * 添加一个 Float 键值对。
     */
    fun put(key: String, value: Float): IntentBuilder {
        return addSource("", Bundle().apply { putFloat(key, value) })
    }

    /**
     * 添加一个 Double 键值对。
     */
    fun put(key: String, value: Double): IntentBuilder {
        return addSource("", Bundle().apply { putDouble(key, value) })
    }

    // ==================================================================
    // 配置
    // ==================================================================

    /**
     * 设置 Intent 的 [Intent.setAction]。
     *
     * @param action Action 字符串，如 [Intent.ACTION_VIEW]；传 null 清空
     * @return 自身（链式调用）
     */
    fun action(action: String?): IntentBuilder {
        this.intentAction = action
        return this
    }

    // ==================================================================
    // 构建与输出
    // ==================================================================

    /**
     * 构建 [Intent]。
     *
     * 将所有已收集的数据源合并为一个 Bundle 并注入 Intent.extras。
     * 无数据源时返回不含 extras 的 Intent。
     *
     * @return 新的 Intent 实例，action 为 [action] 设置的值（默认 null）
     */
    fun build(): Intent {
        val intent = Intent(intentAction)
        if (sources.isNotEmpty()) {
            intent.putExtras(mergeAll())
        }
        return intent
    }

    /**
     * 将所有已收集的数据源合并后注入已有 [Intent]。
     *
     * 合并结果通过 [Intent.putExtras] 写入，**会覆盖** Intent 中原有的同名 key。
     * 无数据源时原样返回 intent。
     *
     * @param intent 目标 Intent
     * @return 传入的 Intent（支持链式调用）
     */
    fun into(intent: Intent): Intent {
        if (sources.isNotEmpty()) {
            intent.putExtras(mergeAll())
        }
        return intent
    }

    // ==================================================================
    // 生命周期
    // ==================================================================

    /**
     * 重置所有状态（数据源、错误日志、action），回到初始空白状态。
     *
     * 可用于复用同一个 builder 实例构造新的 Intent。
     *
     * @return 自身（链式调用）
     */
    fun reset(): IntentBuilder {
        sources.clear()
        errorLog.clear()
        intentAction = null
        return this
    }

    /**
     * 当前已收集的数据源数量（用于调试 / 断言）。
     */
    fun sourceCount(): Int = sources.size

    // ==================================================================
    // 内部实现
    // ==================================================================

    /** 添加数据源 + Bundle */
    private fun addSource(prefix: String, bundle: Bundle): IntentBuilder {
        sources.add(prefix to bundle)
        return this
    }

    /** 按顺序合并所有 (prefix, Bundle) */
    private fun mergeAll(): Bundle {
        var merged = Bundle()
        for ((prefix, bundle) in sources) {
            merged = UrlIntentBuilder.mergeBundles(merged, bundle, prefix)
        }
        return merged
    }

    /** 记录错误并触发回调，返回 this 以支持链式调用 */
    private fun report(source: String, input: String, message: String): IntentBuilder {
        val error = BuildError(source, input, message)
        errorLog.add(error)
        try {
            errorHandler?.onError(error)
        } catch (_: Exception) {
            // 用户回调异常不影响构建流程
        }
        return this
    }
}
