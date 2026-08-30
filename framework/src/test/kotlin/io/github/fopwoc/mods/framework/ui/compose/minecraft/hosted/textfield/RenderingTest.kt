package io.github.fopwoc.mods.framework.ui.compose.minecraft.hosted

import io.github.fopwoc.mods.framework.ui.compose.layout.core.Rect
import io.github.fopwoc.mods.framework.ui.compose.model.style.TextFieldStyle
import io.github.fopwoc.mods.framework.ui.compose.state.TextFieldState
import kotlin.test.Test
import kotlin.test.assertFalse
import net.minecraft.client.gui.GuiTextField

class MinecraftHostedTextFieldRenderingTest {
  @Test
  fun disablingFocusedTextFieldClearsFocusAndBlursWidget() {
    val state = TextFieldState("value")
    state.requestFocus()
    val widget = GuiTextField(null, 0, 0, 120, 20)
    widget.setFocused(true)

    updateTextFieldWidget(
        widget = widget,
        bounds = Rect(0, 0, 120, 20),
        state = state,
        enabled = false,
        style = TextFieldStyle(),
    )

    assertFalse(state.focused)
    assertFalse(widget.isFocused)
  }
}
