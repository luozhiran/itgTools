package com.itg.itg_string.intent

import android.content.Intent
import android.os.Bundle
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.charset.Charset

/**
 * [UrlIntentBuilder] 单元测试。
 *
 * 覆盖 toBundle（URL → Bundle）和 putQueryExtras（URL → Intent extras）两类场景。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UrlIntentBuilderTest {

    // ==================== toBundle：标准场景 ====================

    @Test
    fun toBundle_standardParams() {
        val bundle = UrlIntentBuilder.toBundle("https://api.prod.com:443/v2/users?active=true&role=admin")

        assertNotNull(bundle)
        assertEquals(2, bundle!!.size())
        assertEquals("true", bundle.getString("active"))
        assertEquals("admin", bundle.getString("role"))
    }

    @Test
    fun toBundle_singleParam() {
        val bundle = UrlIntentBuilder.toBundle("https://example.com?q=kotlin")

        assertNotNull(bundle)
        assertEquals("kotlin", bundle!!.getString("q"))
    }

    @Test
    fun toBundle_multiValueParams() {
        val bundle = UrlIntentBuilder.toBundle("https://example.com?tag=android&tag=jvm&tag=kotlin")

        assertNotNull(bundle)
        val tags = bundle!!.getStringArrayList("tag")
        assertNotNull(tags)
        assertEquals(listOf("android", "jvm", "kotlin"), tags)
    }

    @Test
    fun toBundle_mixedSingleAndMultiValue() {
        val bundle = UrlIntentBuilder.toBundle("https://example.com?q=kotlin&tag=a&tag=b&page=1")

        assertNotNull(bundle)
        assertEquals(3, bundle!!.size())
        assertEquals("kotlin", bundle.getString("q"))
        assertEquals("1", bundle.getString("page"))
        assertEquals(listOf("a", "b"), bundle.getStringArrayList("tag"))
    }

    @Test
    fun toBundle_keyWithoutValue() {
        val bundle = UrlIntentBuilder.toBundle("https://example.com?flag")

        assertNotNull(bundle)
        assertEquals("", bundle!!.getString("flag"))
    }

    @Test
    fun toBundle_keyWithoutValueWithOthers() {
        val bundle = UrlIntentBuilder.toBundle("https://example.com?debug&q=kotlin&verbose")

        assertNotNull(bundle)
        assertEquals("", bundle!!.getString("debug"))
        assertEquals("kotlin", bundle.getString("q"))
        assertEquals("", bundle.getString("verbose"))
    }

    @Test
    fun toBundle_emptyValue() {
        val bundle = UrlIntentBuilder.toBundle("https://example.com?key=")

        assertNotNull(bundle)
        assertEquals("", bundle!!.getString("key"))
    }

    @Test
    fun toBundle_numericValues() {
        val bundle = UrlIntentBuilder.toBundle("https://example.com?count=5&price=9.99&id=007")

        assertNotNull(bundle)
        // 全部按字符串存储
        assertEquals("5", bundle!!.getString("count"))
        assertEquals("9.99", bundle.getString("price"))
        assertEquals("007", bundle.getString("id"))  // 保留前导零
    }

    @Test
    fun toBundle_booleanLikeValues() {
        val bundle = UrlIntentBuilder.toBundle("https://example.com?active=true&debug=false&flag=1")

        assertNotNull(bundle)
        assertEquals("true", bundle!!.getString("active"))
        assertEquals("false", bundle.getString("debug"))
        assertEquals("1", bundle.getString("flag"))
    }

    @Test
    fun toBundle_encodedParams() {
        val bundle = UrlIntentBuilder.toBundle("https://example.com?name=John%20Doe&city=New+York")

        assertNotNull(bundle)
        assertEquals("John Doe", bundle!!.getString("name"))
        assertEquals("New York", bundle.getString("city"))
    }

    @Test
    fun toBundle_urlWithLeadingQuestionMarkInQuery() {
        // QueryParams.parse 自动去除前导 ?
        val bundle = UrlIntentBuilder.toBundle("https://example.com?q=kotlin&page=1")

        assertNotNull(bundle)
        assertEquals("kotlin", bundle!!.getString("q"))
        assertEquals("1", bundle.getString("page"))
    }

    // ==================== toBundle：边界/异常条件 ====================

    @Test
    fun toBundle_noQuery_returnsEmptyBundle() {
        val bundle = UrlIntentBuilder.toBundle("https://example.com/path")

        assertNotNull(bundle)
        assertTrue(bundle!!.isEmpty)
    }

    @Test
    fun toBundle_queryIsOnlyQuestionMark_returnsEmptyBundle() {
        val bundle = UrlIntentBuilder.toBundle("https://example.com?")

        assertNotNull(bundle)
        assertTrue(bundle!!.isEmpty)
    }

    @Test
    fun toBundle_urlWithFragmentOnly() {
        val bundle = UrlIntentBuilder.toBundle("https://example.com/doc#section1")

        assertNotNull(bundle)
        assertTrue(bundle!!.isEmpty)
    }

    @Test
    fun toBundle_urlWithBothQueryAndFragment() {
        val bundle = UrlIntentBuilder.toBundle("https://example.com/search?q=kotlin#top")

        assertNotNull(bundle)
        assertEquals("kotlin", bundle!!.getString("q"))
    }

    @Test
    fun toBundle_null_returnsNull() {
        assertNull(UrlIntentBuilder.toBundle(null))
    }

    @Test
    fun toBundle_empty_returnsNull() {
        assertNull(UrlIntentBuilder.toBundle(""))
    }

    @Test
    fun toBundle_blank_returnsNull() {
        assertNull(UrlIntentBuilder.toBundle("   "))
    }

    @Test
    fun toBundle_invalidUrl_returnsNull() {
        assertNull(UrlIntentBuilder.toBundle("not a valid url"))
    }

    @Test
    fun toBundle_containsAllKeys() {
        val bundle = UrlIntentBuilder.toBundle("https://example.com?z=last&a=first&m=middle")

        assertNotNull(bundle)
        val keys = bundle!!.keySet()
        assertTrue(keys.containsAll(listOf("z", "a", "m")))
        assertEquals(3, keys.size)
    }

    // ==================== putQueryExtras：标准场景 ====================

    @Test
    fun putQueryExtras_standardParams() {
        val intent = Intent(Intent.ACTION_VIEW)
        val result = UrlIntentBuilder.putQueryExtras(
            "https://api.prod.com:443/v2/users?active=true&role=admin",
            intent
        )

        assertSame(intent, result)  // 返回同一个 Intent（链式调用）
        assertEquals("true", intent.getStringExtra("active"))
        assertEquals("admin", intent.getStringExtra("role"))
    }

    @Test
    fun putQueryExtras_multiValueParams() {
        val intent = Intent(Intent.ACTION_VIEW)
        UrlIntentBuilder.putQueryExtras("https://example.com?tag=a&tag=b&tag=c", intent)

        val tags = intent.getStringArrayListExtra("tag")
        assertNotNull(tags)
        assertEquals(listOf("a", "b", "c"), tags)
    }

    @Test
    fun putQueryExtras_chainingSupport() {
        val intent = UrlIntentBuilder.putQueryExtras(
            "https://example.com?from=deep_link",
            Intent(Intent.ACTION_VIEW)
        )
        assertEquals("deep_link", intent.getStringExtra("from"))
    }

    // ==================== putQueryExtras：边界条件 ====================

    @Test
    fun putQueryExtras_noQuery_doesNotModifyExtras() {
        val intent = Intent(Intent.ACTION_VIEW)
        UrlIntentBuilder.putQueryExtras("https://example.com/path", intent)

        assertNull(intent.extras)
    }

    @Test
    fun putQueryExtras_null_returnsSameIntent() {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.putExtra("preserve", "keep_me")
        val result = UrlIntentBuilder.putQueryExtras(null, intent)

        assertSame(intent, result)
        assertEquals("keep_me", intent.getStringExtra("preserve"))
        // null URL 不写入任何额外 extras
        assertEquals(1, intent.extras?.size())
    }

    @Test
    fun putQueryExtras_invalidUrl_returnsSameIntent() {
        val intent = Intent(Intent.ACTION_VIEW)
        val result = UrlIntentBuilder.putQueryExtras("not a url", intent)

        assertSame(intent, result)
        assertNull(intent.extras)
    }

    @Test
    fun putQueryExtras_emptyUrl_returnsSameIntent() {
        val intent = Intent(Intent.ACTION_VIEW)
        val result = UrlIntentBuilder.putQueryExtras("", intent)

        assertSame(intent, result)
        assertNull(intent.extras)
    }

    @Test
    fun putQueryExtras_preservesExistingExtras() {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.putExtra("existing_key", "existing_value")

        UrlIntentBuilder.putQueryExtras("https://example.com?new_key=new_value", intent)

        assertEquals("existing_value", intent.getStringExtra("existing_key"))
        assertEquals("new_value", intent.getStringExtra("new_key"))
    }

    @Test
    fun putQueryExtras_overwritesConflictingKeys() {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.putExtra("active", "old_value")

        UrlIntentBuilder.putQueryExtras("https://example.com?active=new_value", intent)

        // putExtras 会覆盖同 key 的值
        assertEquals("new_value", intent.getStringExtra("active"))
    }

    // ==================== 集成场景 ====================

    @Test
    fun integration_toBundleThenModifyThenPutToIntent() {
        val bundle = UrlIntentBuilder.toBundle("https://example.com?q=kotlin&page=1")
        assertNotNull(bundle)

        // 业务层二次加工：追加自定义参数
        bundle!!.putString("extra_key", "extra_value")

        val intent = Intent(Intent.ACTION_VIEW)
        intent.putExtras(bundle)

        assertEquals("kotlin", intent.getStringExtra("q"))
        assertEquals("1", intent.getStringExtra("page"))
        assertEquals("extra_value", intent.getStringExtra("extra_key"))
    }

    @Test
    fun integration_fullRealWorldUrl() {
        val url = "https://api.prod.com:443/v2/users?active=true&role=admin&debug=1&tag=premium&tag=verified"

        // 功能一
        val bundle = UrlIntentBuilder.toBundle(url)
        assertNotNull(bundle)
        assertEquals(4, bundle!!.size())
        assertEquals("true", bundle.getString("active"))
        assertEquals("admin", bundle.getString("role"))
        assertEquals("1", bundle.getString("debug"))
        assertEquals(listOf("premium", "verified"), bundle.getStringArrayList("tag"))

        // 功能二
        val intent = UrlIntentBuilder.putQueryExtras(url, Intent(Intent.ACTION_VIEW))
        assertEquals("true", intent.getStringExtra("active"))
        assertEquals("admin", intent.getStringExtra("role"))
        assertEquals("1", intent.getStringExtra("debug"))
        assertEquals(listOf("premium", "verified"), intent.getStringArrayListExtra("tag"))
    }

    // ==================== P0：非 ASCII URL 输入 ====================

    @Test
    fun toBundle_chineseInQuery_success() {
        // P0 修复前：整条 URL 被 URI 拒绝返回 null
        // P0 修复后：非 ASCII 字符被 sanitize → percent-encoding，正常解析
        val bundle = UrlIntentBuilder.toBundle("https://example.com/search?q=中文&type=1")

        assertNotNull(bundle)
        // "中文" 经 sanitize → %E4%B8%AD%E6%96%87 → URLDecoder → "中文"
        assertEquals("中文", bundle!!.getString("q"))
        assertEquals("1", bundle.getString("type"))
    }

    @Test
    fun toBundle_emojiInQuery_success() {
        val bundle = UrlIntentBuilder.toBundle("https://example.com?emoji=😀")

        assertNotNull(bundle)
        assertEquals("😀", bundle!!.getString("emoji"))
    }

    @Test
    fun toBundle_mixedAccentedChars() {
        val bundle = UrlIntentBuilder.toBundle("https://example.com/api?name=José&city=München")

        assertNotNull(bundle)
        assertEquals("José", bundle!!.getString("name"))
        assertEquals("München", bundle.getString("city"))
    }

    @Test
    fun toBundle_japaneseInQuery() {
        val bundle = UrlIntentBuilder.toBundle("https://example.com?q=日本語")

        assertNotNull(bundle)
        assertEquals("日本語", bundle!!.getString("q"))
    }

    @Test
    fun toBundle_alreadyEncodedChinese_stillWorks() {
        // 已正确编码的 URL 不受 sanitize 影响
        val bundle = UrlIntentBuilder.toBundle("https://example.com?q=%E4%B8%AD%E6%96%87")

        assertNotNull(bundle)
        assertEquals("中文", bundle!!.getString("q"))
    }

    // ==================== P1：指定字符集解码 ====================

    @Test
    fun toBundle_withCharset_gbk() {
        // GBK 编码的 "中文" + "测试"
        val bundle = UrlIntentBuilder.toBundle(
            "https://example.com?q=%D6%D0%CE%C4&type=%B2%E2%CA%D4",
            Charset.forName("GBK")
        )

        assertNotNull(bundle)
        assertEquals("中文", bundle!!.getString("q"))
        assertEquals("测试", bundle.getString("type"))
    }

    @Test
    fun toBundle_withCharset_defaultUtf8() {
        val utf8Bundle = UrlIntentBuilder.toBundle("https://example.com?q=%E4%B8%AD%E6%96%87")
        val explicitBundle = UrlIntentBuilder.toBundle(
            "https://example.com?q=%E4%B8%AD%E6%96%87",
            Charsets.UTF_8
        )

        assertEquals(utf8Bundle!!.getString("q"), explicitBundle!!.getString("q"))
    }

    @Test
    fun toBundle_withCharset_shiftJis() {
        val bundle = UrlIntentBuilder.toBundle(
            "https://example.com?q=%93%FA%96%7B%8C%EA",
            Charset.forName("Shift_JIS")
        )

        assertNotNull(bundle)
        assertEquals("日本語", bundle!!.getString("q"))
    }

    // ==================== P1：双重编码检测 ====================

    @Test
    fun toBundle_doubleEncoded_decodedOnce() {
        // %2520 = '%' (%25) + '20' → 解码一次得 "%20"
        // toBundle 只解码一次，值仍含 %20
        val bundle = UrlIntentBuilder.toBundle("https://example.com?name=John%2520Doe")

        assertNotNull(bundle)
        // 只解一层：%25 → %, 所以 %2520 → %20
        assertEquals("John%20Doe", bundle!!.getString("name"))
    }

    @Test
    fun toBundle_doubleEncoded_key() {
        // key 也是双重编码：%256E → 解码一次 → %6E，合起来即 %6E%61%6D%65
        val bundle = UrlIntentBuilder.toBundle("https://example.com?%256E%2561%256D%2565=value")
        assertNotNull(bundle)
        // 解码一层后的 key 是 %6E%61%6D%65（仍需再解码一次才是 "name"）
        assertEquals("value", bundle!!.getString("%6E%61%6D%65"))
    }

    // ==================== P2：key 前缀保护 ====================

    @Test
    fun putQueryExtras_withKeyPrefix() {
        val intent = Intent(Intent.ACTION_VIEW)
        UrlIntentBuilder.putQueryExtras(
            "https://example.com?active=true&role=admin",
            intent,
            keyPrefix = "url_"
        )

        assertNull(intent.getStringExtra("active"))       // 不带前缀的 key 不存在
        assertNull(intent.getStringExtra("role"))
        assertEquals("true", intent.getStringExtra("url_active"))
        assertEquals("admin", intent.getStringExtra("url_role"))
    }

    @Test
    fun putQueryExtras_withEmptyPrefix() {
        val intent = Intent(Intent.ACTION_VIEW)
        UrlIntentBuilder.putQueryExtras(
            "https://example.com?q=test",
            intent,
            keyPrefix = ""
        )

        assertEquals("test", intent.getStringExtra("q"))
    }

    @Test
    fun putQueryExtras_keyPrefix_preservesExistingExtras() {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.putExtra("id", 12345)
        intent.putExtra("active", "original")

        UrlIntentBuilder.putQueryExtras(
            "https://example.com?active=from_url",
            intent,
            keyPrefix = "url_"
        )

        // 原有的 extras 不受影响
        assertEquals(12345, intent.getIntExtra("id", -1))
        assertEquals("original", intent.getStringExtra("active"))
        // URL 参数带前缀写入
        assertEquals("from_url", intent.getStringExtra("url_active"))
    }

    @Test
    fun putQueryExtras_keyPrefix_multiValue() {
        val intent = Intent(Intent.ACTION_VIEW)
        UrlIntentBuilder.putQueryExtras(
            "https://example.com?tag=a&tag=b&tag=c",
            intent,
            keyPrefix = "url_"
        )

        val tags = intent.getStringArrayListExtra("url_tag")
        assertNotNull(tags)
        assertEquals(listOf("a", "b", "c"), tags)
        assertNull(intent.getStringArrayListExtra("tag"))  // 不带前缀的不存在
    }

    @Test
    fun putQueryExtras_defaultNoPrefix_overwritesExisting() {
        // 不带 keyPrefix 的默认行为：覆盖同名 key（保持向后兼容）
        val intent = Intent(Intent.ACTION_VIEW)
        intent.putExtra("active", "old_value")

        UrlIntentBuilder.putQueryExtras("https://example.com?active=new_value", intent)

        assertEquals("new_value", intent.getStringExtra("active"))
    }

    // ==================================================================
    // Map<String, String> → Bundle
    // ==================================================================

    @Test
    fun toBundle_stringMap_basic() {
        val bundle = UrlIntentBuilder.toBundle(mapOf("env" to "production", "timeout" to "30"))

        assertEquals(2, bundle.size())
        assertEquals("production", bundle.getString("env"))
        assertEquals("30", bundle.getString("timeout"))
    }

    @Test
    fun toBundle_stringMap_singleEntry() {
        val bundle = UrlIntentBuilder.toBundle(mapOf("key" to "value"))

        assertEquals("value", bundle.getString("key"))
    }

    @Test
    fun toBundle_stringMap_empty() {
        val bundle = UrlIntentBuilder.toBundle(emptyMap<String, String>())

        assertTrue(bundle.isEmpty)
    }

    @Test
    fun toBundle_stringMap_specialChars() {
        val bundle = UrlIntentBuilder.toBundle(mapOf("name" to "John & Jane", "city" to "New York"))

        assertEquals("John & Jane", bundle.getString("name"))
        assertEquals("New York", bundle.getString("city"))
    }

    // ==================================================================
    // Map<String, List<String>> → Bundle
    // ==================================================================

    @Test
    fun toBundle_listMap_singleValue() {
        val params = linkedMapOf("q" to listOf("kotlin"), "page" to listOf("1"))
        val bundle = UrlIntentBuilder.toBundle(params)

        assertEquals("kotlin", bundle.getString("q"))
        assertEquals("1", bundle.getString("page"))
    }

    @Test
    fun toBundle_listMap_multiValue() {
        val params = linkedMapOf("tag" to listOf("android", "jvm", "kotlin"))
        val bundle = UrlIntentBuilder.toBundle(params)

        assertEquals(listOf("android", "jvm", "kotlin"), bundle.getStringArrayList("tag"))
    }

    @Test
    fun toBundle_listMap_emptyValue() {
        val params = linkedMapOf("flag" to listOf(""), "q" to listOf("test"))
        val bundle = UrlIntentBuilder.toBundle(params)

        assertEquals("", bundle.getString("flag"))
        assertEquals("test", bundle.getString("q"))
    }

    @Test
    fun toBundle_listMap_empty() {
        val bundle = UrlIntentBuilder.toBundle(emptyMap<String, List<String>>())

        assertTrue(bundle.isEmpty)
    }

    @Test
    fun toBundle_listMap_bridgeFromQueryParams() {
        // QueryParams.parse 输出直接桥接到 toBundle
        val parsed = com.itg.itg_string.query.QueryParams.parse("tag=a&tag=b&q=kotlin&debug")
        val bundle = UrlIntentBuilder.toBundle(parsed)

        assertEquals(listOf("a", "b"), bundle.getStringArrayList("tag"))
        assertEquals("kotlin", bundle.getString("q"))
        assertEquals("", bundle.getString("debug"))
    }

    // ==================================================================
    // Map<String, *> → Bundle（异构类型）
    // ==================================================================

    @Test
    fun toBundle_wildcardMap_stringType() {
        val bundle = UrlIntentBuilder.toBundle(mapOf<String, Any?>("name" to "John"))

        assertEquals("John", bundle.getString("name"))
    }

    @Test
    fun toBundle_wildcardMap_intType() {
        val bundle = UrlIntentBuilder.toBundle(mapOf<String, Any?>("count" to 42))

        assertEquals(42, bundle.getInt("count"))
    }

    @Test
    fun toBundle_wildcardMap_longType() {
        val bundle = UrlIntentBuilder.toBundle(mapOf<String, Any?>("ts" to 1699000000000L))

        assertEquals(1699000000000L, bundle.getLong("ts"))
    }

    @Test
    fun toBundle_wildcardMap_booleanType() {
        val bundle = UrlIntentBuilder.toBundle(mapOf<String, Any?>("active" to true, "debug" to false))

        assertTrue(bundle.getBoolean("active"))
        assertFalse(bundle.getBoolean("debug"))
    }

    @Test
    fun toBundle_wildcardMap_floatType() {
        val bundle = UrlIntentBuilder.toBundle(mapOf<String, Any?>("ratio" to 0.75f))

        assertEquals(0.75f, bundle.getFloat("ratio"))
    }

    @Test
    fun toBundle_wildcardMap_doubleType() {
        val bundle = UrlIntentBuilder.toBundle(mapOf<String, Any?>("score" to 4.5))

        assertEquals(4.5, bundle.getDouble("score"), 0.0)
    }

    @Test
    fun toBundle_wildcardMap_stringList() {
        val bundle = UrlIntentBuilder.toBundle(
            mapOf<String, Any?>("tags" to listOf("premium", "verified"))
        )

        assertEquals(listOf("premium", "verified"), bundle.getStringArrayList("tags"))
    }

    @Test
    fun toBundle_wildcardMap_intList() {
        val bundle = UrlIntentBuilder.toBundle(
            mapOf<String, Any?>("ids" to listOf(1, 2, 3))
        )

        assertEquals(listOf(1, 2, 3), bundle.getIntegerArrayList("ids"))
    }

    @Test
    fun toBundle_wildcardMap_nullValuesSkipped() {
        val bundle = UrlIntentBuilder.toBundle(mapOf<String, Any?>(
            "keep" to "me",
            "skip" to null
        ))

        assertEquals("me", bundle.getString("keep"))
        assertFalse(bundle.containsKey("skip"))
    }

    @Test
    fun toBundle_wildcardMap_unknownTypeFallback() {
        // 非标准类型 → toString() 兜底
        val bundle = UrlIntentBuilder.toBundle(mapOf<String, Any?>(
            "data" to StringBuilder("hello")
        ))

        assertEquals("hello", bundle.getString("data"))
    }

    @Test
    fun toBundle_wildcardMap_empty() {
        val bundle = UrlIntentBuilder.toBundle(emptyMap<String, Any>())

        assertTrue(bundle.isEmpty)
    }

    @Test
    fun toBundle_wildcardMap_emptyList() {
        val bundle = UrlIntentBuilder.toBundle(mapOf<String, Any?>(
            "empty_list" to emptyList<String>()
        ))

        assertNotNull(bundle.getStringArrayList("empty_list"))
        assertTrue(bundle.getStringArrayList("empty_list")!!.isEmpty())
    }

    @Test
    fun toBundle_wildcardMap_fullExample() {
        val bundle = UrlIntentBuilder.toBundle(mapOf<String, Any?>(
            "id" to 12345,
            "active" to true,
            "score" to 4.5,
            "name" to "John",
            "tags" to listOf("premium", "verified"),
            "ignored" to null
        ))

        assertEquals(12345, bundle.getInt("id"))
        assertTrue(bundle.getBoolean("active"))
        assertEquals(4.5, bundle.getDouble("score"), 0.0)
        assertEquals("John", bundle.getString("name"))
        assertEquals(listOf("premium", "verified"), bundle.getStringArrayList("tags"))
        assertFalse(bundle.containsKey("ignored"))
    }

    // ==================================================================
    // toMap(url) — URL → Map<String, String>（单值）
    // ==================================================================

    @Test
    fun toMap_basic() {
        val map = UrlIntentBuilder.toMap("https://api.com?active=true&role=admin&page=1")

        assertNotNull(map)
        assertEquals(3, map!!.size)
        assertEquals("true", map["active"])
        assertEquals("admin", map["role"])
        assertEquals("1", map["page"])
    }

    @Test
    fun toMap_multiValueTakesFirstOnly() {
        val map = UrlIntentBuilder.toMap("https://api.com?tag=a&tag=b&tag=c")

        assertNotNull(map)
        assertEquals("a", map!!["tag"])  // 只取第一个值
    }

    @Test
    fun toMap_noQuery_returnsEmptyMap() {
        val map = UrlIntentBuilder.toMap("https://example.com/path")

        assertNotNull(map)
        assertTrue(map!!.isEmpty())
    }

    @Test
    fun toMap_null_returnsNull() {
        assertNull(UrlIntentBuilder.toMap(null))
    }

    @Test
    fun toMap_emptyUrl_returnsNull() {
        assertNull(UrlIntentBuilder.toMap(""))
    }

    @Test
    fun toMap_invalidUrl_returnsNull() {
        assertNull(UrlIntentBuilder.toMap("not a url"))
    }

    @Test
    fun toMap_encodedParams() {
        val map = UrlIntentBuilder.toMap("https://api.com?name=John%20Doe&city=New+York")

        assertNotNull(map)
        assertEquals("John Doe", map!!["name"])
        assertEquals("New York", map["city"])
    }

    @Test
    fun toMap_chineseUrl() {
        val map = UrlIntentBuilder.toMap("https://搜索.com?q=中文&type=1")

        assertNotNull(map)
        assertEquals("中文", map!!["q"])
        assertEquals("1", map["type"])
    }

    @Test
    fun toMap_withCharset_gbk() {
        val map = UrlIntentBuilder.toMap(
            "https://example.com?q=%D6%D0%CE%C4",
            Charset.forName("GBK")
        )

        assertNotNull(map)
        assertEquals("中文", map!!["q"])
    }

    @Test
    fun toMap_keyWithoutValue() {
        val map = UrlIntentBuilder.toMap("https://api.com?flag&q=kotlin")

        assertNotNull(map)
        assertEquals("", map!!["flag"])
        assertEquals("kotlin", map["q"])
    }

    // ==================================================================
    // toMultiMap(url) — URL → Map<String, List<String>>（多值）
    // ==================================================================

    @Test
    fun toMultiMap_basic() {
        val map = UrlIntentBuilder.toMultiMap("https://api.com?q=kotlin&page=1")

        assertNotNull(map)
        assertEquals(listOf("kotlin"), map!!["q"])
        assertEquals(listOf("1"), map["page"])
    }

    @Test
    fun toMultiMap_multiValue() {
        val map = UrlIntentBuilder.toMultiMap("https://api.com?tag=a&tag=b&tag=c&q=kotlin")

        assertNotNull(map)
        assertEquals(listOf("a", "b", "c"), map!!["tag"])
        assertEquals(listOf("kotlin"), map["q"])
    }

    @Test
    fun toMultiMap_noQuery_returnsEmptyMap() {
        val map = UrlIntentBuilder.toMultiMap("https://example.com/path")

        assertNotNull(map)
        assertTrue(map!!.isEmpty())
    }

    @Test
    fun toMultiMap_null_returnsNull() {
        assertNull(UrlIntentBuilder.toMultiMap(null))
    }

    @Test
    fun toMultiMap_equivalentToQueryParams() {
        // toMultiMap 的输出应该与 QueryParams.parse(UrlParser.getQuery(...)) 等价
        val url = "https://example.com?tag=a&tag=b&q=kotlin&flag"
        val fromBuilder = UrlIntentBuilder.toMultiMap(url)
        val fromQueryParams = com.itg.itg_string.query.QueryParams.parse(
            com.itg.itg_string.core.UrlParser.getQuery(url)
        )

        assertEquals(fromQueryParams, fromBuilder)
    }

    @Test
    fun toMultiMap_withCharset() {
        val map = UrlIntentBuilder.toMultiMap(
            "https://example.com?q=%D6%D0%CE%C4",
            Charset.forName("GBK")
        )

        assertNotNull(map)
        assertEquals(listOf("中文"), map!!["q"])
    }

    // ==================================================================
    // putExtras(Map<String, String>) — 单值 Map → Intent
    // ==================================================================

    @Test
    fun putExtras_stringMap_basic() {
        val intent = Intent(Intent.ACTION_VIEW)
        UrlIntentBuilder.putExtras(mapOf("key1" to "val1", "key2" to "val2"), intent)

        assertEquals("val1", intent.getStringExtra("key1"))
        assertEquals("val2", intent.getStringExtra("key2"))
    }

    @Test
    fun putExtras_stringMap_withPrefix() {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.putExtra("id", 999)
        UrlIntentBuilder.putExtras(mapOf("id" to "from_map"), intent, keyPrefix = "map_")

        assertEquals(999, intent.getIntExtra("id", -1))       // 原有值保留
        assertEquals("from_map", intent.getStringExtra("map_id"))  // 带前缀的 Map 值
    }

    @Test
    fun putExtras_stringMap_empty() {
        val intent = Intent(Intent.ACTION_VIEW)
        val result = UrlIntentBuilder.putExtras(emptyMap<String, String>(), intent)

        assertSame(intent, result)
        assertNull(intent.extras)
    }

    @Test
    fun putExtras_stringMap_chainCall() {
        val intent = UrlIntentBuilder.putExtras(
            mapOf("from" to "map_entry"),
            Intent(Intent.ACTION_VIEW)
        )

        assertEquals("map_entry", intent.getStringExtra("from"))
    }

    // ==================================================================
    // putExtras(Map<String, List<String>>) — 多值 Map → Intent
    // ==================================================================

    @Test
    fun putExtras_listMap_multiValue() {
        val intent = Intent(Intent.ACTION_VIEW)
        UrlIntentBuilder.putExtras(
            linkedMapOf("tag" to listOf("a", "b", "c")),
            intent
        )

        assertEquals(listOf("a", "b", "c"), intent.getStringArrayListExtra("tag"))
    }

    @Test
    fun putExtras_listMap_withPrefix() {
        val intent = Intent(Intent.ACTION_VIEW)
        UrlIntentBuilder.putExtras(
            linkedMapOf("tag" to listOf("x", "y")),
            intent,
            keyPrefix = "url_"
        )

        assertEquals(listOf("x", "y"), intent.getStringArrayListExtra("url_tag"))
        assertNull(intent.getStringArrayListExtra("tag"))
    }

    // ==================================================================
    // putExtras(Map<String, *>) — 异构 Map → Intent
    // ==================================================================

    @Test
    fun putExtras_wildcardMap_variousTypes() {
        val intent = Intent(Intent.ACTION_VIEW)
        UrlIntentBuilder.putExtras(
            mapOf<String, Any?>(
                "id" to 42,
                "active" to true,
                "score" to 3.14,
                "name" to "test"
            ),
            intent
        )

        assertEquals(42, intent.getIntExtra("id", -1))
        assertTrue(intent.getBooleanExtra("active", false))
        assertEquals(3.14, intent.getDoubleExtra("score", 0.0), 0.0)
        assertEquals("test", intent.getStringExtra("name"))
    }

    @Test
    fun putExtras_wildcardMap_withPrefix_preservesTypes() {
        val intent = Intent(Intent.ACTION_VIEW)
        UrlIntentBuilder.putExtras(
            mapOf<String, Any?>(
                "count" to 10,
                "enabled" to true,
                "label" to "hello"
            ),
            intent,
            keyPrefix = "cfg_"
        )

        assertEquals(10, intent.getIntExtra("cfg_count", -1))
        assertTrue(intent.getBooleanExtra("cfg_enabled", false))
        assertEquals("hello", intent.getStringExtra("cfg_label"))
    }

    @Test
    fun putExtras_wildcardMap_nullSkipped() {
        val intent = Intent(Intent.ACTION_VIEW)
        UrlIntentBuilder.putExtras(
            mapOf<String, Any?>("keep" to "me", "skip" to null),
            intent
        )

        assertEquals("me", intent.getStringExtra("keep"))
        assertFalse(intent.extras?.containsKey("skip") ?: false)
    }

    @Test
    fun putExtras_wildcardMap_empty() {
        val intent = Intent(Intent.ACTION_VIEW)
        val result = UrlIntentBuilder.putExtras(emptyMap<String, Any>(), intent)

        assertSame(intent, result)
        assertNull(intent.extras)
    }

    // ==================================================================
    // 集成：Map → Bundle → Intent 全链路
    // ==================================================================

    @Test
    fun integration_mapToBundleToIntent() {
        // 1. 从配置 Map 创建 Bundle
        val config = mapOf("env" to "staging", "region" to "us-east-1")
        val bundle = UrlIntentBuilder.toBundle(config)

        // 2. 加工 Bundle
        bundle.putString("extra", "added")

        // 3. 注入 Intent
        val intent = Intent(Intent.ACTION_VIEW)
        intent.putExtras(bundle)

        assertEquals("staging", intent.getStringExtra("env"))
        assertEquals("us-east-1", intent.getStringExtra("region"))
        assertEquals("added", intent.getStringExtra("extra"))
    }

    @Test
    fun integration_queryParamsParsedToIntentWithPrefix() {
        // QueryParams.parse → toBundle(Map) → putExtras(Map, intent, prefix)
        val parsed = com.itg.itg_string.query.QueryParams.parse("id=42&tag=hot&tag=new")

        val intent = Intent(Intent.ACTION_VIEW)
        intent.putExtra("id", 999)  // 业务已有的 id
        UrlIntentBuilder.putExtras(parsed, intent, keyPrefix = "url_")

        // 原有值保留
        assertEquals(999, intent.getIntExtra("id", -1))
        // URL 参数带前缀写入
        assertEquals("42", intent.getStringExtra("url_id"))
        assertEquals(listOf("hot", "new"), intent.getStringArrayListExtra("url_tag"))
    }
}
