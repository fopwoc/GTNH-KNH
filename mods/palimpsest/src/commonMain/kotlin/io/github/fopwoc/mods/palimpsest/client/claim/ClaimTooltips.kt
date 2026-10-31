package io.github.fopwoc.mods.palimpsest.client.claim

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.foundation.Box
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.unit.uu
import io.github.fopwoc.mods.palimpsest.render.iso.MarkProjection
import kotlin.math.roundToInt

/**
 * Hover targets for the owner and the server-reported force-loading state, over each chunk's
 * middle.
 */
@Composable
fun ClaimTooltips(projection: MarkProjection, marks: List<ClaimMark>) {
    val side = CHUNK * projection.camera.pixelsPerBlock * HOVER_SHARE
    if (side < MIN_SIDE) return
    marks.forEach { mark ->
        val (x, y) = projection.at((mark.chunkX + 0.5) * CHUNK, null, (mark.chunkZ + 0.5) * CHUNK)
        if (!projection.onCanvas(x, y, side)) return@forEach
        Box(
            modifier =
                Modifier.offset((x - side / 2).roundToInt().uu, (y - side / 2).roundToInt().uu)
                    .size(side.roundToInt().coerceAtLeast(1).uu)
                    .tooltip(mark.description)
        ) {}
    }
}

private const val CHUNK = 16.0
/** How much of a chunk's width answers to the pointer; at an angle the chunk looks narrower. */
private const val HOVER_SHARE = 0.6
private const val MIN_SIDE = 4.0
