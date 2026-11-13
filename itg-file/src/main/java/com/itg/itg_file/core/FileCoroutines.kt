package com.itg.itg_file.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

/** Runs blocking file work on Dispatchers.IO while following the caller's Job. */
object FileCoroutines {
    suspend fun <T> withIo(block: () -> T): T = runInterruptible(Dispatchers.IO, block)
}
