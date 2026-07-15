# ITG Verification

`itg-verification` 是文件完整性和下载结果校验模块，覆盖 hash 校验、下载包大小/校验和、断点续传判断、分片检查、归档文件检查和文件比较。模块 namespace 当前为 `com.itg.itg_erification`，依赖 `itg-file` 和 `itg-thread-pools`。

## 使用场景总览

| 使用场景 | 推荐 API | 适用条件/支持范围 | 什么情况下使用 | 为什么可以用 |
| --- | --- | --- | --- | --- |
| [校验文件 Hash](./docs/01-hash-download.md) | `HashVerifier.verifyMd5/verifySha256/verifyHash` | 文件存在且可读 | 下载包、补丁包、缓存文件需要确认未损坏 | 通过 `itg-file` 计算摘要并和预期值比较 |
| [从校验文件验证](./docs/01-hash-download.md) | `verifyFromMd5File/verifyFromChecksumFile` | 存在 `.md5`、`.sha256` 等文件 | 服务端同时下发 checksum 文件 | 读取校验文件第一行/哈希值后执行比对 |
| [校验下载完整性](./docs/01-hash-download.md) | `DownloadVerifier.verifyDownload` | 有文件大小或 hash 预期 | 下载完成后判断是否可用 | 同时检查大小、hash 和文件存在性 |
| [判断断点续传和分片](./docs/02-archive-compare-integrity.md) | `verifyResumePossible/verifyParts` | 分片下载或续传 | 下载中断后决定继续还是重下 | 比较当前文件大小和分片文件存在性 |
| [校验归档文件](./docs/02-archive-compare-integrity.md) | `ArchiveVerifier` | zip/归档类文件 | 解压前判断包结构是否可用 | 归档检查可提前发现损坏文件 |
| [比较文件和综合完整性](./docs/02-archive-compare-integrity.md) | `FileComparator`、`IntegrityVerifier` | 两个文件或一组规则 | 迁移、备份、发布验证 | 组合大小、hash、内容比较等信号 |
| [查看 API 速查](./docs/03-api-threading.md) | API 表 | 所有使用者 | 查方法名和异步边界 | 汇总当前源码公开工具类 |

## 文档目录

| 文档 | 内容 |
| --- | --- |
| [01. Hash 与下载校验](./docs/01-hash-download.md) | HashVerifier、DownloadVerifier |
| [02. 归档、比较与完整性](./docs/02-archive-compare-integrity.md) | ArchiveVerifier、FileComparator、IntegrityVerifier |
| [03. API 与线程模型](./docs/03-api-threading.md) | API 速查、异步回调、失败语义 |

## 依赖

```kotlin
dependencies {
    implementation(project(":itg-verification"))
}
```

## 包名提示

当前源码包名拼写为 `com.itg.itg_erification.*`，复制 import 时请以源码为准。