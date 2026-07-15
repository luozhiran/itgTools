# 02. 普通任务与主线程

本节解决普通非 suspend 任务应该使用哪个入口。

## API 选择

| 场景 | API | 说明 |
| --- | --- | --- |
| I/O 密集任务 | `Concurrent.io { }` | 网络同步调用、文件、数据库 |
| CPU 密集任务 | `Concurrent.compute { }` | 图片处理、加解密、大量数据转换 |
| 通用后台任务 | `Concurrent.background { }` | 清理、统计、预处理 |
| 主线程任务 | `Concurrent.main { }` | UI 更新或主线程 API 调用 |

## 推荐做法

I/O 任务：

```kotlin
Concurrent.io {
    val config = loadConfigFromDisk()
    Concurrent.main {
        render(config)
    }
}
```

CPU 计算任务：

```kotlin
Concurrent.compute {
    val digest = md5(bytes)
    Concurrent.main {
        showDigest(digest)
    }
}
```

通用后台任务：

```kotlin
Concurrent.background {
    cleanupExpiredCache()
}
```

主线程任务：

```kotlin
Concurrent.main {
    textView.text = "done"
}
```

## 可复制 Demo

下面示例演示：后台读取字符串、计算摘要、切回主线程回调。需要传入你的 `onResult` 回调。

```kotlin
import com.itg.concurrent.Concurrent
import java.security.MessageDigest

fun runBasicTaskDemo(onResult: (String) -> Unit) {
    Concurrent.io {
        val content = "demo-content"

        val digestFuture = Concurrent.compute {
            val bytes = MessageDigest.getInstance("MD5").digest(content.toByteArray())
            bytes.joinToString("") { "%02x".format(it) }
        }

        val digest = digestFuture.get()

        Concurrent.main {
            onResult(digest)
        }
    }
}
```

## 关键说明

- `Concurrent.io/compute/background` 都返回 `Future<T>`；不关心结果时可以忽略返回值。
- `Concurrent.main { }` 已在主线程时直接执行，不在主线程时分发到 MAIN。
- 不要在 `Concurrent.main { }` 中执行耗时任务。

## 常见错误

错误：

```kotlin
Concurrent.main {
    Thread.sleep(3000L)
}
```

正确：

```kotlin
Concurrent.io {
    val data = blockingLoad()
    Concurrent.main { render(data) }
}
```

## 验证方式

在 demo 中打印线程名：

```kotlin
Log.d("Concurrent", "current=${Thread.currentThread().name}")
```

确认耗时逻辑运行在后台线程，UI 更新运行在主线程。

[返回 README](../README.md)