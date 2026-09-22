package io.github.fopwoc.mods.framework.log

import org.slf4j.LoggerFactory

/** Modern Minecraft logs through SLF4J on every loader; it understands `{}` placeholders natively. */
class Slf4jLogBackend : LogBackend {
    override fun sink(name: String): LogSink = Slf4jLogSink(LoggerFactory.getLogger(name))
}

private class Slf4jLogSink(private val logger: org.slf4j.Logger) : LogSink {
    override fun isEnabled(level: LogLevel): Boolean =
        when (level) {
            LogLevel.DEBUG -> logger.isDebugEnabled
            LogLevel.INFO -> logger.isInfoEnabled
            LogLevel.WARN -> logger.isWarnEnabled
            LogLevel.ERROR -> logger.isErrorEnabled
        }

    override fun log(level: LogLevel, message: String, args: Array<out Any?>) {
        when (level) {
            LogLevel.DEBUG -> logger.debug(message, *args)
            LogLevel.INFO -> logger.info(message, *args)
            LogLevel.WARN -> logger.warn(message, *args)
            LogLevel.ERROR -> logger.error(message, *args)
        }
    }
}
