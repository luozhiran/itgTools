# ITG File

`itg-file` 是 Android 文件工具模块，覆盖文件/目录管理、读写、hash、assets 读取、Okio 版本能力和清理任务。模块 minSdk 21，依赖 `itg-concurrent-core`、AndroidX Fragment 和 Okio。

## 使用场景总览

| 使用场景 | 推荐 API | 适用条件/支持范围 | 什么情况下使用 | 为什么可以用 |
| --- | --- | --- | --- | --- |
| [创建、复制、移动、删除文件](./docs/01-core-operations.md) | `FileUtils` | 本地文件路径 | 管理缓存、导出文件、移动目录 | 封装 `File`/NIO 操作，支持同步和异步方法 |
| [读取文本、字节、行和分块](./docs/02-read-write-hash.md) | `FileReadUtils` | 小文件可一次性读，大文件用流式/分块 | 读取配置、日志、二进制片段 | 提供 maxBytes 限制和 streaming/chunk API |
| [写入文本、字节和原子写](./docs/02-read-write-hash.md) | `FileWriteUtils` | 目标目录可写 | 写缓存、配置、导出数据 | 支持追加、覆盖、流式写入和安全写入模式 |
| [计算文件 hash](./docs/02-read-write-hash.md) | `FileHashUtils` / `OkioHashUtils` | 文件存在且可读 | 校验下载包、缓存一致性 | 支持 MD5、SHA、CRC 等常见摘要 |
| [读取 assets 或复制资源](./docs/03-assets-okio.md) | `AssetUtils` / `OkioAssetUtils` | Android `Context` | 内置配置、模板、离线资源 | 使用 `assets.open`，可复制到文件并报告进度 |
| [使用 Okio 版本 API](./docs/03-assets-okio.md) | `OkioFileUtils`、`OkioReadUtils`、`OkioWriteUtils` | 已引入 Okio | 需要 `ByteString`、`Buffer` 或 Okio source/sink | Okio API 更适合流式和二进制处理 |
| [清理缓存和过期文件](./docs/04-cleanup-api.md) | `FileCleanupManager`、`CleanupExecutor` | 清理目录明确 | 按大小、时间、后缀清理缓存 | 使用清理模型和执行器集中表达规则 |
| [查看 API 速查](./docs/04-cleanup-api.md) | API 表 | 所有使用者 | 查方法分类和安全边界 | 汇总源码公开工具类 |

## 文档目录

| 文档 | 内容 |
| --- | --- |
| [01. 核心文件操作](./docs/01-core-operations.md) | 创建、删除、复制、移动、列表、空间信息 |
| [02. 读写与 Hash](./docs/02-read-write-hash.md) | 文本/字节/分块读写、hash 计算 |
| [03. Assets 与 Okio](./docs/03-assets-okio.md) | assets 读取、复制、Okio 工具 |
| [04. 清理任务与 API](./docs/04-cleanup-api.md) | 缓存清理、安全边界、API 速查 |

## 依赖

```kotlin
dependencies {
    implementation(project(":itg-file"))
}
```