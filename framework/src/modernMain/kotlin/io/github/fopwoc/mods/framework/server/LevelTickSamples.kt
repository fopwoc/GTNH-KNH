package io.github.fopwoc.mods.framework.server

/** Last completed tick index and the corresponding ring of world tick durations in nanoseconds. */
interface LevelTickSamples {
    val tickTimesNanos: LongArray
    val lastTickIndex: Int
}
