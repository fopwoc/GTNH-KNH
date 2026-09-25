package io.github.fopwoc.mods.framework.log

import kotlin.reflect.KClass

/**
 * Class-named logger for common code. Messages use SLF4J/log4j `{}` placeholders, and a trailing
 * [Throwable] argument is logged as the exception, so the platform backend forwards calls as is.
 */
class Logger internal constructor(val name: String) {
    private val sink by lazy { LogBackend.current.sink(name) }

    fun isEnabled(level: LogLevel): Boolean = sink.isEnabled(level)

    fun debug(message: String, vararg args: Any?) = log(LogLevel.DEBUG, message, args)

    fun info(message: String, vararg args: Any?) = log(LogLevel.INFO, message, args)

    fun warn(message: String, vararg args: Any?) = log(LogLevel.WARN, message, args)

    fun error(message: String, vararg args: Any?) = log(LogLevel.ERROR, message, args)

    private fun log(level: LogLevel, message: String, args: Array<out Any?>) {
        if (sink.isEnabled(level)) sink.log(level, message, args)
    }

    companion object {
        fun of(type: KClass<*>): Logger =
            Logger(checkNotNull(type.qualifiedName) { "Anonymous class $type" })

        /** For a logger per instance scope, e.g. `<class>.<modId>`; prefer [of] otherwise. */
        fun named(name: String): Logger = Logger(name)
    }
}

/** `private val logger = logger<MapTree>()` */
inline fun <reified T : Any> logger(): Logger = Logger.of(T::class)
