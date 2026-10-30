package io.github.fopwoc.palimpsest.inspect

import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import io.github.fopwoc.palimpsest.inspect.command.ChangesCommand
import io.github.fopwoc.palimpsest.inspect.command.ChunkCommand
import io.github.fopwoc.palimpsest.inspect.command.CommitsCommand
import io.github.fopwoc.palimpsest.inspect.command.DimensionsCommand
import io.github.fopwoc.palimpsest.inspect.command.InspectCommand

fun main(args: Array<String>) =
    InspectCommand()
        .subcommands(DimensionsCommand(), CommitsCommand(), ChangesCommand(), ChunkCommand())
        .main(args)
