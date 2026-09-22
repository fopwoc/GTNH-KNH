package io.github.fopwoc.mods.framework.ui.compose.layout.render

import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuCanvasFrame
import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputTarget
import io.github.fopwoc.mods.framework.ui.compose.layout.core.Rect
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.input.KeyModifiers

internal interface RenderContext : TextMetrics {
    val viewportWidth: Int
    val viewportHeight: Int
    val mouseX: Int
    val mouseY: Int

    fun fillRect(left: Int, top: Int, right: Int, bottom: Int, color: Color)

    fun drawHorizontalLine(startX: Int, endX: Int, y: Int, color: Color)

    fun drawVerticalLine(x: Int, startY: Int, endY: Int, color: Color)

    fun drawText(text: String, x: Int, y: Int, color: Color, shadow: Boolean)

    fun registerInputTarget(target: InputTarget)

    fun withClipRect(rect: Rect, block: () -> Unit)

    fun drawGpuCanvas(bounds: Rect, frame: GpuCanvasFrame, handle: Any) = Unit

    val textFields: TextFieldHost
        get() = TextFieldHost.None

    /** Draws a vanilla control face stretched over the rectangle. */
    fun drawWidget(widget: Widget, x: Int, y: Int, width: Int, height: Int) = Unit

    fun playClickSound() = Unit

    /** Modifier keys held right now; read inside input callbacks. */
    fun keyModifiers(): KeyModifiers = KeyModifiers.None
}
