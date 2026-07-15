# ITG Encrypt

`itg-encrypt` 提供 Android 可复用的 AES-GCM 加解密工具，覆盖原始 AES key、密码派生、字符串、字节数组、大文件流式处理和异步执行。模块 minSdk 21，异步能力依赖 `itg-thread-pools`。

## 使用场景总览

| 使用场景 | 推荐 API | 适用条件/支持范围 | 什么情况下使用 | 为什么可以用 |
| --- | --- | --- | --- | --- |
| [随机 AES key 加密字节或字符串](./docs/01-key-bytes-string.md) | `generateAesKey`、`encryptBytes`、`encryptString` | 调用方负责安全保存 key | 已有随机 key，数据在内存中 | 默认 `AES/GCM/NoPadding`，每次加密生成随机 IV |
| [用密码派生密钥](./docs/02-password-aad.md) | `encryptBytesWithPassword`、`encryptStringWithPassword` | 用户口令或业务密码场景 | 不想直接保存 AES key | 使用 PBKDF2 派生 key，包内保存 salt/iterations |
| [保护上下文不被篡改](./docs/02-password-aad.md) | `associatedData` | 加解密必须传完全一致 AAD | 密文绑定业务类型、版本或用户上下文 | AAD 不加密但参与 GCM 认证 |
| [加解密大文件](./docs/03-file-encryption.md) | `encryptFile`、`decryptFile` | 文件路径可读写 | 备份包、导出文件、缓存文件 | 文件接口流式处理，失败会清理半成品输出 |
| [后台执行加密任务](./docs/04-async-security.md) | `encryptStringAsync`、`encryptFileAsync` | 回调在线程池，不保证主线程 | 页面不希望被加密计算阻塞 | 异步方法返回 `Future<*>`，可取消 |
| [查看安全约束和 API](./docs/04-async-security.md) | API 速查 | 所有使用者 | 接入前确认失败语义和限制 | 解密失败返回 `null` 或 `false`，不抛到业务层 |

## 文档目录

| 文档 | 内容 |
| --- | --- |
| [01. Key、字节与字符串](./docs/01-key-bytes-string.md) | 原始 AES key 模式和短文本加密 |
| [02. 密码派生与 AAD](./docs/02-password-aad.md) | 密码模式、AAD、包格式 |
| [03. 文件加解密](./docs/03-file-encryption.md) | 大文件流式加密和失败清理 |
| [04. 异步与安全约束](./docs/04-async-security.md) | 异步 API、失败语义、安全清单 |

## 依赖

```kotlin
dependencies {
    implementation(project(":itg-encrypt"))
}
```

## 包名

- `EncryptUtils`：`com.itg.itg_encrypt.core.EncryptUtils`