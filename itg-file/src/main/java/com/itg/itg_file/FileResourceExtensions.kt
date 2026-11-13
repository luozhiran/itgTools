package com.itg.itg_file

import android.content.Context
import com.itg.itg_file.core.FileCoroutines
import com.itg.itg_file.resource.AssetUtils
import java.io.File

/** 将 assets 文件复制到当前文件；[onFailure] 为 null 时只通过返回值判断是否成功。 */
fun File.copyFromAsset(
    context: Context,
    assetPath: String,
    overwrite: Boolean = true,
    onProgress: ((bytesCopied: Long, totalBytes: Long) -> Unit)? = null,
    onFailure: ((Exception) -> Unit)? = null
): Boolean = if (onProgress == null) {
    AssetUtils.copyAssetToFile(context, assetPath, absolutePath, overwrite, onFailure)
} else {
    AssetUtils.copyAssetToFileWithProgress(context, assetPath, absolutePath, overwrite, onProgress, onFailure)
}

/** 在 IO 调度器中复制 assets 文件；回调在 IO 线程执行。 */
suspend fun File.copyFromAssetSuspend(
    context: Context,
    assetPath: String,
    overwrite: Boolean = true,
    onProgress: ((bytesCopied: Long, totalBytes: Long) -> Unit)? = null,
    onFailure: ((Exception) -> Unit)? = null
): Boolean {
    val appContext = context.applicationContext ?: context
    return FileCoroutines.withIo { copyFromAsset(appContext, assetPath, overwrite, onProgress, onFailure) }
}

/** 将 `res/raw` 资源复制到当前文件；[onFailure] 为 null 时只通过返回值判断是否成功。 */
fun File.copyFromRaw(
    context: Context,
    resId: Int,
    overwrite: Boolean = true,
    onProgress: ((bytesCopied: Long, totalBytes: Long) -> Unit)? = null,
    onFailure: ((Exception) -> Unit)? = null
): Boolean = if (onProgress == null) {
    AssetUtils.copyRawToFile(context, resId, absolutePath, overwrite, onFailure)
} else {
    AssetUtils.copyRawToFileWithProgress(context, resId, absolutePath, overwrite, onProgress, onFailure)
}

/** 在 IO 调度器中复制 `res/raw` 资源；回调在 IO 线程执行。 */
suspend fun File.copyFromRawSuspend(
    context: Context,
    resId: Int,
    overwrite: Boolean = true,
    onProgress: ((bytesCopied: Long, totalBytes: Long) -> Unit)? = null,
    onFailure: ((Exception) -> Unit)? = null
): Boolean {
    val appContext = context.applicationContext ?: context
    return FileCoroutines.withIo { copyFromRaw(appContext, resId, overwrite, onProgress, onFailure) }
}
