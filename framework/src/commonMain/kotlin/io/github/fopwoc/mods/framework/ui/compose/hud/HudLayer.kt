package io.github.fopwoc.mods.framework.ui.compose.hud

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * A composed layer drawn over the game while [visible]. [width] and [height] are the GUI-scaled
 * screen size. The composition is released while the layer is hidden.
 */
abstract class HudLayer(val id: String) {
    @Composable abstract fun Content()

    open val visible: Boolean
        get() = true

    var width: Int by mutableIntStateOf(0)
        internal set

    var height: Int by mutableIntStateOf(0)
        internal set

    /** Every frame before drawing, on the render thread; update state the content reads. */
    open fun beforeFrame() = Unit
}
