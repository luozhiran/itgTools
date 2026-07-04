package com.itg.itg_string.builder

import com.itg.itg_string.core.UrlComponents
import com.itg.itg_string.core.UrlParser
import com.itg.itg_string.query.QueryParams
import java.net.URI
import java.net.URISyntaxException

/**
 * URL 流式构造器。
 *
 * 用于**修改已有 URL 的某个字段**或**从头构造**新 URL。
 * 每次调用 [build] 或 [buildString] 都基于当前状态创建新对象，不会修改历史结果。
 *
 * 使用示例：
 *
 * **从已有 URL 修改：**
 * ```kotlin
 * val url = UrlBuilder("https://example.com/path")
 *     .scheme("http")
 *     .port(8080)
 *     .appendPath("extra")
 *     .setQueryParam("key", "value")
 *     .fragment("section1")
 *     .buildString()
 * // "http://example.com:8080/path/extra?key=value#section1"
 * ```
 *
 * **从空白构造：**
 * ```kotlin
 * val url = UrlBuilder()
 *     .scheme("https")
 *     .host("api.example.com")
 *     .path("/v1/users")
 *     .setQueryParam("page", "1")
 *     .buildString()
 * // "https://api.example.com/v1/users?page=1"
 * ```
 *
 * @author ITG Team
 * @since 1.0.0
 */
@Suppress("unused")
class UrlBuilder {

    private var scheme: String? = null
    private var userInfo: String? = null
    private var host: String? = null
    private var port: Int = -1
    private var path: String? = null
    private val queryParams = LinkedHashMap<String, MutableList<String>>()
    private var fragment: String? = null

    /**
     * 以已有 URL 为基础构造。
     *
     * @param baseUrl 已有 URL 字符串，解析失败则从空白构造
     */
    constructor(baseUrl: String?) {
        val c = UrlParser.parse(baseUrl)
        if (c != null) {
            scheme = c.scheme
            userInfo = c.userInfo
            host = c.host
            port = c.port
            path = c.path
            if (!c.query.isNullOrBlank()) {
                val parsed = QueryParams.parse(c.query)
                parsed.forEach { (key, values) ->
                    queryParams[key] = values.toMutableList()
                }
            }
            fragment = c.fragment
        }
    }

    /**
     * 以 [UrlComponents] 为基础构造。
     */
    constructor(components: UrlComponents?) {
        if (components != null) {
            scheme = components.scheme
            userInfo = components.userInfo
            host = components.host
            port = components.port
            path = components.path
            if (!components.query.isNullOrBlank()) {
                val parsed = QueryParams.parse(components.query)
                parsed.forEach { (key, values) ->
                    queryParams[key] = values.toMutableList()
                }
            }
            fragment = components.fragment
        }
    }

    /**
     * 从空白开始构造。
     */
    constructor()

    // ==================== 字段设置 ====================

    /**
     * 设置协议（scheme），如 "https"、"http"、"ftp"。
     */
    fun scheme(scheme: String?): UrlBuilder {
        this.scheme = scheme?.lowercase()
        return this
    }

    /**
     * 设置主机名（host）。
     */
    fun host(host: String?): UrlBuilder {
        this.host = host?.lowercase()
        return this
    }

    /**
     * 设置端口号。传 -1 表示移除端口。
     */
    fun port(port: Int): UrlBuilder {
        this.port = if (port > 0) port else -1
        return this
    }

    /**
     * 设置路径。
     *
     * 建议以 `/` 开头传绝对路径，如 "/api/v1/users"。
     */
    fun path(path: String?): UrlBuilder {
        this.path = path
        return this
    }

    /**
     * 追加路径段。
     *
     * 自动处理 `/` 连接问题：若当前 path 不以 `/` 结尾则先补 `/`，
     * 若 segment 以 `/` 开头则去掉前导 `/` 防止双斜杠。
     *
     * 使用示例：
     * ```kotlin
     * UrlBuilder("https://example.com/api")
     *     .appendPath("v1")
     *     .appendPath("users")
     *     .buildString()
     * // "https://example.com/api/v1/users"
     * ```
     */
    fun appendPath(segment: String): UrlBuilder {
        val cleanSegment = segment.trimStart('/')
        val current = path ?: ""
        path = if (current.endsWith("/")) {
            current + cleanSegment
        } else {
            "$current/$cleanSegment"
        }
        return this
    }

