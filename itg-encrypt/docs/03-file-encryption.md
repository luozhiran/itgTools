# 03. 文件加解密

本节说明大文件的流式加密和解密。

## 适用条件

- 文件较大，不适合一次性读入内存。
- 输入路径可读，输出目录可写。
- 需要失败时避免留下不可识别半成品。

## 推荐做法

```kotlin
EncryptUtils.encryptFile(srcPath, destPath, key)
EncryptUtils.decryptFile(encryptedPath, restoredPath, key)
```

## 可复制 Demo

```kotlin
import com.itg.itg_encrypt.core.EncryptUtils

val key = EncryptUtils.generateAesKey()
val ok = EncryptUtils.encryptFile(
    srcPath = context.filesDir.resolve("input.bin").absolutePath,
    destPath = context.filesDir.resolve("input.bin.enc").absolutePath,
    key = key
)

if (ok) {
    val restored = EncryptUtils.decryptFile(
        srcPath = context.filesDir.resolve("input.bin.enc").absolutePath,
        destPath = context.filesDir.resolve("input.bin.dec").absolutePath,
        key = key
    )
    check(restored)
}
```

## 关键说明

- 文件接口采用流式处理，适合大文件。
- 加密失败或解密失败时会尝试清理输出半成品。
- `encryptFileWithPassword/decryptFileWithPassword` 用于密码模式文件。
- 源路径和目标路径不要相同。
- Android 公共目录写入仍受系统存储权限和分区存储限制影响。

## 验证方式

- 加密返回 `true` 后目标文件应存在且大于 0。
- 解密成功后的文件内容应和原文件一致，可用 hash 校验。

[返回 README](../README.md)