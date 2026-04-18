package io.github.fopwoc.mods.framework.ui.compose.minecraft

import cpw.mods.fml.client.config.GuiButtonExt
import cpw.mods.fml.client.config.GuiCheckBox
import cpw.mods.fml.client.config.GuiSlider
import io.github.fopwoc.mods.framework.ui.compose.layout.ActivePointerSession
import io.github.fopwoc.mods.framework.ui.compose.layout.InputPressResult
import io.github.fopwoc.mods.framework.ui.compose.layout.InputTarget
import io.github.fopwoc.mods.framework.ui.compose.layout.InputTargetKind
import io.github.fopwoc.mods.framework.ui.compose.layout.Rect
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.style.TextFieldStyle
import io.github.fopwoc.mods.framework.ui.compose.state.TextFieldState
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.FontRenderer
import net.minecraft.client.gui.GuiTextField
import kotlin.math.abs

internal data class MinecraftHostedWidgetRenderEnvironment(
    val frame: MinecraftRenderFrameContext,
    val registerInputTarget: (InputTarget) -> Unit,
    val focusTextField: (TextFieldState) -> Unit
) {
    val client: Minecraft
        get() = frame.client

    val font: FontRenderer
        get() = frame.font

    val mouseX: Int
        get() = frame.mouseX

    val mouseY: Int
        get() = frame.mouseY

    val renderEpoch: Int
        get() = frame.renderEpoch
}

internal fun drawMinecraftHostedButton(
    registry: MinecraftHostedWidgetRegistry,
    environment: MinecraftHostedWidgetRenderEnvironment,
    bounds: Rect,
    hostKey: Any,
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    if (bounds.width <= 0 || bounds.height <= 0) {
        return
    }

    val hosted = registry.buttons.getOrPut(hostKey) {
        HostedButton(
            widget = GuiButtonExt(0, bounds.x, bounds.y, bounds.width, bounds.height, text),
            onClick = onClick
        )
    }

    hosted.lastSeenEpoch = environment.renderEpoch
    hosted.onClick = onClick
    updateButtonWidget(
        widget = hosted.widget,
        bounds = bounds,
        text = text,
        enabled = enabled
    )
    hosted.widget.drawButton(environment.client, environment.mouseX, environment.mouseY)
    environment.registerInputTarget(
        InputTarget(
            kind = InputTargetKind.BUTTON,
            bounds = bounds,
            onPress = { clickX, clickY, button ->
                if (!hosted.widget.mousePressed(environment.client, clickX, clickY)) {
                    InputPressResult.Ignored
                } else {
                    hosted.onClick()
                    InputPressResult.captured(
                        ActivePointerSession(
                            button = button,
                            validityCheck = { registry.buttons[hostKey] === hosted },
                            onReleaseHandler = { releaseX, releaseY, releaseButton ->
                                hosted.widget.mouseReleased(releaseX, releaseY)
                                releaseButton == button
                            }
                        )
                    )
                }
            }
        )
    )
}

internal fun drawMinecraftHostedCheckbox(
    registry: MinecraftHostedWidgetRegistry,
    environment: MinecraftHostedWidgetRenderEnvironment,
    bounds: Rect,
    hostKey: Any,
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    if (bounds.width <= 0 || bounds.height <= 0) {
        return
    }

    val hosted = registry.checkboxes.getOrPut(hostKey) {
        HostedCheckbox(
            widget = GuiCheckBox(0, bounds.x, bounds.y, label, checked),
            onCheckedChange = onCheckedChange
        )
    }

    hosted.lastSeenEpoch = environment.renderEpoch
    hosted.onCheckedChange = onCheckedChange
    updateCheckboxWidget(
        widget = hosted.widget,
        bounds = bounds,
        label = label,
        enabled = enabled
    )
    if (hosted.widget.isChecked() != checked) {
        hosted.widget.setIsChecked(checked)
    }
    hosted.widget.drawButton(environment.client, environment.mouseX, environment.mouseY)
    environment.registerInputTarget(
        InputTarget(
            kind = InputTargetKind.CHECKBOX,
            bounds = bounds,
            onPress = { clickX, clickY, button ->
                if (!hosted.widget.mousePressed(environment.client, clickX, clickY)) {
                    InputPressResult.Ignored
                } else {
                    hosted.onCheckedChange(hosted.widget.isChecked())
                    InputPressResult.captured(
                        ActivePointerSession(
                            button = button,
                            validityCheck = { registry.checkboxes[hostKey] === hosted },
                            onReleaseHandler = { releaseX, releaseY, releaseButton ->
                                hosted.widget.mouseReleased(releaseX, releaseY)
                                releaseButton == button
                            }
                        )
                    )
                }
            }
        )
    )
}

