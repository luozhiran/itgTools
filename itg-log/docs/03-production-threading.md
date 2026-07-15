# 03. 生产开关与线程安全

本节说明 release 关闭和并发日志行为。

## 适用条件

- `itg-log` 用于开发、测试或 Demo 页面。
- release 构建不希望产生测试日志。
- 多线程任务需要按发生顺序记录步骤。

## 推荐做法

```kotlin
ItgLog.globalEnabled = BuildConfig.DEBUG
```

## 可复制 Demo

```kotlin
import com.itg.log.ItgLog
import com.itg.log.TestLogger

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        ItgLog.globalEnabled = BuildConfig.DEBUG
    }
}

val logger = TestLogger("Concurrent") { outputToLogcat("Concurrent") }
logger.beginTest("并发任务")
listOf(1, 2, 3).forEach { i ->
    Thread { logger.d("task $i done") }.start()
}
```

## 关键说明

- `globalEnabled = false` 后所有 logger 静默，不做格式化和输出。
- 单实例可通过配置 `enabled = false` 关闭。
- `StepTimer.step()` 同步执行，步骤号不会重复。
- 并发下顺序代表进入 logger 的顺序，不代表任务真实开始顺序。

## 验证方式

- release 构建中不应看到测试日志输出。
- 多线程并发输出时步骤号应连续递增。

[返回 README](../README.md)