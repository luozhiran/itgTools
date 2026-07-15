# 01. Hash 与下载校验

本节说明文件 hash 和下载结果校验。

## 适用条件

- 文件已经下载到本地。
- 服务端提供期望大小、MD5、SHA 或 checksum 文件。
- 需要判断文件是否完整、是否可继续断点续传。

## 推荐做法

```kotlin
val result = HashVerifier.verifySha256(path, expectedSha256)
if (!result.isValid) return
```

## 可复制 Demo

```kotlin
import com.itg.itg_erification.download.DownloadVerifier
import com.itg.itg_erification.hash.HashVerifier

val hash = HashVerifier.verifySha256(
    path = file.absolutePath,
    expectedSha256 = expectedSha256
)

if (hash.isValid) {
    val download = DownloadVerifier.verifyDownload(
        filePath = file.absolutePath,
        expectedSize = expectedBytes,
        expectedHash = expectedSha256,
        hashAlgo = "SHA-256"
    )
    check(download.isComplete && download.isValid)
}
```

## 关键说明

- `HashResult.isValid` 是最终判断字段。
- `verifyHash(path, expectedHash, algorithm)` 支持通过算法名选择。
- `batchVerify` 适合一次校验多个文件。
- `DownloadResult` 同时包含 `isComplete`、`isValid`、大小和消息列表。
- 异步版本依赖线程池，回调不保证主线程。

## 验证方式

- 修改文件一个字节后 hash 校验应失败。
- 期望大小不匹配时 `DownloadResult.isComplete` 应为 false。

[返回 README](../README.md)