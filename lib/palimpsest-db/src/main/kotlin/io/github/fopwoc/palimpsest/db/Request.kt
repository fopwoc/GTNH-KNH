package io.github.fopwoc.palimpsest.db

import java.util.concurrent.CompletableFuture

/**
 * A read in flight: wait on or chain [result]; [cancel] drops work that has not started yet. The
 * result completes on a database thread, so anything that needs the render thread, like a texture
 * upload, is handed over by the caller.
 */
interface Request<T> {
    val result: CompletableFuture<T>

    fun cancel()
}
