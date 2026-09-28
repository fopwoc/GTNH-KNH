package io.github.fopwoc.mods.palimpsest.client.prospecting

/** One discovered location borrowed from its owning mod for display only. */
data class ProspectingMark(
    val kind: Kind,
    val x: Int,
    val y: Int?,
    val z: Int,
    val name: String,
    val detail: String? = null,
) {
    enum class Kind {
        ORE,
        FLUID,
        NODE,
    }
}
