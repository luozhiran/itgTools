package com.itg.itg_encrypt.core

import android.util.Base64
import com.itg.itg_thread_pools.executor.TaskExecutor
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.concurrent.Future
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 加解密工具集。
 *
 * 设计目标：
 * - 默认使用 AES/GCM/NoPadding，提供认证加密，避免只加密不校验
 * - 密码派生默认使用 PBKDF2WithHmacSHA256
 * - 每次调用都创建独立的 Cipher / KeySpec / IV，避免共享可变状态
 * - 文件接口采用流式处理，避免大文件一次性载入内存
 * - 提供同步与异步入口，便于和线程池模块配合
 */
@Suppress("MemberVisibilityCanBePrivate", "unused")
object EncryptUtils {

    private const val MAGIC = 0x49544745
    private const val FORMAT_VERSION: Byte = 1

    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KEY_ALGORITHM = "AES"
    private const val PASSWORD_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val PASSWORD_ALGORITHM_FALLBACK = "PBKDF2WithHmacSHA1"

    private const val DEFAULT_KEY_BITS = 256
    private const val DEFAULT_SALT_BYTES = 16
    private const val DEFAULT_IV_BYTES = 12
    private const val DEFAULT_TAG_BITS = 128
    private const val DEFAULT_ITERATIONS = 150_000
    private const val DEFAULT_BUFFER_SIZE = 32 * 1024

    private const val MAX_HEADER_BYTES = 1_048_576

    private val secureRandom = object : ThreadLocal<SecureRandom>() {
        override fun initialValue(): SecureRandom = SecureRandom()
    }

    enum class KeyMode(val id: Int) {
        RAW(0),
        PASSWORD_PBKDF2(1);

        companion object {
            fun fromId(id: Int): KeyMode {
                return when (id) {
                    RAW.id -> RAW
                    PASSWORD_PBKDF2.id -> PASSWORD_PBKDF2
                    else -> RAW
                }
            }
        }
    }

    enum class KdfAlgorithm(val id: Int, val algorithmName: String) {
        PBKDF2_SHA256(1, PASSWORD_ALGORITHM),
        PBKDF2_SHA1(2, PASSWORD_ALGORITHM_FALLBACK);

        companion object {
            fun fromId(id: Int): KdfAlgorithm {
                return when (id) {
                    PBKDF2_SHA256.id -> PBKDF2_SHA256
                    PBKDF2_SHA1.id -> PBKDF2_SHA1
                    else -> PBKDF2_SHA256
                }
            }

            fun preferred(): KdfAlgorithm {
                return if (isAlgorithmAvailable(PASSWORD_ALGORITHM)) {
                    PBKDF2_SHA256
                } else {
                    PBKDF2_SHA1
                }
            }
        }
    }

