package io.github.fopwoc.mods.framework.ui.compose.minecraft

import io.github.fopwoc.mods.framework.ui.compose.layout.InputTarget
import io.github.fopwoc.mods.framework.ui.compose.layout.Rect
import io.github.fopwoc.mods.framework.ui.compose.layout.RenderContext
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.element.HostedWidgetKey
import io.github.fopwoc.mods.framework.ui.compose.model.style.TextFieldStyle
import io.github.fopwoc.mods.framework.ui.compose.state.TextFieldState

internal class MinecraftRenderContext(
    private val frame: MinecraftRenderFrameContext,
    private val hostedWidgets: MinecraftHostedWidgetRegistry,
    appendInputTarget: (InputTarget) -> Unit,
    private val focusTextField: (TextFieldState) -> Unit,
    callbacks: MinecraftPrimitiveRenderCallbacks
) : RenderContext {
    override val viewportWidth: Int
        get() = frame.viewportWidth

    override val viewportHeight: Int
        get() = frame.viewportHeight

    override val mouseX: Int
        get() = frame.mouseX

    override val mouseY: Int
        get() = frame.mouseY

    private val textMetrics = MinecraftFontTextMetrics(frame.font)
    private val primitiveDrawer = MinecraftPrimitiveDrawer(
        font = frame.font,
        callbacks = callbacks
    )
    private val clipState = MinecraftClipState(
        frame = frame,
        appendInputTarget = appendInputTarget
    )

    override val lineHeight: Int
        get() = textMetrics.lineHeight

    override fun textWidth(text: String): Int = textMetrics.textWidth(text)

    override fun wrapText(text: String, maxWidth: Int): List<String> = textMetrics.wrapText(text, maxWidth)

    override fun fillRect(left: Int, top: Int, right: Int, bottom: Int, color: Color) {
        primitiveDrawer.fillRect(left, top, right, bottom, color)
    }

    override fun drawHorizontalLine(startX: Int, endX: Int, y: Int, color: Color) {
        primitiveDrawer.drawHorizontalLine(startX, endX, y, color)
    }

    override fun drawVerticalLine(x: Int, startY: Int, endY: Int, color: Color) {
        primitiveDrawer.drawVerticalLine(x, startY, endY, color)
    }

    override fun drawText(text: String, x: Int, y: Int, color: Color, shadow: Boolean) {
        primitiveDrawer.drawText(text, x, y, color, shadow)
    }

    override fun drawVanillaButton(
        bounds: Rect,
        hostKey: HostedWidgetKey,
        text: String,
        enabled: Boolean,
        onClick: () -> Unit
    ) {
        drawMinecraftHostedButton(
            registry = hostedWidgets,
            environment = hostedWidgetEnvironment(),
            bounds = bounds,
            hostKey = hostKey,
            text = text,
            enabled = enabled,
            onClick = onClick
        )
    }

    override fun drawVanillaCheckbox(
        bounds: Rect,
        hostKey: HostedWidgetKey,
        label: String,
        checked: Boolean,
        enabled: Boolean,
        onCheckedChange: (Boolean) -> Unit
    ) {
        drawMinecraftHostedCheckbox(
            registry = hostedWidgets,
            environment = hostedWidgetEnvironment(),
            bounds = bounds,
            hostKey = hostKey,
            label = label,
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange
        )
    }

    override fun drawVanillaTextField(
        bounds: Rect,
        hostKey: HostedWidgetKey,
        state: TextFieldState,
        placeholder: String,
        enabled: Boolean,
        style: TextFieldStyle
    ) {
        drawMinecraftHostedTextField(
            registry = hostedWidgets,
            environment = hostedWidgetEnvironment(),
            bounds = bounds,
            hostKey = hostKey,
            state = state,
            placeholder = placeholder,
            enabled = enabled,
            style = style
        )
    }

    override fun drawVanillaSlider(
        bounds: Rect,
        hostKey: HostedWidgetKey,
        value: Double,
        valueRangeStart: Double,
        valueRangeEnd: Double,
        label: String,
        suffix: String,
        enabled: Boolean,
        showDecimal: Boolean,
        onValueChange: (Double) -> Unit
    ) {
        drawMinecraftHostedSlider(
            registry = hostedWidgets,
            environment = hostedWidgetEnvironment(),
            bounds = bounds,
            hostKey = hostKey,
            value = value,
            valueRangeStart = valueRangeStart,
            valueRangeEnd = valueRangeEnd,
            label = label,
            suffix = suffix,
            enabled = enabled,
            showDecimal = showDecimal,
            onValueChange = onValueChange
        )
    }

    override fun drawVanillaSelectableList(
        bounds: Rect,
        hostKey: HostedWidgetKey,
        items: List<String>,
        selectedIndex: Int,
        rowHeight: Int,
        onSelectedIndexChange: (Int) -> Unit
    ) {
        drawMinecraftHostedSelectableList(
            registry = hostedWidgets,
            environment = hostedWidgetEnvironment(),
            bounds = bounds,
            hostKey = hostKey,
            items = items,
            selectedIndex = selectedIndex,
            rowHeight = rowHeight,
            onSelectedIndexChange = onSelectedIndexChange
        )
    }

    override fun registerInputTarget(target: InputTarget) {
        clipState.registerInputTarget(target)
    }

    override fun withClipRect(rect: Rect, block: () -> Unit) {
        clipState.withClipRect(rect, block)
    }

    fun resetClipState() {
        clipState.reset()
    }

    private fun hostedWidgetEnvironment(): MinecraftHostedWidgetRenderEnvironment {
        return MinecraftHostedWidgetRenderEnvironment(
            frame = frame,
            registerInputTarget = ::registerInputTarget,
            focusTextField = focusTextField
        )
    }
}
