package com.itg.itgtools.concurrent

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
import com.itg.concurrent.ConcurrentFactory
import com.itg.itgtools.R
import com.itg.itgtools.route.RoutePath.ITG_CONCURRENT_TEST_ACTIVITY
import com.therouter.router.Route

/**
 * itg-concurrent-core 全功能测试 Activity
 *
 * 覆盖: 任务提交 / Future 操作 / 延迟执行 / 后端切换 /
 * 混合模式 / 自定义分发器 / 可用性检测 / 协程原生 API /
 * ConcurrentUtils 工具 / 边界场景
 */
@Route(path = ITG_CONCURRENT_TEST_ACTIVITY)
class ConcurrentTestActivity : AppCompatActivity() {

    private val model = ConcurrentTestModel()
    private lateinit var container: LinearLayout
    private lateinit var logView: TextView
    private lateinit var logScroll: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_concurrent_test)

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

        // 确保初始状态
        ConcurrentFactory.switchTo(ConcurrentFactory.BackendType.AUTO)
        log("=== itg-concurrent-core 并发中间件测试 ===")
        log("默认后端: ${ConcurrentFactory.currentBackend}")
        log("协程可用: ${ConcurrentFactory.isCoroutineAvailable()}")
        log("线程池可用: ${ConcurrentFactory.isThreadPoolAvailable()}")

        buildAllSections()
    }

    override fun onDestroy() {
        ConcurrentFactory.shutdown()
        super.onDestroy()
    }

    private fun log(msg: String) {
        model.onLog?.invoke(msg)
    }

    private fun buildAllSections() {
        buildSection("1. 基本任务提交") {
            button("io — 提交 IO 任务并获取 Future") { model.testBasicSubmitIO() }
            button("compute — CPU 密集型任务") { model.testBasicSubmitCompute() }
            button("background — 后台任务") { model.testBasicSubmitBackground() }
            button("main — 主线程切换") { model.testMainThread() }
            button("批量提交 5 个并发任务") { model.testSubmitMultipleTasks() }
        }

        buildSection("2. Future 操作 (await / cancel / isDone)") {
            button("await — 阻塞等待 Future 结果") { model.testFutureAwait() }
            button("await 超时 — 超时返回 null") { model.testFutureAwaitTimeout() }
            button("cancel — 取消正在执行的任务") { model.testFutureCancel() }
            button("isDone — 检查任务完成状态") { model.testFutureIsDone() }
        }

        buildSection("3. 延迟执行 (ioDelayed / backgroundDelayed / mainDelayed)") {
            button("ioDelayed — IO 延迟 300ms") { model.testIoDelayed() }
            button("backgroundDelayed — BG 延迟 200ms") { model.testBackgroundDelayed() }
            button("mainDelayed — 主线程延迟 100ms") { model.testMainDelayed() }
            button("延迟任务取消 — 提交后立即取消") { model.testDelayedCancellation() }
        }

        buildSection("4. 后端切换 (COROUTINE / THREAD_POOL / AUTO)") {
            button("switchTo(COROUTINE) → 执行 → 恢复") { model.testSwitchToCoroutine() }
            button("switchTo(THREAD_POOL) → 执行 → 恢复") { model.testSwitchToThreadPool() }
            button("AUTO 自动检测") { model.testAutoDetection() }
        }

        buildSection("5. 混合模式 & 自定义注册") {
            button("useMixed: IO→协程, COMPUTE→线程池") { model.testMixedMode() }
            button("register — 自定义分发器包装") { model.testCustomRegister() }
        }

        buildSection("6. 可用性检测 & 分发器访问") {
            button("isCoroutineAvailable / isThreadPoolAvailable") { model.testAvailabilityDetection() }
            button("getDispatcher — 所有类型遍历") { model.testGetDispatcher() }
            button("getCoroutineDispatcher") { model.testGetCoroutineDispatcher() }
        }

        buildSection("7. 协程原生 suspend API") {
            button("ioSuspend / computeSuspend / backgroundSuspend") { model.testSuspendAPI() }
            button("mainSuspend — 主线程挂起") { model.testMainSuspend() }
            button("suspend 异常处理") { model.testSuspendWithException() }
            button("suspend 协程取消传播") { model.testSuspendCancellation() }
        }

        buildSection("8. ConcurrentUtils 工具类") {
            button("isMainThread / isBackgroundThread") { model.testIsMainThread() }
            button("assertMainThread / assertBackgroundThread") { model.testAssertMainThread() }
            button("assertBackgroundThread (正确场景)") { model.testAssertBackgroundThread() }
            button("sleep — 阻塞延迟 200ms") { model.testSleep() }
            button("getCurrentThreadInfo / Description") { model.testGetCurrentThreadInfo() }
            button("等待多个 Future 全部完成") { model.testAwaitAll() }
            button("await 超时返回 null") { model.testAwaitAllWithTimeout() }
        }

        buildSection("9. 生命周期 / 多次切换") {
            button("shutdown → 重新初始化") { model.testShutdown() }
            button("多次快速切换 COROUTINE/THREAD_POOL") { model.testMultipleSwitch() }
        }

        buildSection("10. 边界场景") {
            button("任务抛出异常 — 异常传播验证") { model.testNullTaskHandling() }
            button("main() 从非主线程调用") { model.testConcurrentMainFromMainThread() }
            button("所有 DispatcherType 遍历执行") { model.testGetAllDispatcherTypes() }
        }
    }

    // ==================== UI 构建辅助 ====================

    private fun buildSection(title: String, builder: LinearLayout.() -> Unit) {
        val titleView = TextView(this).apply {
            text = title
            setTextColor(Color.parseColor("#2E7D32"))
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

        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)
            )
            setBackgroundColor(Color.parseColor("#E0E0E0"))
        }
        container.addView(divider)

        buttonContainer.builder()
    }

    private fun LinearLayout.button(label: String, action: () -> Unit) {
        val btn = Button(this@ConcurrentTestActivity).apply {
            text = label
            textSize = 12f
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            isAllCaps = false
            setPadding(dp(12), dp(6), dp(12), dp(6))

            val layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            layoutParams.bottomMargin = dp(2)
            this.layoutParams = layoutParams
        }

        btn.setOnClickListener {
            // 在新线程上执行，避免 ANR
            Thread {
                try {
                    log("\n▶ $label")
                    action()
                } catch (e: Exception) {
                    log("❌ 异常: ${e.javaClass.simpleName}: ${e.message}")
                }
            }.start()
        }
        addView(btn)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
