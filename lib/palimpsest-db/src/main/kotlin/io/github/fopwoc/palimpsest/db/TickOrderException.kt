package io.github.fopwoc.palimpsest.db

/** World time went backwards: this is no longer the same history, and the mod decides what next. */
class TickOrderException(val tick: WorldTick, val latest: WorldTick) :
    IllegalStateException("Commit at tick ${tick.value} is not after the latest, ${latest.value}")
