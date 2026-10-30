package io.github.fopwoc.palimpsest.inspect.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import io.github.fopwoc.palimpsest.db.DimensionId

/** `--dimension` / `-d`, as the history folder names it: dim0, dim-1, dim184… */
fun CliktCommand.dimensionOption() =
    option("-d", "--dimension", help = "Dimension key as in dimensions/, e.g. dim0 or dim-1")
        .convert { DimensionId(it) }
        .default(DimensionId("dim0"))
