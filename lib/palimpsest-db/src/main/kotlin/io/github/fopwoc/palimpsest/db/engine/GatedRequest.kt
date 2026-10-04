package io.github.fopwoc.palimpsest.db.engine

import io.github.fopwoc.palimpsest.db.Request
import java.util.concurrent.CompletableFuture

/**
 * A read asked for before what it reads exists: [start] runs once [gate] completes, and its result
 * becomes this one's. Cancelling before then means it never starts.
 */
internal class GatedRequest<T>(gate: CompletableFuture<*>, start: () -> Request<T>) : Request<T> {
    @Volatile private var inner: Request<T>? = null

    override val result = CompletableFuture<T>()

    init {
        gate.whenComplete { _, error ->
            when {
                error != null -> result.completeExceptionally(error)
                result.isDone -> Unit
                else ->
                    start()
                        .also {
                            inner = it
                            // Cancelled between the check above and now: stop what just started.
                            if (result.isCancelled) it.cancel()
                        }
                        .result
                        .whenComplete { value, failure ->
                            if (failure != null) result.completeExceptionally(failure)
                            else result.complete(value)
                        }
            }
        }
    }

    override fun cancel() {
        result.cancel(false)
        inner?.cancel()
    }
}
