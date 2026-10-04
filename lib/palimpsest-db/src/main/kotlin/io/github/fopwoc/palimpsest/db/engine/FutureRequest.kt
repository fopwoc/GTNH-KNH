package io.github.fopwoc.palimpsest.db.engine

import io.github.fopwoc.palimpsest.db.Request
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService

/** A read queued on [executor]; cancelling before it starts means it never runs. */
internal class FutureRequest<T>(executor: ExecutorService, task: () -> T) : Request<T> {
    override val result = CompletableFuture<T>()

    private val queued = executor.submit {
        if (!result.isDone) runCatching(task).fold(result::complete, result::completeExceptionally)
    }

    override fun cancel() {
        result.cancel(false)
        queued.cancel(false)
    }
}
