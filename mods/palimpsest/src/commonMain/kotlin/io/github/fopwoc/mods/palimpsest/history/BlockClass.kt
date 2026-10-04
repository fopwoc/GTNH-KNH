package io.github.fopwoc.mods.palimpsest.history

import io.github.fopwoc.palimpsest.db.BlockKind

/** How the map treats a block: its [kind] for the scan rules, and the colour it is drawn in. */
data class BlockClass(val kind: BlockKind, val color: Int, val tint: Int)
