package io.github.fopwoc.palimpsest.db

import java.util.concurrent.CompletableFuture

/** A read in flight: wait on or chain [result]; [cancel] drops work that has not started yet. */
interface Request<T> {
    val result: CompletableFuture<T>

    fun cancel()
}