internal fun drawMinecraftHostedTextField(
    registry: MinecraftHostedWidgetRegistry,
    environment: MinecraftHostedWidgetRenderEnvironment,
    bounds: Rect,
    hostKey: Any,
    state: TextFieldState,
    placeholder: String,
    enabled: Boolean,
    style: TextFieldStyle
) {
    val hosted = registry.textFields.getOrPut(hostKey) {
        val widget = GuiTextField(environment.font, bounds.x, bounds.y, bounds.width, bounds.height)
        widget.setCanLoseFocus(false)
        HostedTextField(hostKey, state, widget)
    }

    hosted.lastSeenEpoch = environment.renderEpoch
    hosted.currentState = state
    updateTextFieldWidget(
        widget = hosted.widget,
        bounds = bounds,
        state = state,
        enabled = enabled,
        style = style
    )
    hosted.widget.drawTextBox()

    if (state.text.isEmpty() && !state.focused && placeholder.isNotEmpty()) {
        val placeholderX = bounds.x + if (style.drawBackground) 4 else 0
        val placeholderY = bounds.y + ((bounds.height - environment.font.FONT_HEIGHT) / 2).coerceAtLeast(0)
        environment.font.drawStringWithShadow(placeholder, placeholderX, placeholderY, Color.rgb(red = 0x80, green = 0x80, blue = 0x80).argbInt)
    }

    if (enabled) {
        environment.registerInputTarget(
            InputTarget(
                kind = InputTargetKind.TEXT_FIELD,
                bounds = bounds,
                onPress = { clickX, clickY, button ->
                    if (button != 0) {
                        InputPressResult.Ignored
                    } else {
                        environment.focusTextField(state)
                        hosted.widget.mouseClicked(clickX, clickY, button)
                        state.syncFocus(hosted.widget.isFocused)
                        state.text = hosted.widget.text
                        InputPressResult.Consumed
                    }
                }
            )
        )
    }
}

internal fun drawMinecraftHostedSlider(
    registry: MinecraftHostedWidgetRegistry,
    environment: MinecraftHostedWidgetRenderEnvironment,
    bounds: Rect,
    hostKey: Any,
    value: Double,
    valueRangeStart: Double,
    valueRangeEnd: Double,
    label: String,
    suffix: String,
    enabled: Boolean,
    showDecimal: Boolean,
    onValueChange: (Double) -> Unit
) {
    if (bounds.width <= 0 || bounds.height <= 0) {
        return
    }

    val start = minOf(valueRangeStart, valueRangeEnd)
    val end = maxOf(valueRangeStart, valueRangeEnd)
    val coercedValue = value.coerceIn(start, end)
    val prefix = sliderPrefix(label)
    val hosted = registry.sliders[hostKey]
        ?.takeUnless {
            it.label != label ||
                it.suffix != suffix ||
                it.valueRangeStart != start ||
                it.valueRangeEnd != end ||
                it.showDecimal != showDecimal
        }
        ?: createHostedSlider(
            registry = registry,
            hostKey = hostKey,
            bounds = bounds,
            prefix = prefix,
            label = label,
            suffix = suffix,
            valueRangeStart = start,
            valueRangeEnd = end,
            value = coercedValue,
            showDecimal = showDecimal,
            onValueChange = onValueChange
        )

    hosted.lastSeenEpoch = environment.renderEpoch
    hosted.onValueChange = onValueChange
    updateSliderWidget(
        widget = hosted.widget,
        bounds = bounds,
        prefix = prefix,
        suffix = suffix,
        enabled = enabled,
        showDecimal = showDecimal
    )
    updateSliderValue(hosted, coercedValue)
    hosted.widget.drawButton(environment.client, environment.mouseX, environment.mouseY)
    environment.registerInputTarget(
        InputTarget(
            kind = InputTargetKind.SLIDER,
            bounds = bounds,
            onPress = { clickX, clickY, button ->
                if (!hosted.widget.mousePressed(environment.client, clickX, clickY)) {
                    InputPressResult.Ignored
                } else {
                    InputPressResult.captured(
                        ActivePointerSession(
                            button = button,
                            validityCheck = { registry.sliders[hostKey] === hosted },
                            onReleaseHandler = { releaseX, releaseY, releaseButton ->
                                hosted.widget.mouseReleased(releaseX, releaseY)
                                releaseButton == button
                            }
                        )
                    )
                }
            }
        )
    )
}

