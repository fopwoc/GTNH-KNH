package io.github.fopwoc.palimpsest.inspect.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.path
import io.github.fopwoc.palimpsest.db.LogLevel
import io.github.fopwoc.palimpsest.db.PalimpsestDb
import io.github.fopwoc.palimpsest.inspect.history.HistoryCopy
import io.github.fopwoc.palimpsest.inspect.history.StderrLog
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** A command reading one history folder, always through a [HistoryCopy]. */
abstract class HistoryCommand(name: String) : CliktCommand(name) {
    protected val history by
        argument(help = "The world's history folder, e.g. palimpsest/maps/<world>/history")
            .path(mustExist = true, canBeFile = false)

    private val cache by
        option(
                help =
                    "The world's cache folder, e.g. palimpsest/cache/<world>; copied too, so " +
                        "indexes need not be rebuilt"
            )
            .path(mustExist = true, canBeFile = false)

    private val logLevel by
        option(envvar = "LOG_LEVEL", help = "The database's own log on stderr")
            .enum<LogLevel>(ignoreCase = true)
            .default(LogLevel.ERROR)

    protected fun <T> withHistory(read: (PalimpsestDb) -> T): T =
        HistoryCopy.open(history, cache, StderrLog(logLevel)).use { read(it.db) }

    protected fun time(epochMillis: Long): String =
        TIME.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

    private companion object {
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
