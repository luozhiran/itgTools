package com.itg.itg_file.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

/** 在 IO 调度器执行阻塞文件操作，并遵循调用方协程的取消状态。 */
object FileCoroutines {
    suspend fun <T> withIo(block: () -> T): T = runInterruptible(Dispatchers.IO, block)
}
