package io.github.fopwoc.mods.framework.ui.compose.canvas

/** A stable ARGB pixel copy. Rows are tightly packed from the top left of [region]. */
class PixelSnapshot
internal constructor(
    internal val owner: PixelCanvas,
    val region: PixelRegion,
    val argb: IntArray,
    internal val revision: Long,
    internal val tileIndex: Int,
)
