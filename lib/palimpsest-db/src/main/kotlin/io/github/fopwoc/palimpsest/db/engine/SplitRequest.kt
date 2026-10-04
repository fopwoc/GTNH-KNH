package io.github.fopwoc.palimpsest.db.engine

import io.github.fopwoc.palimpsest.db.Request
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService

/**
 * A read split into [parts] independent tasks on [executor], finished by [finish] once all are
 * done; cancelling drops every part not started yet.
 */
internal class SplitRequest<T>(
    executor: ExecutorService,
    parts: List<() -> Unit>,
    finish: () -> T,
) : Request<T> {
    private val tasks = parts.map { FutureRequest(executor, it) }

    override val result: CompletableFuture<T> =
        CompletableFuture.allOf(*tasks.map { it.result }.toTypedArray()).thenApply { finish() }

    override fun cancel() {
        tasks.forEach(FutureRequest<Unit>::cancel)
        result.cancel(false)
    }
}
