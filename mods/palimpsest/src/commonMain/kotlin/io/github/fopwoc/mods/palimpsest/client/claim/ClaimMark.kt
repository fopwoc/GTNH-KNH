package io.github.fopwoc.mods.palimpsest.client.claim

/** A ServerUtilities claim. Loading is only asserted when the server explicitly reports it. */
data class ClaimMark(
    val chunkX: Int,
    val chunkZ: Int,
    val owner: String,
    val color: Int,
    val forceLoaded: Boolean,
) {
    val description: String
        get() = "$owner · ${if (forceLoaded) "Force loaded" else "Load state hidden or inactive"}"
}
