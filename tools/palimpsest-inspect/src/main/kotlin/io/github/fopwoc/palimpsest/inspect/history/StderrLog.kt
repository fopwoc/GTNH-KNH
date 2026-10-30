package io.github.fopwoc.palimpsest.inspect.history

import io.github.fopwoc.palimpsest.db.DbLog
import io.github.fopwoc.palimpsest.db.LogLevel

/** The database's own log on stderr from [threshold] up, so it never mixes with a report. */
class StderrLog(private val threshold: LogLevel) : DbLog {
    override fun log(level: LogLevel, tag: String, message: String, error: Throwable?) {
        if (level < threshold) return
        System.err.println("$level [$tag] $message")
        error?.printStackTrace()
    }
}
