package com.itg.itg_web_cache

internal object WebCacheSafeCallbacks {
    fun emit(
        listener: WebCacheEventListener,
        event: WebCacheEvent,
        logger: WebCacheLogger
    ) {
        runCatching {
            listener.onEvent(event)
        }.onFailure {
            log(logger, "Web cache event listener failed.", it)
        }
    }

    fun log(logger: WebCacheLogger, message: String, throwable: Throwable? = null) {
        runCatching {
            logger.log(message, throwable)
        }
    }

    fun complete(callback: ((Boolean) -> Unit)?, success: Boolean, logger: WebCacheLogger) {
        runCatching {
            callback?.invoke(success)
        }.onFailure {
            log(logger, "Web cache completion callback failed.", it)
        }
    }
}