    data class PacketHeader(
        val keyMode: KeyMode,
        val kdfAlgorithm: KdfAlgorithm,
        val iterations: Int,
        val salt: ByteArray,
        val iv: ByteArray,
        val associatedData: ByteArray,
        val tagBits: Int = DEFAULT_TAG_BITS
    ) {
        fun toByteArray(): ByteArray {
            val output = ByteArrayOutputStream()
            DataOutputStream(output).use { data ->
                data.writeInt(MAGIC)
                data.writeByte(FORMAT_VERSION.toInt())
                data.writeByte(keyMode.id)
                data.writeByte(kdfAlgorithm.id)
                data.writeInt(iterations)
                data.writeInt(tagBits)
                data.writeInt(salt.size)
                data.writeInt(iv.size)
                data.writeInt(associatedData.size)
                data.write(salt)
                data.write(iv)
                data.write(associatedData)
            }
            return output.toByteArray()
        }

        companion object {
            fun fromByteArray(packet: ByteArray): Pair<PacketHeader, Int>? {
                if (packet.size < 4) return null
                return try {
                    ByteArrayInputStream(packet).use { input ->
                        DataInputStream(input).use { data ->
                            val magic = data.readInt()
                            if (magic != MAGIC) return null

                            val version = data.readUnsignedByte()
                            if (version != FORMAT_VERSION.toInt()) return null

                            val keyMode = KeyMode.fromId(data.readUnsignedByte())
                            val kdfAlgorithm = KdfAlgorithm.fromId(data.readUnsignedByte())
                            val iterations = data.readInt()
                            val tagBits = data.readInt()
                            val saltSize = data.readInt()
                            val ivSize = data.readInt()
                            val aadSize = data.readInt()

                            if (saltSize < 0 || ivSize < 0 || aadSize < 0) return null
                            if (saltSize > MAX_HEADER_BYTES ||
                                ivSize > MAX_HEADER_BYTES ||
                                aadSize > MAX_HEADER_BYTES
                            ) return null

                            val salt = ByteArray(saltSize)
                            val iv = ByteArray(ivSize)
                            val aad = ByteArray(aadSize)
                            data.readFully(salt)
                            data.readFully(iv)
                            data.readFully(aad)
                            val offset = packet.size - input.available()
                            Pair(
                                PacketHeader(
                                    keyMode = keyMode,
                                    kdfAlgorithm = kdfAlgorithm,
                                    iterations = iterations,
                                    salt = salt,
                                    iv = iv,
                                    associatedData = aad,
                                    tagBits = tagBits
                                ),
                                offset
                            )
                        }
                    }
                } catch (_: EOFException) {
                    null
                } catch (_: IOException) {
                    null
                }
            }
        }
    }

    @JvmStatic
    fun generateAesKey(keyBits: Int = DEFAULT_KEY_BITS): ByteArray {
        require(keyBits == 128 || keyBits == 192 || keyBits == 256) {
            "AES key size must be 128, 192, or 256 bits"
        }
        val key = ByteArray(keyBits / 8)
        random().nextBytes(key)
        return key
    }

    @JvmStatic
    fun generateSalt(bytes: Int = DEFAULT_SALT_BYTES): ByteArray {
        require(bytes > 0) { "Salt size must be greater than 0" }
        val salt = ByteArray(bytes)
        random().nextBytes(salt)
        return salt
    }

    @JvmStatic
    fun generateIv(bytes: Int = DEFAULT_IV_BYTES): ByteArray {
        require(bytes > 0) { "IV size must be greater than 0" }
        val iv = ByteArray(bytes)
        random().nextBytes(iv)
        return iv
    }

