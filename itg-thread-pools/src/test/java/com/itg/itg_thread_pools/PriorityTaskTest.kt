package com.itg.itg_thread_pools

import com.itg.itg_thread_pools.executor.Priority
import com.itg.itg_thread_pools.executor.PriorityRunnable
import org.junit.Assert.assertTrue
import org.junit.Test

class PriorityTaskTest {
    @Test
    fun higherPrioritySortsBeforeLowerPriority() {
        val high = PriorityRunnable(Priority.HIGH, 1, Runnable {})
        val low = PriorityRunnable(Priority.LOW, 2, Runnable {})

        assertTrue(high.compareTo(low) < 0)
        assertTrue(low.compareTo(high) > 0)
    }
}
