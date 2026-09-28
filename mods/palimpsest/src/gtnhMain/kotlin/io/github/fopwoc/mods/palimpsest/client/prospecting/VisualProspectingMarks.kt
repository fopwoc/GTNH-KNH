package io.github.fopwoc.mods.palimpsest.client.prospecting

import com.sinthoras.visualprospecting.database.ClientCache
import com.sinthoras.visualprospecting.database.OreVeinPosition
import com.sinthoras.visualprospecting.database.veintypes.VeinType

/** Reads Visual Prospecting's discovered client data without owning or persisting a copy. */
object VisualProspectingMarks {
    fun read(dimensionId: Int): List<ProspectingMark> {
        val cache = ClientCache.instance
        val ores =
            cache.allOreVeins
                .asSequence()
                .filter {
                    it.dimensionId == dimensionId &&
                        it !== OreVeinPosition.EMPTY_VEIN &&
                        it.veinType !== VeinType.NO_VEIN
                }
                .map { vein ->
                    ProspectingMark(
                        kind = ProspectingMark.Kind.ORE,
                        x = vein.blockX,
                        y = (vein.veinType.minBlockY + vein.veinType.maxBlockY) / 2,
                        z = vein.blockZ,
                        name = vein.veinType.veinName,
                        detail = if (vein.isDepleted) "Depleted" else null,
                    )
                }
        val fluids =
            cache.allUndergroundFluids
                .asSequence()
                .filter { it.dimensionId == dimensionId && it.isProspected }
                .map { fluid ->
                    ProspectingMark(
                        kind = ProspectingMark.Kind.FLUID,
                        x = fluid.blockX,
                        y = null,
                        z = fluid.blockZ,
                        name = fluid.fluid.name.replace('_', ' '),
                        detail = "${fluid.minProduction}-${fluid.maxProduction} L/s",
                    )
                }
        return (ores + fluids).toList()
    }
}
