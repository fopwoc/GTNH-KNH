package io.github.fopwoc.mods.palimpsest.map

import java.util.Locale

/**
 * Roughly what map history costs on disk at a commit interval, for the settings screen.
 *
 * Each commit path-copies the tree from the changed tiles to a new root: about 22 nodes, half a
 * kilobyte, whatever else changed. The tiles themselves are deltas of a few dozen bytes. The
 * estimate assumes a player whose surroundings change every interval (machines, crops, mobs), so it
 * is the ceiling for continuous play rather than a typical figure.
 */
object MapStorageEstimate {
    private const val SECONDS_PER_DAY = 86_400.0
    /** Root record plus the path-copied nodes of one commit, measured on a 64×64-tile world. */
    private const val PATH_BYTES_PER_COMMIT = 520.0
    private const val DELTA_BYTES_PER_TILE = 40.0
    /** Chunks assumed to change every interval around an active player. */
    private const val ACTIVE_TILES_PER_COMMIT = 4.0
    private const val KIB = 1024.0
    private const val MIB = KIB * 1024
    private const val GIB = MIB * 1024

    fun bytesPerDay(commitIntervalSeconds: Int): Double {
        val commits = SECONDS_PER_DAY / commitIntervalSeconds.coerceAtLeast(1)
        return commits * (PATH_BYTES_PER_COMMIT + ACTIVE_TILES_PER_COMMIT * DELTA_BYTES_PER_TILE)
    }

    /** E.g. `24 h of play ≈ 59 MB`. */
    fun describeDay(commitIntervalSeconds: Int): String =
        "24 h of play ≈ ${formatBytes(bytesPerDay(commitIntervalSeconds))}"

    fun formatBytes(bytes: Double): String =
        when {
            bytes >= GIB -> String.format(Locale.ROOT, "%.1f GB", bytes / GIB)
            bytes >= MIB -> String.format(Locale.ROOT, "%.0f MB", bytes / MIB)
            bytes >= KIB -> String.format(Locale.ROOT, "%.0f KB", bytes / KIB)
            else -> String.format(Locale.ROOT, "%.0f B", bytes)
        }
}
