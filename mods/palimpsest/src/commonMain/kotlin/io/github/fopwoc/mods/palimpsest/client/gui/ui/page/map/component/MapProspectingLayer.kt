package io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map.component

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.foundation.Box
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.unit.uu
import io.github.fopwoc.mods.palimpsest.client.prospecting.ProspectingLayers
import io.github.fopwoc.mods.palimpsest.client.prospecting.ProspectingMark
import io.github.fopwoc.mods.palimpsest.client.prospecting.color
import io.github.fopwoc.mods.palimpsest.client.prospecting.description
import io.github.fopwoc.mods.palimpsest.client.prospecting.symbol
import io.github.fopwoc.mods.palimpsest.render.iso.MarkProjection
import kotlin.math.roundToInt

/** Discovered markers on the live full map, at their own height or on the ground. */
@Composable
internal fun MapProspectingLayer(
    projection: MarkProjection,
    marks: List<ProspectingMark>,
    enabled: ProspectingLayers.Enabled,
) {
    marks.forEach { mark ->
        if (!enabled.shows(mark)) return@forEach
        val (screenX, screenY) = projection.at(mark.x + 0.5, mark.y?.toDouble(), mark.z + 0.5)
        if (!projection.onCanvas(screenX, screenY, SIZE.toDouble())) return@forEach
        Box(
            modifier =
                Modifier.offset(
                        (screenX - SIZE / 2).roundToInt().uu,
                        (screenY - SIZE / 2).roundToInt().uu,
                    )
                    .size(SIZE.uu)
                    .background(Color(0xE0181A26))
                    .border(mark.kind.color())
                    .tooltip(mark.description)
        ) {
            Text(mark.kind.symbol())
        }
    }
}

private const val SIZE = 14
