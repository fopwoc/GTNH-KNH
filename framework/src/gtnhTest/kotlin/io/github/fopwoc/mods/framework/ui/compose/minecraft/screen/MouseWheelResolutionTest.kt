package io.github.fopwoc.mods.framework.ui.compose.minecraft.screen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MouseWheelResolutionTest {
    @Test
    fun resolveMouseWheelEventReturnsNullWhenInputCannotBeScaled() {
        assertNull(
            resolveMouseWheelEvent(
                width = 100,
                height = 80,
                displayWidth = 200,
                displayHeight = 100,
                event = MouseWheelEvent(wheelDelta = 0, eventX = 40, eventY = 20),
            )
        )
        assertNull(
            resolveMouseWheelEvent(
                width = 100,
                height = 80,
                displayWidth = null,
                displayHeight = 100,
                event = MouseWheelEvent(wheelDelta = -120, eventX = 40, eventY = 20),
            )
        )
        assertNull(
            resolveMouseWheelEvent(
                width = 100,
                height = 80,
                displayWidth = 0,
                displayHeight = 100,
                event = MouseWheelEvent(wheelDelta = -120, eventX = 40, eventY = 20),
            )
        )
    }

    @Test
    fun resolveMouseWheelEventScalesDisplayCoordinatesIntoGuiSpace() {
        val resolved =
            resolveMouseWheelEvent(
                width = 100,
                height = 80,
                displayWidth = 200,
                displayHeight = 160,
                event = MouseWheelEvent(wheelDelta = -120, eventX = 50, eventY = 40),
            )

        assertEquals(ResolvedMouseWheelEvent(wheelDelta = -120, mouseX = 25, mouseY = 59), resolved)
    }
}
