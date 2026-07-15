# 01. Key、字节与字符串

本节说明使用随机 AES key 加密内存中的字节数组或短字符串。

## 适用条件

- 调用方能够安全保存或传递 AES key。
- 明文数据已经在内存中。
- 需要加密短 JSON、token、配置或二进制片段。

## 推荐做法

```kotlin
val key = EncryptUtils.generateAesKey()
val packet = EncryptUtils.encryptBytes(plain, key)
val plain = EncryptUtils.decryptBytes(packet!!, key)
```

## 可复制 Demo

```kotlin
import com.itg.itg_encrypt.core.EncryptUtils

val key = EncryptUtils.generateAesKey()
val cipherText = EncryptUtils.encryptString("top secret", key)
val plainText = EncryptUtils.decryptString(cipherText!!, key)

check(plainText == "top secret")
```

## 关键说明

- 默认使用 `AES/GCM/NoPadding`。
- `generateAesKey()` 默认生成模块定义的 key 长度。
- `encryptString()` 返回 Base64 字符串，适合持久化到文本配置。
- 解密 key 不匹配时返回 `null`。
- 不要把固定字符串直接当 AES key 使用。

## 验证方式

- 同一 key 加密后能解密回原文。
- 换一个 key 解密应返回 `null`。

[返回 README](../README.md)