package io.github.fopwoc.mods.framework.player

import java.util.UUID

/** A player as common code sees one; platform code resolves it back to the native entity by [id]. */
data class GamePlayer(val id: UUID, val name: String)
