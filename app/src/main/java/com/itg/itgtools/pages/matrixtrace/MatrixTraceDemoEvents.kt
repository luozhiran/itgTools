package com.itg.itgtools.pages.matrixtrace

import android.os.Handler
import android.os.Looper
import com.itg.matrixtracecanary.MatrixTraceEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArraySet

/** Matrix 可从后台线程回调；示例页只在主线程收到更新通知。 */
object MatrixTraceDemoEvents {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lock = Any()
    private val lines = ArrayDeque<String>()
    private val observers = CopyOnWriteArraySet<() -> Unit>()

    fun add(event: MatrixTraceEvent) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date())
        val detail = when (event.kind) {
            MatrixTraceEvent.Kind.FPS -> "${event.scene} · ${"%.1f".format(Locale.US, event.fps)} FPS"
            MatrixTraceEvent.Kind.SLOW_METHOD -> "${event.methodName} · ${event.costMs} ms"
            MatrixTraceEvent.Kind.STARTUP, MatrixTraceEvent.Kind.PAGE_SWITCH ->
                "${event.measurement} · ${event.fromScene} → ${event.scene} · ${event.costMs} ms"
            MatrixTraceEvent.Kind.BLOCK, MatrixTraceEvent.Kind.ANR ->
                "${event.scene} · ${event.costMs} ms · ${event.tag}"
            MatrixTraceEvent.Kind.OTHER -> "${event.tag} · ${event.scene}"
        }
        synchronized(lock) {
            lines.addFirst("$time  ${event.kind}  $detail")
            while (lines.size > 30) lines.removeLast()
        }
        mainHandler.post { observers.forEach { it() } }
    }

    fun snapshot(): String = synchronized(lock) {
        if (lines.isEmpty()) "暂无事件。可先点击上方按钮；启动事件需重新打开应用。"
        else lines.joinToString("\n\n")
    }

    fun observe(observer: () -> Unit): AutoCloseable {
        observers.add(observer)
        return AutoCloseable { observers.remove(observer) }
    }
}