    /**
     * 设置用户信息（user:pass 格式）。
     */
    fun userInfo(userInfo: String?): UrlBuilder {
        this.userInfo = userInfo
        return this
    }

    /**
     * 设置片段标识符（# 之后的部分）。
     */
    fun fragment(fragment: String?): UrlBuilder {
        this.fragment = fragment
        return this
    }

    /**
     * 设置原始查询字符串（会覆盖所有已设置的参数）。
     *
     * 对输入执行 [QueryParams.parse] 以保证内部数据结构一致。
     */
    fun query(queryString: String?): UrlBuilder {
        queryParams.clear()
        if (!queryString.isNullOrBlank()) {
            val parsed = QueryParams.parse(queryString)
            parsed.forEach { (key, values) ->
                queryParams[key] = values.toMutableList()
            }
        }
        return this
    }

    // ==================== 查询参数操作 ====================

    /**
     * 设置单个查询参数（覆盖同 key 的已有值）。
     */
    fun setQueryParam(key: String, value: String): UrlBuilder {
        queryParams[key] = mutableListOf(value)
        return this
    }

    /**
     * 添加查询参数（保留同 key 的已有值，追加为新值）。
     */
    fun addQueryParam(key: String, value: String): UrlBuilder {
        queryParams.getOrPut(key) { mutableListOf() }.add(value)
        return this
    }

    /**
     * 移除指定 key 的所有查询参数。
     */
    fun removeQueryParam(key: String): UrlBuilder {
        queryParams.remove(key)
        return this
    }

    /**
     * 移除所有查询参数。
     */
    fun clearQueryParams(): UrlBuilder {
        queryParams.clear()
        return this
    }

    /**
     * 批量设置查询参数（覆盖）。
     */
    fun setQueryParams(params: Map<String, String>): UrlBuilder {
        params.forEach { (key, value) ->
            queryParams[key] = mutableListOf(value)
        }
        return this
    }

    /**
     * 获取当前所有查询参数（只读）。
     */
    fun getQueryParams(): Map<String, List<String>> = queryParams.toMap()

    // ==================== 构建 ====================

    /**
     * 构建 URL 字符串。
     *
     * @return 构造好的 URL 字符串，信息不足返回 null
     */
    fun buildString(): String? {
        if (scheme == null && host == null && path == null) return null

        val sb = StringBuilder()

        // scheme
        if (scheme != null) {
            sb.append(scheme).append("://")
        }

        // authority (userInfo@host:port)
        if (userInfo != null) {
            sb.append(userInfo).append("@")
        }

        if (host != null) {
            sb.append(host)
        }

        if (port > 0) {
            sb.append(":").append(port)
        }

        // path
        val currentPath = path
        if (!currentPath.isNullOrBlank()) {
            if (host != null && !currentPath.startsWith("/")) {
                sb.append("/")
            }
            sb.append(currentPath)
        }

        // query
        if (queryParams.isNotEmpty()) {
            val queryStr = QueryParams.buildMulti(queryParams)
            if (queryStr.isNotEmpty()) {
                sb.append("?").append(queryStr)
            }
        }

        // fragment
        if (!fragment.isNullOrBlank()) {
            sb.append("#").append(fragment)
        }

        return sb.toString()
    }

    /**
     * 构建为 [UrlComponents]。
     *
     * @return 解析后的 [UrlComponents]，构建失败返回 null
     */
    fun build(): UrlComponents? {
        val urlString = buildString() ?: return null
        return UrlParser.parse(urlString)
    }

    /**
     * 构建为 [java.net.URI]。
     *
     * @return [URI] 实例，构建失败返回 null
     */
    fun buildUri(): URI? {
        val urlString = buildString() ?: return null
        return try {
            URI(urlString)
        } catch (_: URISyntaxException) {
            null
        }
    }

    /**
     * 重置所有字段到空白状态。
     */
    fun reset(): UrlBuilder {
        scheme = null
        userInfo = null
        host = null
        port = -1
        path = null
        queryParams.clear()
        fragment = null
        return this
    }
}
