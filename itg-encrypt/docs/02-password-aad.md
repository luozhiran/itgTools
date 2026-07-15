# 02. 密码派生与 AAD

本节说明密码模式和 `associatedData` 的使用边界。

## 适用条件

- 密钥来自用户密码、口令或业务密码。
- 希望密文绑定业务上下文，防止元数据被替换。
- 可以在解密时拿到同一份密码和 AAD。

## 推荐做法

```kotlin
val packet = EncryptUtils.encryptBytesWithPassword(plain, password)
val plain = EncryptUtils.decryptBytesWithPassword(packet!!, password)
```

## 可复制 Demo

```kotlin
import com.itg.itg_encrypt.core.EncryptUtils

val password = "correct horse battery staple".toCharArray()
val aad = "profile:v1".toByteArray()

val cipher = EncryptUtils.encryptStringWithPassword(
    plainText = "private profile",
    password = password,
    associatedData = aad
)

val plain = EncryptUtils.decryptStringWithPassword(
    packetBase64 = cipher!!,
    password = password,
    associatedData = aad
)

check(plain == "private profile")
```

## 关键说明

- 密码模式优先使用 `PBKDF2WithHmacSHA256`。
- salt、IV、迭代次数、AAD 等元数据会写入自描述包头。
- AAD 不会被加密，但会参与 GCM 认证。
- 加密和解密的 AAD 必须逐字节一致，否则解密失败。
- 不要降低 KDF 迭代次数到很低的值。

## 验证方式

- 用错误密码解密应返回 `null`。
- 改变 AAD 后解密应失败。

[返回 README](../README.md)