package io.github.fopwoc.mods.framework.log

import java.util.logging.Level

/** Fallback backend over `java.util.logging` for code running outside a game. */
internal object JavaLogBackend : LogBackend {
    override fun sink(name: String): LogSink = JavaLogSink(java.util.logging.Logger.getLogger(name))
}

private class JavaLogSink(private val logger: java.util.logging.Logger) : LogSink {
    override fun isEnabled(level: LogLevel): Boolean = logger.isLoggable(level.julLevel)

    override fun log(level: LogLevel, message: String, args: Array<out Any?>) {
        val throwable = args.lastOrNull() as? Throwable
        val values = if (throwable != null) args.dropLast(1) else args.asList()
        logger.log(level.julLevel, formatPlaceholders(message, values), throwable)
    }
}

private val LogLevel.julLevel: Level
    get() =
        when (this) {
            LogLevel.DEBUG -> Level.FINE
            LogLevel.INFO -> Level.INFO
            LogLevel.WARN -> Level.WARNING
            LogLevel.ERROR -> Level.SEVERE
        }

/** SLF4J-style `{}` substitution; surplus placeholders stay literal. */
internal fun formatPlaceholders(message: String, args: List<Any?>): String = buildString {
    var cursor = 0
    args.forEach { arg ->
        val index = message.indexOf("{}", cursor)
        if (index < 0) return@forEach
        append(message, cursor, index).append(arg)
        cursor = index + 2
    }
    append(message, cursor, message.length)
}
