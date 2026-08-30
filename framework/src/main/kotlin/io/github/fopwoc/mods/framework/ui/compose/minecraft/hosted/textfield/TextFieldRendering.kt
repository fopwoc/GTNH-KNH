package io.github.fopwoc.mods.framework.ui.compose.minecraft.hosted

import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputPressResult
import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputTarget
import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputTargetKind
import io.github.fopwoc.mods.framework.ui.compose.layout.core.Rect
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.element.HostedWidgetKey
import io.github.fopwoc.mods.framework.ui.compose.model.style.TextFieldStyle
import io.github.fopwoc.mods.framework.ui.compose.state.TextFieldState
import net.minecraft.client.gui.GuiTextField

internal fun drawMinecraftHostedTextField(
    registry: MinecraftHostedWidgetRegistry,
    environment: MinecraftHostedWidgetRenderEnvironment,
    bounds: Rect,
    hostKey: HostedWidgetKey,
    state: TextFieldState,
    placeholder: String,
    enabled: Boolean,
    style: TextFieldStyle,
) {
  val hosted =
      registry.getOrCreateTextField(hostKey) {
        val widget = GuiTextField(environment.font, bounds.x, bounds.y, bounds.width, bounds.height)
        widget.setCanLoseFocus(false)
        HostedTextField(hostKey, state, widget)
      }

  hosted.markSeen(environment.renderEpoch)
  hosted.currentState = state
  updateTextFieldWidget(
      widget = hosted.widget,
      bounds = bounds,
      state = state,
      enabled = enabled,
      style = style,
  )
  hosted.widget.drawTextBox()
  drawTextFieldPlaceholderIfNeeded(
      environment = environment,
      bounds = bounds,
      state = state,
      placeholder = placeholder,
      style = style,
  )

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
            },
        )
    )
  }
}

private fun drawTextFieldPlaceholderIfNeeded(
    environment: MinecraftHostedWidgetRenderEnvironment,
    bounds: Rect,
    state: TextFieldState,
    placeholder: String,
    style: TextFieldStyle,
) {
  if (state.text.isNotEmpty() || state.focused || placeholder.isEmpty()) {
    return
  }

  val placeholderX = bounds.x + if (style.drawBackground) 4 else 0
  val placeholderY =
      bounds.y + ((bounds.height - environment.font.FONT_HEIGHT) / 2).coerceAtLeast(0)
  environment.font.drawStringWithShadow(
      placeholder,
      placeholderX,
      placeholderY,
      Color.rgb(red = 0x80, green = 0x80, blue = 0x80).argbInt,
  )
}

internal fun updateTextFieldWidget(
    widget: GuiTextField,
    bounds: Rect,
    state: TextFieldState,
    enabled: Boolean,
    style: TextFieldStyle,
) {
  updateTextFieldBounds(widget, bounds)
  if (!enabled && state.focused) {
    state.clearFocus()
  }
  widget.setEnabled(enabled)
  widget.setMaxStringLength(style.maxLength)
  widget.setTextColor(style.textColor.argbInt)
  widget.setDisabledTextColour(style.disabledTextColor.argbInt)
  widget.setEnableBackgroundDrawing(style.drawBackground)
  if (widget.text != state.text) {
    widget.text = state.text
  }
  widget.setFocused(enabled && state.focused)
}

private fun updateTextFieldBounds(widget: GuiTextField, bounds: Rect) {
  widget.xPosition = bounds.x
  widget.yPosition = bounds.y
  widget.width = bounds.width
  widget.height = bounds.height
}
