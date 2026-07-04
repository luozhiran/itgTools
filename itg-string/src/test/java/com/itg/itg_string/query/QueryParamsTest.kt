package com.itg.itg_string.query

import org.junit.Assert.*
import org.junit.Test
import java.nio.charset.Charset

/**
 * [QueryParams] 单元测试。
 *
 * 覆盖查询字符串解析、构建、编解码等场景。
 */
class QueryParamsTest {

    // ==================== parse ====================

    @Test
    fun parse_standard() {
        val result = QueryParams.parse("q=kotlin&page=1&sort=desc")
        assertEquals(3, result.size)
        assertEquals(listOf("kotlin"), result["q"])
        assertEquals(listOf("1"), result["page"])
        assertEquals(listOf("desc"), result["sort"])
    }

    @Test
    fun parse_withLeadingQuestionMark() {
        val result = QueryParams.parse("?q=kotlin&page=1")
        assertEquals(2, result.size)
        assertEquals(listOf("kotlin"), result["q"])
        assertEquals(listOf("1"), result["page"])
    }

    @Test
    fun parse_duplicateKeys() {
        val result = QueryParams.parse("tag=android&tag=jvm&tag=kotlin")
        assertEquals(1, result.size)
        assertEquals(listOf("android", "jvm", "kotlin"), result["tag"])
    }

    @Test
    fun parse_keyWithoutValue() {
        val result = QueryParams.parse("flag&q=kotlin")
        assertEquals(2, result.size)
        assertEquals(listOf(""), result["flag"])
        assertEquals(listOf("kotlin"), result["q"])
    }

    @Test
    fun parse_emptyValue() {
        val result = QueryParams.parse("key=&foo=bar")
        assertEquals(listOf(""), result["key"])
        assertEquals(listOf("bar"), result["foo"])
    }

    @Test
    fun parse_encodedValues() {
        val result = QueryParams.parse("q=hello+world&name=John%20Doe")
        assertEquals(listOf("hello world"), result["q"])
        assertEquals(listOf("John Doe"), result["name"])
    }

    @Test
    fun parse_null_returnsEmptyMap() {
        assertTrue(QueryParams.parse(null).isEmpty())
    }

    @Test
    fun parse_empty_returnsEmptyMap() {
        assertTrue(QueryParams.parse("").isEmpty())
    }

    @Test
    fun parse_blank_returnsEmptyMap() {
        assertTrue(QueryParams.parse("   ").isEmpty())
    }

    @Test
    fun parse_onlyQuestionMark_returnsEmptyMap() {
        assertTrue(QueryParams.parse("?").isEmpty())
    }

    @Test
    fun parse_onlyAmpersand_returnsEmptyMap() {
        assertTrue(QueryParams.parse("&").isEmpty())
    }

    @Test
    fun parse_preservesInsertionOrder() {
        val result = QueryParams.parse("z=last&a=first&m=middle")
        val keys = result.keys.toList()
        assertEquals("z", keys[0])
        assertEquals("a", keys[1])
        assertEquals("m", keys[2])
    }

    // ==================== getFirst ====================

    @Test
    fun getFirst_existing() {
        assertEquals("kotlin", QueryParams.getFirst("q=kotlin&page=2", "q"))
    }

    @Test
    fun getFirst_nonExisting() {
        assertNull(QueryParams.getFirst("q=kotlin&page=2", "size"))
    }

    @Test
    fun getFirst_nullInput() {
        assertNull(QueryParams.getFirst(null, "key"))
    }

    // ==================== getAll ====================

    @Test
    fun getAll_singleValue() {
        val values = QueryParams.getAll("q=kotlin&page=2", "q")
        assertEquals(listOf("kotlin"), values)
    }

    @Test
    fun getAll_multiValue() {
        val values = QueryParams.getAll("tag=a&tag=b&tag=c", "tag")
        assertEquals(listOf("a", "b", "c"), values)
    }

    @Test
    fun getAll_nonExisting() {
        assertTrue(QueryParams.getAll("q=kotlin", "missing").isEmpty())
    }

    // ==================== getParam (from full URL) ====================

    @Test
    fun getParam_fromFullUrl() {
        assertEquals(
            "kotlin",
            QueryParams.getParam("https://example.com/search?q=kotlin&page=1", "q")
        )
    }

    @Test
    fun getParam_noQuery_returnsNull() {
        assertNull(QueryParams.getParam("https://example.com/path", "q"))
    }

    @Test
    fun getParams_fromFullUrl_multiValue() {
        val values = QueryParams.getParams("https://example.com?tag=a&tag=b", "tag")
        assertEquals(listOf("a", "b"), values)
    }

    // ==================== build ====================

    @Test
    fun build_standard() {
        val query = QueryParams.build(mapOf("q" to "kotlin", "page" to "1"))
        // 顺序不保证（HashMap），只验证包含关系
        assertTrue(query.contains("q=kotlin"))
        assertTrue(query.contains("page=1"))
        assertTrue(query.length > 0)
    }

