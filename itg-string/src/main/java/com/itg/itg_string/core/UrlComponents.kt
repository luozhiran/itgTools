package com.itg.itg_string.core

/**
 * URL 解析结果——不可变数据容器。
 *
 * 一次解析，所有字段同时可用。调用方按需取值，不必重复解析。
 *
 * 以 [https://user:pass@www.example.com:8080/path/to/resource?query=param#fragment]
 * 为例，各字段对应值为：
 * - [scheme] = "https"
 * - [userInfo] = "user:pass"
 * - [host] = "www.example.com"
 * - [port] = 8080
 * - [authority] = "user:pass@www.example.com:8080"
 * - [path] = "/path/to/resource"
 * - [query] = "query=param"
 * - [fragment] = "fragment"
 *
 * @property rawUrl 解析时的原始输入
 * @property scheme 协议，如 "https"；相对 URL 时为 null
 * @property userInfo 用户信息，如 "user:pass"；不存在时为 null
 * @property host 主机名，如 "www.example.com"；不存在时为 null
 * @property port 端口号，未显式指定时为 -1
 * @property authority 授权部分（userInfo@host:port），不存在时为 null
 * @property path 路径，如 "/path/to/resource"；不存在时为 null
 * @property query 查询字符串（原始未解码），如 "query=param"；不存在时为 null
 * @property fragment 片段标识符，如 "fragment"；不存在时为 null
 * @property isAbsolute 是否为绝对 URL
 * @property isOpaque 是否为 opaque URL（如 "mailto:user@example.com"）
 *
 * @author ITG Team
 * @since 1.0.0
 */
data class UrlComponents(
    val rawUrl: String,
    val scheme: String?,
    val userInfo: String?,
    val host: String?,
    val port: Int,
    val authority: String?,
    val path: String?,
    val query: String?,
    val fragment: String?,
    val isAbsolute: Boolean,
    val isOpaque: Boolean
) {

    /**
     * 返回 "scheme://host/path" 形式的简洁标识字符串。
     *
     * 不包含端口、查询参数、片段和用户信息。
     * 例如 "https://www.example.com/path/to/resource"。
     */
    val schemeHostPath: String?
        get() {
            if (scheme == null || host == null) return null
            val sb = StringBuilder()
            sb.append(scheme).append("://").append(host)
            if (path != null) {
                sb.append(path)
            }
            return sb.toString()
        }

    /**
     * 返回 "host:port" 形式的主机端口串。
     *
     * 无显式指定端口时省略端口号，例如 "www.example.com"。
     * 例如 "www.example.com:8080"。
     */
    val hostPort: String?
        get() {
            if (host == null) return null
            return if (port > 0) "$host:$port" else host
        }

    /**
     * 判断是否为安全的 HTTPS 连接。
     */
    val isHttps: Boolean
        get() = scheme.equals("https", ignoreCase = true)

    /**
     * 判断当前 URL 是否使用了标准端口（http:80 / https:443）。
     */
    val isDefaultPort: Boolean
        get() {
            if (port <= 0) return true
            return when {
                scheme.equals("http", ignoreCase = true) -> port == 80
                scheme.equals("https", ignoreCase = true) -> port == 443
                else -> false
            }
        }

    /**
     * 将解析结果还原为规范化的 URL 字符串。
     *
     * 将各个组件按照 RFC 3986 结构重新拼接，方便日志输出和调试。
     */
    override fun toString(): String {
        val sb = StringBuilder()
        if (scheme != null) {
            sb.append(scheme).append("://")
        }
        if (!userInfo.isNullOrBlank()) {
            sb.append(userInfo).append("@")
        }
        if (host != null) {
            sb.append(host)
        }
        if (port > 0) {
            sb.append(":").append(port)
        }
        if (!path.isNullOrBlank()) {
            if (host != null && !path.startsWith("/")) {
                sb.append("/")
            }
            sb.append(path)
        }
        if (!query.isNullOrBlank()) {
            sb.append("?").append(query)
        }
        if (!fragment.isNullOrBlank()) {
            sb.append("#").append(fragment)
        }
        return sb.toString()
    }
}
