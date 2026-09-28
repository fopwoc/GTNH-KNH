package io.github.fopwoc.mods.palimpsest.client.prospecting

import com.dyonovan.tcnodetracker.TCNodeTracker
import net.minecraft.client.resources.I18n

/** Borrows scanned aura nodes from TCNodeTracker's current-world client list. */
object TrackedAuraNodes {
    fun read(dimensionId: Int): List<ProspectingMark> =
        TCNodeTracker.nodelist
            .asSequence()
            .filter { it.dim == dimensionId }
            .map { node ->
                val type = I18n.format("nodetype.${node.type}.name")
                val modifier =
                    node.mod.takeUnless { it == "BLANK" }?.let { I18n.format("nodemod.$it.name") }
                val aspects =
                    node.aspect.entries
                        .sortedByDescending { it.value }
                        .joinToString { "${it.key} ${it.value}" }
                ProspectingMark(
                    kind = ProspectingMark.Kind.NODE,
                    x = node.x,
                    y = node.y,
                    z = node.z,
                    name = "Aura node",
                    detail =
                        listOfNotNull(type, modifier, aspects.takeIf(String::isNotEmpty))
                            .joinToString(" · "),
                )
            }
            .toList()
}
