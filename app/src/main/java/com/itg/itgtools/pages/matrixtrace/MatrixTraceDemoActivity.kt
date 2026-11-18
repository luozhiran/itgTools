package com.itg.itgtools.pages.matrixtrace

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.doOnPreDraw
import com.itg.itgtools.databinding.ActivityMatrixTraceDemoBinding
import com.itg.matrixtracecanary.MatrixTraceCanary

/** 故意制造性能问题，仅供手动验证监控事件。 */
class MatrixTraceDemoActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMatrixTraceDemoBinding
    private var eventSubscription: AutoCloseable? = null
    private var frameAnimator: ValueAnimator? = null
    private var frameCount = 0
    private var showingInnerPage = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMatrixTraceDemoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.runFpsDemo.setOnClickListener { startJankyAnimation() }
        binding.runSlowMethod.setOnClickListener { simulateSlowMethod() }
        binding.runBlock.setOnClickListener { simulateMainThreadBlock() }
        binding.runAnr.setOnClickListener { simulateSuspectedAnr() }
        binding.openTargetPage.setOnClickListener {
            startActivity(Intent(this, MatrixTraceTargetActivity::class.java))
        }
        binding.switchInnerPage.setOnClickListener { switchInnerPage() }
        renderEvents()
    }

    override fun onStart() {
        super.onStart()
        eventSubscription = MatrixTraceDemoEvents.observe(::renderEvents)
        renderEvents()
    }

    override fun onStop() {
        eventSubscription?.close()
        eventSubscription = null
        super.onStop()
    }

    override fun onDestroy() {
        frameAnimator?.cancel()
        frameAnimator = null
        super.onDestroy()
    }

    private fun renderEvents() {
        binding.eventLog.text = MatrixTraceDemoEvents.snapshot()
    }

    private fun startJankyAnimation() {
        frameAnimator?.cancel()
        frameCount = 0
        binding.animationStatus.text = "动画运行中：每 8 帧制造一次约 90 ms 的掉帧，持续 15 秒。"
        frameAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 15_000L
            addUpdateListener { animator ->
                val travel = (binding.animationTrack.width - binding.animationDot.width)
                    .coerceAtLeast(0)
                binding.animationDot.translationX = travel * (animator.animatedValue as Float)
                frameCount++
                if (frameCount % 8 == 0) SystemClock.sleep(90)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    binding.animationStatus.text = "动画结束。查看 FPS 事件；Matrix 按时间窗口汇总帧率。"
                    frameAnimator = null
                }
            })
            start()
        }
    }

    private fun simulateSlowMethod() {
        // 宿主 app 应用 com.itg.matrix-slow-method 插件后，此方法退出时上报 SLOW_METHOD。
        SystemClock.sleep(900)
    }

    private fun simulateMainThreadBlock() {
        // Matrix Trace 的 Looper 监控会观察这段主线程阻塞。
        SystemClock.sleep(2_500)
    }

    private fun simulateSuspectedAnr() {
        // 运行中观察 Matrix 疑似 ANR；系统 ANR 退出记录在下次进程启动后读取。
        SystemClock.sleep(6_000)
    }

    private fun switchInnerPage() {
        val from = if (showingInnerPage) "同 Activity 详情" else "监控首页"
        val to = if (showingInnerPage) "监控首页" else "同 Activity 详情"
        val transition = MatrixTraceCanary.beginPageTransition(from, to)
        showingInnerPage = !showingInnerPage
        binding.innerPageLabel.text = if (showingInnerPage) "当前内容：同 Activity 详情" else "当前内容：监控首页"
        binding.switchInnerPage.text = if (showingInnerPage) "返回同 Activity 首页" else "切换同 Activity 内容"
        binding.innerPageLabel.doOnPreDraw {
            binding.innerPageLabel.post { transition.finish() }
        }
    }
}
