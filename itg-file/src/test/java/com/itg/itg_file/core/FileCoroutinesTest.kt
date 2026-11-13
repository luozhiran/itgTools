package com.itg.itg_file.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class FileCoroutinesTest {
    @Test
    fun cancellingCallerInterruptsBlockingFileOperation() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val interrupted = AtomicBoolean(false)
        val job = launch {
            FileCoroutines.withIo {
                started.complete(Unit)
                try {
                    Thread.sleep(30_000)
                } catch (_: InterruptedException) {
                    interrupted.set(true)
                }
            }
        }

        started.await()
        job.cancelAndJoin()
        assertTrue(interrupted.get())
    }
}
