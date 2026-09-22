package io.github.fopwoc.mods.framework.log

import java.util.ServiceLoader

/**
 * The platform's logging system. Each platform source set registers its implementation as a
 * `META-INF/services` entry; without one (unit tests, headless tools) JDK logging is used.
 */
interface LogBackend {
    fun sink(name: String): LogSink

    companion object {
        val current: LogBackend by lazy {
            ServiceLoader.load(LogBackend::class.java, LogBackend::class.java.classLoader).firstOrNull()
                ?: JavaLogBackend
        }
    }
}

interface LogSink {
    fun isEnabled(level: LogLevel): Boolean

    /** [message] keeps its `{}` placeholders; a trailing [Throwable] in [args] is the exception. */
    fun log(level: LogLevel, message: String, args: Array<out Any?>)
}
