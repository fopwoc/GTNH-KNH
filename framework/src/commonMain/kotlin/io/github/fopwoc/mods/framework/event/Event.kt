package io.github.fopwoc.mods.framework.event

import io.github.fopwoc.mods.framework.log.Logger
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A game event common code subscribes to. The framework's platform source sets fire it from the
 * loader's native event, always on the thread the game runs that event on.
 */
class Event<T> internal constructor() {
    private val listeners = CopyOnWriteArrayList<(T) -> Unit>()

    fun subscribe(listener: (T) -> Unit): Subscription {
        listeners += listener
        return Subscription { listeners -= listener }
    }

    /** Calls every listener; one that throws is logged and does not stop the rest. */
    @Suppress("TooGenericExceptionCaught")
    internal fun emit(value: T) = listeners.forEach { listener ->
        try {
            listener(value)
        } catch (e: Exception) {
            logger.error("Event listener failed", e)
        }
    }

    private companion object {
        val logger = Logger.of(Event::class)
    }
}

fun interface Subscription {
    fun unsubscribe()
}
