# 04. 异步与安全约束

本节说明异步 API、失败语义和接入安全清单。

## 适用条件

- 加密任务可能耗时，不能阻塞 UI。
- 需要拿到 `Future<*>` 以便取消后台任务。
- 接入前需要确认哪些行为是安全边界。

## 推荐做法

```kotlin
EncryptUtils.encryptStringAsync("hello", key) { cipher ->
    // 回调不保证在主线程
}
```

## 可复制 Demo

```kotlin
import android.os.Handler
import android.os.Looper
import com.itg.itg_encrypt.core.EncryptUtils

val main = Handler(Looper.getMainLooper())
val key = EncryptUtils.generateAesKey()

val future = EncryptUtils.encryptStringAsync("hello", key) { cipher ->
    main.post {
        textView.text = cipher ?: "encrypt failed"
    }
}

// 页面销毁时可按需取消
// future.cancel(true)
```

## 关键说明

- 异步方法使用 `itg-thread-pools` 后台线程池。
- 回调线程不是主线程；更新 UI 前需要切回主线程。
- 解密失败返回 `null` 或 `false`，文件失败会清理输出。
- 包头被 GCM 认证，篡改包头应导致解密失败。
- 不要复用同一组 IV；模块加密 API 会自动生成随机 IV。

## API 速查

| 场景 | API |
| --- | --- |
| Key 生成 | `generateAesKey`、`generateSalt`、`generateIv` |
| 原始 key | `encryptBytes`、`decryptBytes`、`encryptString`、`decryptString` |
| 密码模式 | `encryptBytesWithPassword`、`decryptBytesWithPassword`、`encryptStringWithPassword` |
| 文件 | `encryptFile`、`decryptFile`、`encryptFileWithPassword`、`decryptFileWithPassword` |
| 异步 | 上述能力对应的 `*Async` 方法 |

## 验证方式

- release 日志中不要打印 key、password、明文或密文全文。
- 错 key、错密码、错 AAD、篡改包头都应解密失败。

[返回 README](../README.md)