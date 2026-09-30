package io.github.fopwoc.mods.palimpsest.client.map

/** Never starts another chunk after the client-thread budget expires. One scan is indivisible. */
internal class ChunkScanBudget(
    private val maxChunks: Int,
    private val nanos: Long = 2_000_000,
    private val clock: () -> Long = System::nanoTime,
) {
    private val started = clock()
    private var scanned = 0

    fun take(): Boolean {
        if (scanned >= maxChunks || (scanned > 0 && clock() - started >= nanos)) return false
        scanned++
        return true
    }
}
