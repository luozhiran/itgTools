package com.itg.concurrent

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * A managed coroutine scope for callers that do not own an Android lifecycle.
 *
 * Hold this object for long-running work and call [cancel] or [close] when the
 * owner is released.
 */
class ConcurrentScope internal constructor(
    private val delegate: CoroutineScope
) : CoroutineScope by delegate, AutoCloseable {

    fun launch(block: suspend CoroutineScope.() -> Unit): Job {
        return delegate.launch(block = block)
    }

    fun cancel() {
        delegate.cancel()
    }

    override fun close() {
        cancel()
    }
}