    @JvmStatic
    fun deriveKeyFromPassword(
        password: CharArray,
        salt: ByteArray,
        iterations: Int = DEFAULT_ITERATIONS,
        keyBits: Int = DEFAULT_KEY_BITS,
        kdfAlgorithm: KdfAlgorithm = KdfAlgorithm.preferred()
    ): ByteArray {
        require(password.isNotEmpty()) { "password must not be empty" }
        require(salt.isNotEmpty()) { "salt must not be empty" }
        require(iterations > 0) { "iterations must be greater than 0" }
        require(keyBits == 128 || keyBits == 192 || keyBits == 256) {
            "AES key size must be 128, 192, or 256 bits"
        }

        val spec = PBEKeySpec(password, salt, iterations, keyBits)
        return try {
            val factory = SecretKeyFactory.getInstance(kdfAlgorithm.algorithmName)
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    @JvmStatic
    @JvmOverloads
    fun encryptBytes(
        plainText: ByteArray,
        key: ByteArray,
        associatedData: ByteArray? = null
    ): ByteArray? {
        return try {
            val iv = generateIv()
            val header = PacketHeader(
                keyMode = KeyMode.RAW,
                kdfAlgorithm = KdfAlgorithm.preferred(),
                iterations = 0,
                salt = ByteArray(0),
                iv = iv,
                associatedData = associatedData.orEmpty()
            )
            val headerBytes = header.toByteArray()
            val cipher = newCipher(Cipher.ENCRYPT_MODE, key, iv, headerBytes, header.tagBits)
            val ciphertext = cipher.doFinal(plainText)
            headerBytes + ciphertext
        } catch (_: Exception) {
            null
        }
    }

    @JvmStatic
    @JvmOverloads
    fun encryptBytesWithPassword(
        plainText: ByteArray,
        password: CharArray,
        associatedData: ByteArray? = null,
        iterations: Int = DEFAULT_ITERATIONS
    ): ByteArray? {
        val salt = generateSalt()
        val iv = generateIv()
        val aad = associatedData.orEmpty()
        val kdfAlgorithm = KdfAlgorithm.preferred()
        val header = PacketHeader(
            keyMode = KeyMode.PASSWORD_PBKDF2,
            kdfAlgorithm = kdfAlgorithm,
            iterations = iterations,
            salt = salt,
            iv = iv,
            associatedData = aad
        )
        val derived = try {
            deriveKeyFromPassword(password, salt, iterations, kdfAlgorithm = kdfAlgorithm)
        } catch (_: Exception) {
            return null
        }
        return try {
            val headerBytes = header.toByteArray()
            val cipher = newCipher(Cipher.ENCRYPT_MODE, derived, iv, headerBytes, header.tagBits)
            val ciphertext = cipher.doFinal(plainText)
            headerBytes + ciphertext
        } catch (_: Exception) {
            null
        } finally {
            derived.fill(0)
        }
    }

    @JvmStatic
    @JvmOverloads
    fun decryptBytes(
        packet: ByteArray,
        key: ByteArray,
        strictKeyMode: Boolean = true
    ): ByteArray? {
        val parsed = PacketHeader.fromByteArray(packet) ?: return null
        val (header, offset) = parsed
        if (strictKeyMode && header.keyMode != KeyMode.RAW) return null
        if (offset !in 0..packet.size) return null
        return try {
            val headerBytes = header.toByteArray()
            val cipher = newCipher(Cipher.DECRYPT_MODE, key, header.iv, headerBytes, header.tagBits)
            cipher.doFinal(packet, offset, packet.size - offset)
        } catch (_: Exception) {
            null
        }
    }

    @JvmStatic
    @JvmOverloads
    fun decryptBytesWithPassword(
        packet: ByteArray,
        password: CharArray,
        strictKeyMode: Boolean = true
    ): ByteArray? {
        val parsed = PacketHeader.fromByteArray(packet) ?: return null
        val (header, offset) = parsed
        if (strictKeyMode && header.keyMode != KeyMode.PASSWORD_PBKDF2) return null
        if (offset !in 0..packet.size) return null

        val derived = try {
            deriveKeyFromPassword(
                password,
                header.salt,
                header.iterations,
                kdfAlgorithm = header.kdfAlgorithm
            )
        } catch (_: Exception) {
            return null
        }

        return try {
            val headerBytes = header.toByteArray()
            val cipher = newCipher(Cipher.DECRYPT_MODE, derived, header.iv, headerBytes, header.tagBits)
            cipher.doFinal(packet, offset, packet.size - offset)
        } catch (_: Exception) {
            null
        } finally {
            derived.fill(0)
        }
    }

    @JvmStatic
    @JvmOverloads
    fun encryptString(
        plainText: String,
        key: ByteArray,
        associatedData: ByteArray? = null
    ): String? {
        val packet = encryptBytes(plainText.toByteArray(StandardCharsets.UTF_8), key, associatedData)
            ?: return null
        return Base64.encodeToString(packet, Base64.NO_WRAP)
    }

    @JvmStatic
    @JvmOverloads
    fun encryptStringWithPassword(
        plainText: String,
        password: CharArray,
        associatedData: ByteArray? = null,
        iterations: Int = DEFAULT_ITERATIONS
    ): String? {
        val packet = encryptBytesWithPassword(
            plainText = plainText.toByteArray(StandardCharsets.UTF_8),
            password = password,
            associatedData = associatedData,
            iterations = iterations
        ) ?: return null
        return Base64.encodeToString(packet, Base64.NO_WRAP)
    }

    @JvmStatic
    @JvmOverloads
    fun decryptString(
        packetBase64: String,
        key: ByteArray,
        strictKeyMode: Boolean = true
    ): String? {
        val packet = decodeBase64(packetBase64) ?: return null
        val plain = decryptBytes(packet, key, strictKeyMode) ?: return null
        return String(plain, StandardCharsets.UTF_8)
    }

    @JvmStatic
    @JvmOverloads
    fun decryptStringWithPassword(
        packetBase64: String,
        password: CharArray,
        strictKeyMode: Boolean = true
    ): String? {
        val packet = decodeBase64(packetBase64) ?: return null
        val plain = decryptBytesWithPassword(packet, password, strictKeyMode) ?: return null
        return String(plain, StandardCharsets.UTF_8)
    }

    @JvmStatic
    @JvmOverloads
    fun encryptFile(
        srcPath: String,
        destPath: String,
        key: ByteArray,
        overwrite: Boolean = true,
        associatedData: ByteArray? = null,
        bufferSize: Int = DEFAULT_BUFFER_SIZE
    ): Boolean {
        return encryptFileInternal(
            srcPath = srcPath,
            destPath = destPath,
            overwrite = overwrite,
            bufferSize = bufferSize,
            headerFactory = {
                PacketHeader(
                    keyMode = KeyMode.RAW,
                    kdfAlgorithm = KdfAlgorithm.preferred(),
                    iterations = 0,
                    salt = ByteArray(0),
                    iv = generateIv(),
                    associatedData = associatedData.orEmpty()
                )
            },
            cipherFactory = { header ->
                CipherContext(
                    cipher = newCipher(
                        Cipher.ENCRYPT_MODE,
                        key,
                        header.iv,
                        header.toByteArray(),
                        header.tagBits
                    ),
                    cleanup = {}
                )
            }
        )
    }

    @JvmStatic
    @JvmOverloads
    fun encryptFileWithPassword(
        srcPath: String,
        destPath: String,
        password: CharArray,
        overwrite: Boolean = true,
        associatedData: ByteArray? = null,
        iterations: Int = DEFAULT_ITERATIONS,
        bufferSize: Int = DEFAULT_BUFFER_SIZE
    ): Boolean {
        return encryptFileInternal(
            srcPath = srcPath,
            destPath = destPath,
            overwrite = overwrite,
            bufferSize = bufferSize,
            headerFactory = {
                PacketHeader(
                    keyMode = KeyMode.PASSWORD_PBKDF2,
                    kdfAlgorithm = KdfAlgorithm.preferred(),
                    iterations = iterations,
                    salt = generateSalt(),
                    iv = generateIv(),
                    associatedData = associatedData.orEmpty()
                )
            },
            cipherFactory = { header ->
                val derived = deriveKeyFromPassword(
                    password,
                    header.salt,
                    header.iterations,
                    kdfAlgorithm = header.kdfAlgorithm
                )
                try {
                    CipherContext(
                        cipher = newCipher(
                    Cipher.ENCRYPT_MODE,
                    derived,
                    header.iv,
                    header.toByteArray(),
                    header.tagBits
                ),
                        cleanup = { derived.fill(0) }
                    )
                } catch (e: Exception) {
                    derived.fill(0)
                    throw e
                }
            }
        )
    }

    @JvmStatic
    @JvmOverloads
    fun decryptFile(
        srcPath: String,
        destPath: String,
        key: ByteArray,
        overwrite: Boolean = true,
        bufferSize: Int = DEFAULT_BUFFER_SIZE
    ): Boolean {
        return decryptFileInternal(
            srcPath = srcPath,
            destPath = destPath,
            overwrite = overwrite,
            bufferSize = bufferSize
        ) { header ->
            if (header.keyMode != KeyMode.RAW) {
                null
            } else {
                CipherContext(
                    cipher = newCipher(
                    Cipher.DECRYPT_MODE,
                    key,
                    header.iv,
                    header.toByteArray(),
                    header.tagBits
                ),
                    cleanup = {}
                )
            }
        }
    }

    @JvmStatic
    @JvmOverloads
    fun decryptFileWithPassword(
        srcPath: String,
        destPath: String,
        password: CharArray,
        overwrite: Boolean = true,
        bufferSize: Int = DEFAULT_BUFFER_SIZE
    ): Boolean {
        return decryptFileInternal(
            srcPath = srcPath,
            destPath = destPath,
            overwrite = overwrite,
            bufferSize = bufferSize
        ) { header ->
            if (header.keyMode != KeyMode.PASSWORD_PBKDF2) {
                null
            } else {
                val derived = deriveKeyFromPassword(
                    password,
                    header.salt,
                    header.iterations,
                    kdfAlgorithm = header.kdfAlgorithm
                )
                try {
                    CipherContext(
                        cipher = newCipher(
                        Cipher.DECRYPT_MODE,
                        derived,
                        header.iv,
                        header.toByteArray(),
                        header.tagBits
                    ),
                        cleanup = { derived.fill(0) }
                    )
                } catch (e: Exception) {
                    derived.fill(0)
                    throw e
                }
            }
        }
    }

    @JvmStatic
    fun encryptBytesAsync(
        plainText: ByteArray,
        key: ByteArray,
        associatedData: ByteArray? = null,
        onResult: (ByteArray?) -> Unit
    ): Future<*> {
        return TaskExecutor.io { onResult(encryptBytes(plainText, key, associatedData)) }
    }

    @JvmStatic
    fun decryptBytesAsync(
        packet: ByteArray,
        key: ByteArray,
        onResult: (ByteArray?) -> Unit
    ): Future<*> {
        return TaskExecutor.io { onResult(decryptBytes(packet, key)) }
    }

    @JvmStatic
    fun encryptStringAsync(
        plainText: String,
        key: ByteArray,
        associatedData: ByteArray? = null,
        onResult: (String?) -> Unit
    ): Future<*> {
        return TaskExecutor.io { onResult(encryptString(plainText, key, associatedData)) }
    }

    @JvmStatic
    fun decryptStringAsync(
        packetBase64: String,
        key: ByteArray,
        onResult: (String?) -> Unit
    ): Future<*> {
        return TaskExecutor.io { onResult(decryptString(packetBase64, key)) }
    }

    @JvmStatic
    fun encryptBytesWithPasswordAsync(
        plainText: ByteArray,
        password: CharArray,
        associatedData: ByteArray? = null,
        iterations: Int = DEFAULT_ITERATIONS,
        onResult: (ByteArray?) -> Unit
    ): Future<*> {
        return TaskExecutor.io {
            onResult(encryptBytesWithPassword(plainText, password, associatedData, iterations))
        }
    }

    @JvmStatic
    fun decryptBytesWithPasswordAsync(
        packet: ByteArray,
        password: CharArray,
        onResult: (ByteArray?) -> Unit
    ): Future<*> {
        return TaskExecutor.io { onResult(decryptBytesWithPassword(packet, password)) }
    }

    @JvmStatic
    fun encryptStringWithPasswordAsync(
        plainText: String,
        password: CharArray,
        associatedData: ByteArray? = null,
        iterations: Int = DEFAULT_ITERATIONS,
        onResult: (String?) -> Unit
    ): Future<*> {
        return TaskExecutor.io {
            onResult(encryptStringWithPassword(plainText, password, associatedData, iterations))
        }
    }

    @JvmStatic
    fun decryptStringWithPasswordAsync(
        packetBase64: String,
        password: CharArray,
        onResult: (String?) -> Unit
    ): Future<*> {
        return TaskExecutor.io {
            onResult(decryptStringWithPassword(packetBase64, password))
        }
    }

    @JvmStatic
    fun encryptFileAsync(
        srcPath: String,
        destPath: String,
        key: ByteArray,
        overwrite: Boolean = true,
        associatedData: ByteArray? = null,
        onResult: (Boolean) -> Unit
    ): Future<*> {
        return TaskExecutor.io {
            onResult(encryptFile(srcPath, destPath, key, overwrite, associatedData))
        }
    }

    @JvmStatic
    fun encryptFileWithPasswordAsync(
        srcPath: String,
        destPath: String,
        password: CharArray,
        overwrite: Boolean = true,
        associatedData: ByteArray? = null,
        iterations: Int = DEFAULT_ITERATIONS,
        onResult: (Boolean) -> Unit
    ): Future<*> {
        return TaskExecutor.io {
            onResult(
                encryptFileWithPassword(
                    srcPath = srcPath,
                    destPath = destPath,
                    password = password,
                    overwrite = overwrite,
                    associatedData = associatedData,
                    iterations = iterations
                )
            )
        }
    }

    @JvmStatic
    fun decryptFileAsync(
        srcPath: String,
        destPath: String,
        key: ByteArray,
        overwrite: Boolean = true,
        onResult: (Boolean) -> Unit
    ): Future<*> {
        return TaskExecutor.io {
            onResult(decryptFile(srcPath, destPath, key, overwrite))
        }
    }

    @JvmStatic
    fun decryptFileWithPasswordAsync(
        srcPath: String,
        destPath: String,
        password: CharArray,
        overwrite: Boolean = true,
        onResult: (Boolean) -> Unit
    ): Future<*> {
        return TaskExecutor.io {
            onResult(decryptFileWithPassword(srcPath, destPath, password, overwrite))
        }
    }

    private data class CipherContext(
        val cipher: Cipher,
        val cleanup: () -> Unit
    )

    private inline fun encryptFileInternal(
        srcPath: String,
        destPath: String,
        overwrite: Boolean,
        bufferSize: Int,
        headerFactory: () -> PacketHeader,
        cipherFactory: (PacketHeader) -> CipherContext
    ): Boolean {
        if (!isReadableFile(srcPath)) return false
        if (srcPath.isBlank() || destPath.isBlank()) return false
        require(bufferSize > 0) { "bufferSize must be greater than 0" }

        val srcFile = File(srcPath)
        val destFile = File(destPath)
        var success = false
        return try {
            if (srcFile.canonicalFile == destFile.canonicalFile) return false
            if (destFile.exists() && !overwrite) return false
            destFile.parentFile?.mkdirs()

            val header = headerFactory()
            val headerBytes = header.toByteArray()
            val context = cipherFactory(header)
            try {
                FileInputStream(srcFile).use { input ->
                    BufferedInputStream(input, bufferSize).use { bufferedInput ->
                        FileOutputStream(destFile).use { output ->
                            BufferedOutputStream(output, bufferSize).use { bufferedOutput ->
                                bufferedOutput.write(headerBytes)
                                bufferedOutput.flush()
                                CipherOutputStream(bufferedOutput, context.cipher).use { cipherOutput ->
                                    copyStream(bufferedInput, cipherOutput, bufferSize)
                                }
                            }
                        }
                    }
                }
                success = true
                true
            } finally {
                context.cleanup.invoke()
                if (!success) {
                    destFile.delete()
                }
            }
        } catch (_: Exception) {
            if (!success) {
                destFile.delete()
            }
            false
        }
    }

    private inline fun decryptFileInternal(
        srcPath: String,
        destPath: String,
        overwrite: Boolean,
        bufferSize: Int,
        cipherFactory: (PacketHeader) -> CipherContext?
    ): Boolean {
        if (!isReadableFile(srcPath)) return false
        if (srcPath.isBlank() || destPath.isBlank()) return false
        require(bufferSize > 0) { "bufferSize must be greater than 0" }

        val srcFile = File(srcPath)
        val destFile = File(destPath)
        var success = false
        return try {
            if (srcFile.canonicalFile == destFile.canonicalFile) return false
            if (destFile.exists() && !overwrite) return false
            destFile.parentFile?.mkdirs()

            FileInputStream(srcFile).use { input ->
                BufferedInputStream(input, bufferSize).use { bufferedInput ->
                    val dataInput = DataInputStream(bufferedInput)
                    val header = readHeader(dataInput) ?: return false
                    val context = cipherFactory(header) ?: return false
                    try {
                        FileOutputStream(destFile).use { output ->
                            BufferedOutputStream(output, bufferSize).use { bufferedOutput ->
                                CipherInputStream(bufferedInput, context.cipher).use { cipherInput ->
                                    copyStream(cipherInput, bufferedOutput, bufferSize)
                                }
                            }
                        }
                        success = true
                        true
                    } finally {
                        context.cleanup.invoke()
                        if (!success) {
                            destFile.delete()
                        }
                    }
                }
            }
        } catch (_: Exception) {
            if (!success) {
                destFile.delete()
            }
            false
        }
    }

    private fun readHeader(input: DataInputStream): PacketHeader? {
        return try {
            val magic = input.readInt()
            if (magic != MAGIC) return null
            val version = input.readUnsignedByte()
            if (version != FORMAT_VERSION.toInt()) return null
            val keyMode = KeyMode.fromId(input.readUnsignedByte())
            val kdfAlgorithm = KdfAlgorithm.fromId(input.readUnsignedByte())
            val iterations = input.readInt()
            val tagBits = input.readInt()
            val saltSize = input.readInt()
            val ivSize = input.readInt()
            val aadSize = input.readInt()

            if (saltSize < 0 || ivSize < 0 || aadSize < 0) return null
            if (saltSize > MAX_HEADER_BYTES || ivSize > MAX_HEADER_BYTES || aadSize > MAX_HEADER_BYTES) return null

            val salt = ByteArray(saltSize)
            val iv = ByteArray(ivSize)
            val aad = ByteArray(aadSize)
            input.readFully(salt)
            input.readFully(iv)
            input.readFully(aad)

            PacketHeader(
                keyMode = keyMode,
                kdfAlgorithm = kdfAlgorithm,
                iterations = iterations,
                salt = salt,
                iv = iv,
                associatedData = aad,
                tagBits = tagBits
            )
        } catch (_: IOException) {
            null
        }
    }

    private fun newCipher(
        mode: Int,
        rawKey: ByteArray,
        iv: ByteArray,
        authenticatedData: ByteArray,
        tagBits: Int
    ): Cipher {
        require(rawKey.size == 16 || rawKey.size == 24 || rawKey.size == 32) {
            "AES key must be 16, 24, or 32 bytes"
        }
        require(iv.size == DEFAULT_IV_BYTES) {
            "AES-GCM IV must be $DEFAULT_IV_BYTES bytes"
        }
        require(tagBits == 96 || tagBits == 104 || tagBits == 112 || tagBits == 120 || tagBits == 128) {
            "GCM tag length must be one of the standard values"
        }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val keySpec = SecretKeySpec(rawKey, KEY_ALGORITHM)
        val gcmSpec = GCMParameterSpec(tagBits, iv)
        cipher.init(mode, keySpec, gcmSpec)
        if (authenticatedData.isNotEmpty()) {
            cipher.updateAAD(authenticatedData)
        }
        return cipher
    }

    private fun isAlgorithmAvailable(algorithm: String): Boolean {
        return try {
            SecretKeyFactory.getInstance(algorithm)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun random(): SecureRandom = secureRandom.get()

    private fun isReadableFile(path: String): Boolean {
        if (path.isBlank()) return false
        val file = File(path)
        return file.isFile && file.canRead()
    }

    private fun copyStream(
        input: java.io.InputStream,
        output: java.io.OutputStream,
        bufferSize: Int
    ) {
        val buffer = ByteArray(bufferSize)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
        }
        output.flush()
    }

    private fun ByteArray?.orEmpty(): ByteArray = this ?: ByteArray(0)

    private fun decodeBase64(value: String): ByteArray? {
        return try {
            Base64.decode(value, Base64.DEFAULT)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
