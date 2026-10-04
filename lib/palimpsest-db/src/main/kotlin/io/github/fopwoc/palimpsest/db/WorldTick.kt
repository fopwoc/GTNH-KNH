package io.github.fopwoc.palimpsest.db

/**
 * World time in game ticks: the same on every computer, growing only while the world runs and
 * untouched by `/time set`. History is ordered by it; wall-clock time is only a label.
 */
@JvmInline
value class WorldTick(val value: Long) : Comparable<WorldTick> {
    override fun compareTo(other: WorldTick): Int = value.compareTo(other.value)
}