internal fun drawMinecraftHostedSelectableList(
    registry: MinecraftHostedWidgetRegistry,
    environment: MinecraftHostedWidgetRenderEnvironment,
    bounds: Rect,
    hostKey: Any,
    items: List<String>,
    selectedIndex: Int,
    rowHeight: Int,
    onSelectedIndexChange: (Int) -> Unit
) {
    if (bounds.width <= 0 || bounds.height <= 0) {
        return
    }

    val resolvedRowHeight = rowHeight.coerceAtLeast(12)
    val hosted = registry.selectableLists[hostKey]
        ?.takeUnless { it.slotHeight != resolvedRowHeight }
        ?: HostedSelectableList(environment.client, rowHeight).also { registry.selectableLists[hostKey] = it }

    hosted.lastSeenEpoch = environment.renderEpoch
    hosted.update(
        bounds = bounds,
        items = items,
        selectedIndex = selectedIndex,
        onSelectedIndexChange = onSelectedIndexChange
    )
    hosted.render(environment.mouseX, environment.mouseY)
    environment.registerInputTarget(
        InputTarget(
            kind = InputTargetKind.SELECTABLE_LIST,
            bounds = bounds,
            onPress = { clickX, clickY, button ->
                if (!hosted.handleClick(clickX, clickY)) {
                    InputPressResult.Ignored
                } else {
                    InputPressResult.captured(
                        ActivePointerSession(
                            button = button,
                            validityCheck = { registry.selectableLists[hostKey] === hosted },
                            onDragHandler = { _, dragY -> hosted.handleDrag(dragY) },
                            onReleaseHandler = { _, _, releaseButton ->
                                hosted.handleRelease()
                                releaseButton == button
                            }
                        )
                    )
                }
            },
            onWheel = { _, _, wheelDelta -> hosted.handleWheel(wheelDelta) }
        )
    )
}

private fun createHostedSlider(
    registry: MinecraftHostedWidgetRegistry,
    hostKey: Any,
    bounds: Rect,
    prefix: String,
    label: String,
    suffix: String,
    valueRangeStart: Double,
    valueRangeEnd: Double,
    value: Double,
    showDecimal: Boolean,
    onValueChange: (Double) -> Unit
): HostedSlider {
    lateinit var hosted: HostedSlider
    val widget = GuiSlider(
        0,
        bounds.x,
        bounds.y,
        bounds.width,
        bounds.height,
        prefix,
        suffix,
        valueRangeStart,
        valueRangeEnd,
        value,
        showDecimal,
        true,
        { slider ->
            if (!hosted.suppressCallback) {
                hosted.onValueChange(slider.getValue())
            }
        }
    )
    hosted = HostedSlider(
        widget = widget,
        label = label,
        suffix = suffix,
        valueRangeStart = valueRangeStart,
        valueRangeEnd = valueRangeEnd,
        showDecimal = showDecimal,
        onValueChange = onValueChange
    )
    registry.sliders[hostKey] = hosted
    return hosted
}

private fun updateButtonWidget(widget: GuiButtonExt, bounds: Rect, text: String, enabled: Boolean) {
    updateButtonWidgetBounds(widget, bounds)
    widget.displayString = text
    widget.enabled = enabled
    widget.visible = true
}

private fun updateCheckboxWidget(widget: GuiCheckBox, bounds: Rect, label: String, enabled: Boolean) {
    updateButtonWidgetBounds(widget, bounds, height = bounds.height.coerceAtLeast(11))
    widget.displayString = label
    widget.enabled = enabled
    widget.visible = true
}

private fun updateTextFieldWidget(
    widget: GuiTextField,
    bounds: Rect,
    state: TextFieldState,
    enabled: Boolean,
    style: TextFieldStyle
) {
    updateTextFieldBounds(widget, bounds)
    widget.setEnabled(enabled)
    widget.setMaxStringLength(style.maxLength)
    widget.setTextColor(style.textColor.argbInt)
    widget.setDisabledTextColour(style.disabledTextColor.argbInt)
    widget.setEnableBackgroundDrawing(style.drawBackground)
    if (widget.text != state.text) {
        widget.text = state.text
    }
    widget.setFocused(state.focused)
}

private fun updateSliderWidget(
    widget: GuiSlider,
    bounds: Rect,
    prefix: String,
    suffix: String,
    enabled: Boolean,
    showDecimal: Boolean
) {
    updateButtonWidgetBounds(widget, bounds)
    widget.enabled = enabled
    widget.visible = true
    widget.dispString = prefix
    widget.suffix = suffix
    widget.showDecimal = showDecimal
}

private fun updateSliderValue(hosted: HostedSlider, coercedValue: Double) {
    if (abs(hosted.widget.getValue() - coercedValue) > 1e-9) {
        hosted.suppressCallback = true
        hosted.widget.setValue(coercedValue)
        hosted.widget.updateSlider()
        hosted.suppressCallback = false
    }
}

private fun updateButtonWidgetBounds(widget: GuiButtonExt, bounds: Rect, height: Int = bounds.height) {
    widget.xPosition = bounds.x
    widget.yPosition = bounds.y
    widget.width = bounds.width
    widget.height = height
}

private fun updateButtonWidgetBounds(widget: GuiCheckBox, bounds: Rect, height: Int = bounds.height) {
    widget.xPosition = bounds.x
    widget.yPosition = bounds.y
    widget.width = bounds.width
    widget.height = height
}

private fun updateTextFieldBounds(widget: GuiTextField, bounds: Rect) {
    widget.xPosition = bounds.x
    widget.yPosition = bounds.y
    widget.width = bounds.width
    widget.height = bounds.height
}

private fun sliderPrefix(label: String): String {
    return if (label.isBlank()) {
        ""
    } else {
        "$label: "
    }
}
