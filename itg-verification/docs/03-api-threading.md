# 03. API 与线程模型

本节汇总校验模块公开能力和线程边界。

## API 速查

| 分类 | 工具类 | 常用 API |
| --- | --- | --- |
| Hash | `HashVerifier` | `verifyMd5`、`verifySha1`、`verifySha256`、`verifySha512`、`verifyHash`、`batchVerify` |
| 校验文件 | `HashVerifier` | `verifyFromMd5File`、`verifyFromChecksumFile`、`parseChecksumFile` |
| 下载 | `DownloadVerifier` | `verifySize`、`verifyMinSize`、`verifyDownload`、`verifyResumePossible`、`verifyParts` |
| 归档 | `ArchiveVerifier` | 以源码方法为准，适合解压前检查 |
| 比较 | `FileComparator` | 以源码方法为准，适合大小、hash 或内容比较 |
| 完整性 | `IntegrityVerifier` | 以源码方法为准，适合组合校验 |

## 可复制 Demo

```kotlin
import android.os.Handler
import android.os.Looper
import com.itg.itg_erification.hash.HashVerifier

val main = Handler(Looper.getMainLooper())
HashVerifier.verifySha256Async(file.absolutePath, expectedSha256) { result ->
    main.post {
        statusText.text = if (result.isValid) "valid" else "invalid: ${result.actual}"
    }
}
```

## 关键说明

- 同步校验会读取文件，避免在主线程处理大文件。
- 异步 API 回调不保证主线程。
- 校验失败时优先使用返回对象中的消息、expected、actual 做诊断。
- `itg-verification` 依赖 `itg-file`，文件不存在或不可读会导致校验失败。

## 验证方式

- UI 更新前必须切主线程。
- 对缺失文件调用校验 API 应得到失败结果，而不是业务崩溃。

[返回 README](../README.md)