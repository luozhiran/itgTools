package com.itg.itgtools.log

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.itg.itgtools.R
import com.itg.itgtools.route.RoutePath.ITG_LOG_TEST_ACTIVITY
import com.therouter.router.Route

/**
 * itg-log 全功能测试 Activity
 *
 * 覆盖: 基础 API / 计时精度 / 多线程并发 /
 * 自定义 Formatter / 自定义 Output / DSL 配置 / 边界场景
 */
@Route(path = ITG_LOG_TEST_ACTIVITY)
class LogTestActivity : AppCompatActivity() {

    private val model = LogTestModel(cacheDir)
    private lateinit var container: LinearLayout
    private lateinit var logView: TextView
    private lateinit var logScroll: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_test)

        container = findViewById(R.id.testContainer)
        logView = findViewById(R.id.logTextView)
        logScroll = findViewById(R.id.logScrollView)
        logView.movementMethod = ScrollingMovementMethod()

        model.onLog = { text ->
            runOnUiThread {
                logView.append("\n$text")
                logScroll.post { logScroll.fullScroll(View.FOCUS_DOWN) }
            }
        }

        findViewById<Button>(R.id.clearLogButton).setOnClickListener {
            logView.text = ""
            log("日志已清空")
        }

        log("=== itg-log 日志库测试 ===")

        buildAllSections()
    }

    private fun log(msg: String) {
        model.onLog?.invoke(msg)
    }

    private fun buildAllSections() {
        buildSection("1. 基础 API (生命周期 / 级别 / step / mark)") {
            button("beginTest → d/i/w/e/v → ok/fail → endTest") { model.testBasicLifecycle() }
            button("连续 3 轮测试 (验证步骤重置)") { model.testMultipleTests() }
            button("日志级别过滤 (minLevel=INFO)") { model.testLogLevels() }
            button("step() 返回 StepRecord 结构化数据") { model.testStepMethod() }
            button("mark() 时间标记 (不影响步骤)") { model.testMarkMethod() }
        }

        buildSection("2. 计时精度") {
            button("sleep(200ms) 后计时验证") { model.testTimingAccuracy() }
            button("增量计时 (D+) 精度验证") { model.testDeltaAccuracy() }
            button("高频日志 ×100 条压力测试") { model.testRapidLogging() }
        }

        buildSection("3. 多线程并发") {
            button("10线程 × 5条 = 50条并发日志") { model.testConcurrentLogging() }
            button("并发步骤序号严格递增验证") { model.testConcurrentStepsInOrder() }
        }

        buildSection("4. 自定义 Formatter") {
            button("CompactFormatter (默认紧凑格式)") { model.testCompactFormatter() }
            button("TimestampFormatter (带时间戳)") { model.testTimestampFormatter() }
            button("ColoredFormatter (ANSI 着色)") { model.testColoredFormatter() }
            button("自定义 JSON Formatter") { model.testCustomFormatter() }
            button("自定义标题格式 headerFormatter") { model.testCustomHeaderFormatter() }
        }

        buildSection("5. 自定义 Output (回调 / 文件 / 组合)") {
            button("CallbackOutput — 回调捕获验证") { model.testCallbackOutput() }
            button("FileOutput — 写入文件验证") { model.testFileOutput() }
            button("CompositeOutput — 3路同时输出") { model.testCompositeOutput() }
        }

        buildSection("6. 配置 DSL") {
            button("configureLogger { ... } Builder") { model.testConfigurationBuilder() }
            button("TestLogger 默认配置") { model.testDefaultConfig() }
        }

        buildSection("7. 查询 API & 嵌套") {
            button("currentStep / elapsedMs / isStarted") { model.testQueryAPI() }
            button("嵌套 beginTest (步骤号独立)") { model.testNestedBeginTest() }
        }

        buildSection("8. 边界场景") {
            button("空消息 / 空字符串") { model.testEmptyMessage() }
            button("超长消息 (2000字符)") { model.testLongMessage() }
            button("特殊字符 (换行 / Unicode / JSON)") { model.testSpecialCharacters() }
            button("不调用 beginTest 直接输出") { model.testNoBeginTest() }
            button("beginTest 后不调用 endTest") { model.testBeginWithoutEnd() }
        }
    }

    // ==================== UI 构建 ====================

    private fun buildSection(title: String, builder: LinearLayout.() -> Unit) {
        val titleView = TextView(this).apply {
            text = title
            setTextColor(Color.parseColor("#E65100"))
            textSize = 16f
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(0, dp(16), 0, dp(4))
        }
        container.addView(titleView)

        val buttonContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), 0, dp(4), dp(4))
        }
        container.addView(buttonContainer)

        container.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
            setBackgroundColor(Color.parseColor("#E0E0E0"))
        })

        buttonContainer.builder()
    }

    private fun LinearLayout.button(label: String, action: () -> Unit) {
        val btn = Button(this@LogTestActivity).apply {
            text = label
            textSize = 12f
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            isAllCaps = false
            setPadding(dp(12), dp(6), dp(12), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(2) }
        }
        btn.setOnClickListener {
            Thread {
                try {
                    log("\n▶ $label")
                    val start = System.currentTimeMillis()
                    action()
                    log("⏱ 总耗时: ${System.currentTimeMillis() - start}ms")
                } catch (e: Exception) {
                    log("❌ 异常: ${e.javaClass.simpleName}: ${e.message}")
                }
            }.start()
        }
        addView(btn)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
