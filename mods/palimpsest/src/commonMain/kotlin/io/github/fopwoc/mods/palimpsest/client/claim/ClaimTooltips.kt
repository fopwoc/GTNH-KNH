package io.github.fopwoc.mods.palimpsest.client.claim

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.foundation.Box
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.unit.uu
import io.github.fopwoc.mods.palimpsest.map.MapCamera
import kotlin.math.floor

/** Hover targets for the owner and the server-reported force-loading state. */
@Composable
fun ClaimTooltips(camera: MapCamera, marks: List<ClaimMark>) {
    val side = 16 * camera.pixelsPerBlock
    if (side < 6) return
    marks.forEach { mark ->
        val (x, y) = camera.screenAt(mark.chunkX * 16.0, mark.chunkZ * 16.0)
        if (x + side < 0 || y + side < 0 || x >= camera.width || y >= camera.height) return@forEach
        Box(
            modifier =
                Modifier.offset(floor(x).toInt().uu, floor(y).toInt().uu)
                    .size(side.toInt().coerceAtLeast(1).uu)
                    .tooltip(mark.description)
        ) {}
    }
}
