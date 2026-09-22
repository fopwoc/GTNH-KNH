package io.github.fopwoc.mods.framework.log

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager

/** Minecraft 1.7.10 logs through log4j2, which understands `{}` placeholders natively. */
class Log4jLogBackend : LogBackend {
    override fun sink(name: String): LogSink = Log4jLogSink(LogManager.getLogger(name))
}

private class Log4jLogSink(private val logger: org.apache.logging.log4j.Logger) : LogSink {
    override fun isEnabled(level: LogLevel): Boolean = logger.isEnabled(level.log4jLevel)

    override fun log(level: LogLevel, message: String, args: Array<out Any?>) {
        logger.log(level.log4jLevel, message, *args)
    }
}

private val LogLevel.log4jLevel: Level
    get() =
        when (this) {
            LogLevel.DEBUG -> Level.DEBUG
            LogLevel.INFO -> Level.INFO
            LogLevel.WARN -> Level.WARN
            LogLevel.ERROR -> Level.ERROR
        }
