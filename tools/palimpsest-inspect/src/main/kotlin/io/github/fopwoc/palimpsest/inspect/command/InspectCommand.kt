package io.github.fopwoc.palimpsest.inspect.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context

/** The root: only subcommands do anything. */
class InspectCommand : CliktCommand("palimpsest-inspect") {
    override fun help(context: Context) =
        "Read a Palimpsest map history to debug it. Works on a temporary copy, so it is safe on " +
            "the world of a running game."

    override fun run() = Unit
}
