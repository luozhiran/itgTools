package com.itg.itg_thread_pools

import android.os.Handler
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.itg.itg_thread_pools.manager.HandlerManager
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class HandlerManagerInstrumentedTest {
    @Test
    fun registeredCallbackReceivesMessage() {
        val name = "handler-test"
        val latch = CountDownLatch(1)
        HandlerManager.registerMessageCallback(name, Handler.Callback { message ->
            if (message.what == 42) latch.countDown()
            true
        })
        try {
            HandlerManager.sendMessage(name, 42)
            assertTrue(latch.await(3, TimeUnit.SECONDS))
        } finally {
            HandlerManager.quit(name)
        }
    }
}
