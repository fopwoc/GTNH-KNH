package io.github.fopwoc.mods.framework.event

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

    internal fun emit(value: T) = listeners.forEach { it(value) }
}

fun interface Subscription {
    fun unsubscribe()
}
