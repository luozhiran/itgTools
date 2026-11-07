package com.example.itg_dialog

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DialogSchedulerTest {
    private val directExecutor = Executor { it.run() }

    @Test
    fun companionCallsUseTheSameGlobalQueue() {
        val id = "global-${UUID.randomUUID()}"
        try {
            assertTrue(DialogScheduler.enqueue(DialogMessage(id, 42, "from producer", DialogDeliveryPolicy.REQUIRE_CONSUMER)))
            assertFalse(DialogScheduler.enqueue(DialogMessage(id, 99, "duplicate")))
            val queued = DialogScheduler.pendingSnapshot().single { it.id == id }
            assertEquals("from producer", queued.payload)
            assertEquals(DialogDeliveryPolicy.REQUIRE_CONSUMER, queued.deliveryPolicy)
            assertFalse(DialogScheduler.enqueue(DialogMessage(
                "$id-once", 5, "transient", DialogDeliveryPolicy.DROP_IF_UNHANDLED,
            )))
            assertTrue(DialogScheduler.pendingSnapshot().none { it.id == "$id-once" })
        } finally {
            assertTrue(DialogScheduler.cancel(id))
        }
    }

    @Test
    fun dropIfUnhandledWithoutRegisteredConsumersIsDiscardedImmediately() {
        val scheduler = DialogScheduler<String>(directExecutor, onError = { throw it })
        assertFalse(scheduler.enqueue(DialogMessage("once", 10, "notice", DialogDeliveryPolicy.DROP_IF_UNHANDLED)))
        assertTrue(scheduler.pendingSnapshot().isEmpty())
    }

    @Test
    fun dropIfUnhandledTriesAllCurrentConsumersThenDiscards() {
        val scheduler = DialogScheduler<String>(directExecutor, onError = { throw it })
        val calls = mutableListOf<String>()
        scheduler.register(object : DialogConsumer<String> {
            override fun canHandle(message: DialogMessage<String>): Boolean {
                calls.add("first")
                return false
            }
            override fun show(message: DialogMessage<String>, delivery: DialogDelivery) = Unit
        })
        scheduler.register(object : DialogConsumer<String> {
            override fun canHandle(message: DialogMessage<String>): Boolean {
                calls.add("second")
                return false
            }
            override fun show(message: DialogMessage<String>, delivery: DialogDelivery) = Unit
        })

        assertTrue(scheduler.enqueue(DialogMessage("once", 10, "notice", DialogDeliveryPolicy.DROP_IF_UNHANDLED)))
        assertEquals(listOf("first", "second"), calls)
        assertTrue(scheduler.pendingSnapshot().isEmpty())
        scheduler.retryPending()
        assertEquals(listOf("first", "second"), calls)
    }

    @Test
    fun dropIfUnhandledCanBeHandledByLaterConsumer() {
        val scheduler = DialogScheduler<String>(directExecutor, onError = { throw it })
        val shown = mutableListOf<String>()
        scheduler.register(object : DialogConsumer<String> {
            override fun canHandle(message: DialogMessage<String>) = false
            override fun show(message: DialogMessage<String>, delivery: DialogDelivery) = Unit
        })
        scheduler.register(object : DialogConsumer<String> {
            override fun canHandle(message: DialogMessage<String>) = true
            override fun show(message: DialogMessage<String>, delivery: DialogDelivery) {
                shown.add(message.id)
                delivery.finish()
            }
        })

        assertTrue(scheduler.enqueue(DialogMessage("once", 10, "notice", DialogDeliveryPolicy.DROP_IF_UNHANDLED)))
        assertEquals(listOf("once"), shown)
        assertTrue(scheduler.pendingSnapshot().isEmpty())
    }

    @Test
    fun dropIfUnhandledAfterShowFailureDoesNotStayPending() {
        val scheduler = DialogScheduler<String>(directExecutor, onError = {})
        scheduler.register(object : DialogConsumer<String> {
            override fun canHandle(message: DialogMessage<String>) = true
            override fun show(message: DialogMessage<String>, delivery: DialogDelivery) {
                delivery.retry()
            }
        })

        assertTrue(scheduler.enqueue(DialogMessage("once", 10, "notice", DialogDeliveryPolicy.DROP_IF_UNHANDLED)))
        assertTrue(scheduler.pendingSnapshot().isEmpty())
        assertNull(scheduler.showingMessage())
    }

    @Test
    fun higherPriorityFirstAndEqualPriorityUsesInsertionOrder() {
        val scheduler = DialogScheduler<String>(directExecutor, onError = { throw it })
        scheduler.enqueue(DialogMessage("low-1", 1, "low-1"))
        scheduler.enqueue(DialogMessage("high-1", 10, "high-1"))
        scheduler.enqueue(DialogMessage("low-2", 1, "low-2"))
        scheduler.enqueue(DialogMessage("high-2", 10, "high-2"))

        val shown = mutableListOf<String>()
        val deliveries = mutableListOf<DialogDelivery>()
        scheduler.register(object : DialogConsumer<String> {
            override fun canHandle(message: DialogMessage<String>) = true

            override fun show(message: DialogMessage<String>, delivery: DialogDelivery) {
                shown.add(message.id)
                deliveries.add(delivery)
            }
        })

        assertEquals(listOf("high-1"), shown)
        assertEquals("high-1", scheduler.showingMessage()?.id)
        assertEquals(listOf("high-2", "low-1", "low-2"), scheduler.pendingSnapshot().map { it.id })
        repeat(4) { deliveries[it].finish() }
        assertEquals(listOf("high-1", "high-2", "low-1", "low-2"), shown)
        assertNull(scheduler.showingMessage())
        assertTrue(scheduler.pendingSnapshot().isEmpty())
    }

    @Test
    fun decliningConsumerPassesMessageToNextConsumer() {
        val scheduler = DialogScheduler<Int>(directExecutor, onError = { throw it })
        val calls = mutableListOf<String>()
        scheduler.register(object : DialogConsumer<Int> {
            override fun canHandle(message: DialogMessage<Int>): Boolean {
                calls.add("first")
                return false
            }

            override fun show(message: DialogMessage<Int>, delivery: DialogDelivery) = Unit
        })
        scheduler.register(object : DialogConsumer<Int> {
            override fun canHandle(message: DialogMessage<Int>): Boolean {
                calls.add("second")
                return true
            }

            override fun show(message: DialogMessage<Int>, delivery: DialogDelivery) {
                delivery.finish()
            }
        })

        assertTrue(scheduler.enqueue(DialogMessage("ad", 5, 1)))
        assertEquals(listOf("first", "second"), calls)
        assertTrue(scheduler.pendingSnapshot().isEmpty())
        assertNull(scheduler.showingMessage())
    }

    @Test
    fun newHighPriorityMessageWaitsUntilCurrentDialogCloses() {
        val scheduler = DialogScheduler<Int>(directExecutor, onError = { throw it })
        val shown = mutableListOf<String>()
        val deliveries = mutableListOf<DialogDelivery>()
        scheduler.register(object : DialogConsumer<Int> {
            override fun canHandle(message: DialogMessage<Int>) = true

            override fun show(message: DialogMessage<Int>, delivery: DialogDelivery) {
                shown.add(message.id)
                deliveries.add(delivery)
            }
        })
        scheduler.enqueue(DialogMessage("current", 1, 1))
        scheduler.enqueue(DialogMessage("later-low", 2, 2))
        scheduler.enqueue(DialogMessage("later-high", 10, 10))
        assertEquals(listOf("current"), shown)

        deliveries[0].finish()
        assertEquals(listOf("current", "later-high"), shown)
        deliveries[1].finish()
        deliveries[2].finish()
        assertEquals(listOf("current", "later-high", "later-low"), shown)
    }

    @Test
    fun unhandledHighPriorityDoesNotBlockOtherMessagesAndCanBeRetried() {
        val scheduler = DialogScheduler<String>(directExecutor, onError = { throw it })
        val shown = mutableListOf<String>()
        scheduler.register(object : DialogConsumer<String> {
            override fun canHandle(message: DialogMessage<String>) = message.id == "low"

            override fun show(message: DialogMessage<String>, delivery: DialogDelivery) {
                shown.add(message.id)
                delivery.finish()
            }
        })
        scheduler.enqueue(DialogMessage("high", 10, "high"))
        scheduler.enqueue(DialogMessage("low", 1, "low"))
        assertEquals(listOf("low"), shown)
        assertEquals(listOf("high"), scheduler.pendingSnapshot().map { it.id })

        scheduler.register(object : DialogConsumer<String> {
            override fun canHandle(message: DialogMessage<String>) = message.id == "high"

            override fun show(message: DialogMessage<String>, delivery: DialogDelivery) {
                shown.add(message.id)
                delivery.finish()
            }
        })
        assertEquals(listOf("low", "high"), shown)
        assertTrue(scheduler.pendingSnapshot().isEmpty())
    }

    @Test
    fun closingActiveConsumerRequeuesWithoutLettingStaleDeliveryFinishNewShow() {
        val scheduler = DialogScheduler<Int>(directExecutor, onError = { throw it })
        lateinit var firstDelivery: DialogDelivery
        lateinit var secondDelivery: DialogDelivery
        val first = scheduler.register(object : DialogConsumer<Int> {
            override fun canHandle(message: DialogMessage<Int>) = true

            override fun show(message: DialogMessage<Int>, delivery: DialogDelivery) {
                firstDelivery = delivery
            }
        })
        scheduler.register(object : DialogConsumer<Int> {
            override fun canHandle(message: DialogMessage<Int>) = true

            override fun show(message: DialogMessage<Int>, delivery: DialogDelivery) {
                secondDelivery = delivery
            }
        })
        scheduler.enqueue(DialogMessage("one", 1, 1))
        first.close()
        firstDelivery.finish()

        assertEquals("one", scheduler.showingMessage()?.id)
        secondDelivery.finish()
        assertNull(scheduler.showingMessage())
        assertTrue(scheduler.pendingSnapshot().isEmpty())
    }

    @Test
    fun failedShowKeepsMessageForAnotherConsumer() {
        val errors = mutableListOf<Throwable>()
        val scheduler = DialogScheduler<Int>(directExecutor, onError = errors::add)
        val failing = scheduler.register(object : DialogConsumer<Int> {
            override fun canHandle(message: DialogMessage<Int>) = true

            override fun show(message: DialogMessage<Int>, delivery: DialogDelivery) {
                error("show failed")
            }
        })
        scheduler.enqueue(DialogMessage("one", 1, 1))
        assertEquals(listOf("one"), scheduler.pendingSnapshot().map { it.id })
        assertEquals(1, errors.size)

        failing.close()
        val shown = mutableListOf<String>()
        scheduler.register(object : DialogConsumer<Int> {
            override fun canHandle(message: DialogMessage<Int>) = true

            override fun show(message: DialogMessage<Int>, delivery: DialogDelivery) {
                shown.add(message.id)
                delivery.finish()
            }
        })
        assertEquals(listOf("one"), shown)
    }

    @Test
    fun retryFromOneConsumerOffersTheSameMessageToAnother() {
        val scheduler = DialogScheduler<Int>(directExecutor, onError = { throw it })
        val calls = mutableListOf<String>()
        scheduler.register(object : DialogConsumer<Int> {
            override fun canHandle(message: DialogMessage<Int>) = true

            override fun show(message: DialogMessage<Int>, delivery: DialogDelivery) {
                calls.add("first")
                delivery.retry()
            }
        })
        scheduler.register(object : DialogConsumer<Int> {
            override fun canHandle(message: DialogMessage<Int>) = true

            override fun show(message: DialogMessage<Int>, delivery: DialogDelivery) {
                calls.add("second")
                delivery.finish()
            }
        })
        scheduler.enqueue(DialogMessage("ad", 1, 1))
        assertEquals(listOf("first", "second"), calls)
        assertNull(scheduler.showingMessage())
        assertTrue(scheduler.pendingSnapshot().isEmpty())
    }

    @Test
    fun retryPendingRechecksChangedConsumerConditions() {
        val scheduler = DialogScheduler<Int>(directExecutor, onError = { throw it })
        var ready = false
        var shown = 0
        scheduler.register(object : DialogConsumer<Int> {
            override fun canHandle(message: DialogMessage<Int>) = ready

            override fun show(message: DialogMessage<Int>, delivery: DialogDelivery) {
                shown++
                delivery.finish()
            }
        })
        scheduler.enqueue(DialogMessage("waiting", 5, 1))
        assertEquals(listOf("waiting"), scheduler.pendingSnapshot().map { it.id })

        ready = true
        scheduler.retryPending()
        assertEquals(1, shown)
        assertTrue(scheduler.pendingSnapshot().isEmpty())
    }

    @Test
    fun concurrentProducersDoNotLoseOrDuplicateMessages() {
        val scheduler = DialogScheduler<Int>(directExecutor, onError = { throw it })
        val workers = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        val done = CountDownLatch(200)
        try {
            repeat(200) { index ->
                workers.execute {
                    start.await()
                    scheduler.enqueue(DialogMessage("id-$index", index, index))
                    done.countDown()
                }
            }
            start.countDown()
            assertTrue(done.await(10, TimeUnit.SECONDS))
            assertEquals(200, scheduler.pendingSnapshot().size)
            assertFalse(scheduler.enqueue(DialogMessage("id-100", 100, 100)))

            val shownPriorities = mutableListOf<Int>()
            scheduler.register(object : DialogConsumer<Int> {
                override fun canHandle(message: DialogMessage<Int>) = true

                override fun show(message: DialogMessage<Int>, delivery: DialogDelivery) {
                    shownPriorities.add(message.priority)
                    delivery.finish()
                }
            })
            assertEquals((199 downTo 0).toList(), shownPriorities)
            assertTrue(scheduler.pendingSnapshot().isEmpty())
        } finally {
            workers.shutdownNow()
        }
    }
}