    @Test
    fun build_empty() {
        assertEquals("", QueryParams.build(emptyMap()))
    }

    @Test
    fun build_withSpecialChars() {
        val query = QueryParams.build(mapOf("q" to "hello world", "filter" to "a&b"))
        // 值应该被编码
        assertTrue(query.contains("hello+world") || query.contains("hello%20world"))
    }

    // ==================== buildMulti ====================

    @Test
    fun buildMulti_standard() {
        val params = linkedMapOf(
            "tag" to listOf("a", "b"),
            "q" to listOf("kotlin")
        )
        val query = QueryParams.buildMulti(params)
        assertTrue(query.contains("tag=a"))
        assertTrue(query.contains("tag=b"))
        assertTrue(query.contains("q=kotlin"))
    }

    @Test
    fun buildMulti_emptyValue() {
        val params = linkedMapOf("flag" to listOf(""))
        val query = QueryParams.buildMulti(params)
        assertEquals("flag=", query)
    }

    @Test
    fun buildMulti_emptyList() {
        val params = linkedMapOf("flag" to emptyList<String>())
        val query = QueryParams.buildMulti(params)
        assertEquals("flag", query)
    }

    @Test
    fun buildMulti_emptyMap() {
        assertEquals("", QueryParams.buildMulti(emptyMap()))
    }

    // ==================== containsKey ====================

    @Test
    fun containsKey_true() {
        assertTrue(QueryParams.containsKey("q=kotlin&page=1", "q"))
    }

    @Test
    fun containsKey_false() {
        assertFalse(QueryParams.containsKey("q=kotlin&page=1", "size"))
    }

    // ==================== count ====================

    @Test
    fun count_standard() {
        assertEquals(3, QueryParams.count("a=1&b=2&c=3"))
    }

    @Test
    fun count_duplicateKeys() {
        assertEquals(1, QueryParams.count("tag=a&tag=b&tag=c"))
    }

    @Test
    fun count_null() {
        assertEquals(0, QueryParams.count(null))
    }

    // ==================== encode / decode ====================

    @Test
    fun encode_simple() {
        val encoded = QueryParams.encode("hello world")
        assertTrue(encoded.contains("hello"))
        // URLEncoder 用 + 替代空格
        assertEquals("hello+world", encoded)
    }

    @Test
    fun encode_specialChars() {
        val encoded = QueryParams.encode("a&b=c")
        assertFalse(encoded.contains("&"))
        assertFalse(encoded.contains("="))
    }

    @Test
    fun decode_simple() {
        assertEquals("hello world", QueryParams.decode("hello+world"))
    }

    @Test
    fun decode_percentEncoded() {
        assertEquals("a&b=c", QueryParams.decode("a%26b%3Dc"))
    }

    @Test
    fun encodeDecode_roundTrip() {
        val original = "name=John Doe&city=New York"
        val encoded = QueryParams.encode(original)
        val decoded = QueryParams.decode(encoded)
        assertEquals(original, decoded)
    }

    // ==================== 多字符集解码 ====================

    @Test
    fun decode_utf8() {
        // UTF-8 编码的 "中文"
        assertEquals("中文", QueryParams.decode("%E4%B8%AD%E6%96%87"))
    }

    @Test
    fun decode_gbk() {
        // GBK 编码的 "中文" → %D6%D0%CE%C4
        val decoded = QueryParams.decode("%D6%D0%CE%C4", Charset.forName("GBK"))
        assertEquals("中文", decoded)
    }

    @Test
    fun decode_shiftJis() {
        // Shift_JIS 编码的 "日本語"
        val decoded = QueryParams.decode("%93%FA%96%7B%8C%EA", Charset.forName("Shift_JIS"))
        assertEquals("日本語", decoded)
    }

    @Test
    fun decode_utf8Default() {
        // 默认无 charset 参数时走 UTF-8
        assertEquals(QueryParams.decode("%E4%B8%AD%E6%96%87"),
                     QueryParams.decode("%E4%B8%AD%E6%96%87", Charsets.UTF_8))
    }

    @Test
    fun parse_withCharset_gbk() {
        val params = QueryParams.parse("q=%D6%D0%CE%C4&page=1", Charset.forName("GBK"))
        assertEquals(listOf("中文"), params["q"])
        assertEquals(listOf("1"), params["page"])
    }

    @Test
    fun parse_withCharset_defaultUtf8() {
        val paramsUtf8 = QueryParams.parse("q=%E4%B8%AD%E6%96%87")
        val paramsExplicit = QueryParams.parse("q=%E4%B8%AD%E6%96%87", Charsets.UTF_8)
        assertEquals(paramsUtf8["q"], paramsExplicit["q"])
    }
}
