# itg-encrypt

`itg-encrypt` 提供一套可直接在 Android 工程里复用的加解密能力，面向以下场景：

- 短文本或二进制数据加密
- 密码派生密钥加密
- 大文件流式加解密
- 异步执行，和 `itg-thread-pools` 配合

## 设计原则

- 默认使用 `AES/GCM/NoPadding`
- 默认使用随机 `IV`
- 密码派生默认优先 `PBKDF2WithHmacSHA256`
- 包头会被 GCM 认证，防止元数据被篡改
- 不使用全局可变密钥状态，线程安全
- 文件接口采用流式处理，避免大文件一次性进内存

## 依赖方式

模块内部已经依赖 `itg-thread-pools`，调用方只要依赖 `:itg-encrypt` 即可。

```kotlin
implementation(project(":itg-encrypt"))
```

## 核心 API

### 1. 原始密钥模式

适合你已经持有随机生成的 AES key 的场景。

```kotlin
val key = EncryptUtils.generateAesKey()
val plain = "hello".toByteArray()

val packet = EncryptUtils.encryptBytes(plain, key)
val decrypted = EncryptUtils.decryptBytes(packet!!, key)
```

### 2. 密码派生模式

适合用户输入密码、口令或设备级口令的场景。

```kotlin
val password = "correct horse battery staple".toCharArray()
val plain = "secret text".toByteArray()

val packet = EncryptUtils.encryptBytesWithPassword(plain, password)
val decrypted = EncryptUtils.decryptBytesWithPassword(packet!!, password)
```

### 3. 字符串加解密

适合配置、令牌、短 JSON 这类数据。

```kotlin
val key = EncryptUtils.generateAesKey()
val cipherText = EncryptUtils.encryptString("top secret", key)
val plainText = EncryptUtils.decryptString(cipherText!!, key)
```

### 4. 文件加解密

适合大文件、导出包、备份包。

```kotlin
val key = EncryptUtils.generateAesKey()

val ok = EncryptUtils.encryptFile(
    srcPath = "/sdcard/input.bin",
    destPath = "/sdcard/input.bin.enc",
    key = key
)

val restored = EncryptUtils.decryptFile(
    srcPath = "/sdcard/input.bin.enc",
    destPath = "/sdcard/input.bin.dec",
    key = key
)
```

### 5. 异步接口

适合和线程池、页面状态联动。

```kotlin
EncryptUtils.encryptStringAsync(
    plainText = "hello",
    key = EncryptUtils.generateAesKey()
) { cipherText ->
    // 更新 UI 或继续处理
}
```

## AAD 说明

`associatedData` 用来承载附加认证数据。它不会被加密，但会被认证。

适合放：

- 业务版本号
- 文件类型标识
- 会影响解密语义的上下文

注意：

- 加密和解密时必须使用完全一致的 `associatedData`
- 如果你不需要这个能力，可以传 `null`
- 包头本身已经被认证，不需要把包头字段再额外拼进业务层

## 包格式

输出数据采用自描述格式，便于后续扩展：

- magic
- version
- key mode
- KDF algorithm
- iterations
- tag bits
- salt length + salt
- IV length + IV
- AAD length + AAD
- ciphertext

## 安全约束

- 不要复用同一组 `IV`
- 不要把固定字符串当作 AES key
- 密码模式下不要降低迭代次数到很低
- 解密失败会返回 `null` 或 `false`
- 文件操作失败会清理半成品输出

## 推荐用法

如果你是新接入，优先顺序是：

1. 数据已经在内存里，且你有随机 AES key，直接用原始密钥模式
2. 需要用户口令，使用密码派生模式
3. 处理大文件，使用文件流式 API
4. 需要 UI 或后台串联，使用异步 API

## 现有测试

模块内已有单测覆盖：

- 原始密钥加解密
- 密码派生加解密
- 错密钥失败
- 错密码失败
- 包头篡改失败
- 文件往返

