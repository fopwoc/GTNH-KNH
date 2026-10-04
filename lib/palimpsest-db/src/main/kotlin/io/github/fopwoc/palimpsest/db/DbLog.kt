package io.github.fopwoc.palimpsest.db

/**
 * Where the database reports. The mod forwards to the Minecraft log, benchmarks to the console.
 * Tags name the layer: `open`, `writer`, `read`. Called from any of the database's threads, several
 * at once.
 */
fun interface DbLog {
    fun log(level: LogLevel, tag: String, message: String, error: Throwable?)
}
