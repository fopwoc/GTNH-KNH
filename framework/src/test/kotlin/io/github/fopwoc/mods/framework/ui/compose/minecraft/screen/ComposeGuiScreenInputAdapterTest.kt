package io.github.fopwoc.mods.framework.ui.compose.minecraft.screen

import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputTarget
import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputTargetKind
import io.github.fopwoc.mods.framework.ui.compose.layout.core.Rect
import io.github.fopwoc.mods.framework.ui.compose.minecraft.hosted.MinecraftHostedWidgetRegistry
import io.github.fopwoc.mods.framework.ui.compose.minecraft.session.ComposeRenderRuntimeSync
import io.github.fopwoc.mods.framework.ui.compose.runtime.BackCallback
import io.github.fopwoc.mods.framework.ui.compose.runtime.ComposeBackDispatcher
import io.github.fopwoc.mods.framework.ui.compose.runtime.ComposeGuiRuntime
import org.lwjgl.input.Keyboard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ComposeGuiScreenInputAdapterTest {
    @Test
    fun keyTypedUsesBackDispatcherForEscapeBeforeFallback() {
        val runtime = ComposeGuiRuntime(onCompositionChanged = {})
        val backDispatcher = ComposeBackDispatcher()
        val inputAdapter = ComposeGuiScreenInputAdapter(
            backDispatcher = backDispatcher,
            interactionState = ComposeGuiScreenInteractionState(MinecraftHostedWidgetRegistry()),
            renderedInputTargets = emptyList(),
            runtimeSync = ComposeRenderRuntimeSync(runtime)
        )
        var backCount = 0
        var fallbackCalled = false
        val registration = backDispatcher.register(
            BackCallback(
                enabled = true,
                onBack = {
                    backCount += 1
                    true
                }
            )
        )

        try {
            inputAdapter.keyTyped('\u0000', Keyboard.KEY_ESCAPE) {
                fallbackCalled = true
            }

            assertEquals(1, backCount)
            assertFalse(fallbackCalled)
        } finally {
            registration.dispose()
        }
    }

    @Test
    fun resolveMouseWheelEventReturnsNullWhenInputCannotBeScaled() {
        assertNull(
            resolveMouseWheelEvent(
                width = 100,
                height = 80,
                displayWidth = 200,
                displayHeight = 100,
                event = MouseWheelEvent(wheelDelta = 0, eventX = 40, eventY = 20)
            )
        )
        assertNull(
            resolveMouseWheelEvent(
                width = 100,
                height = 80,
                displayWidth = null,
                displayHeight = 100,
                event = MouseWheelEvent(wheelDelta = -120, eventX = 40, eventY = 20)
            )
        )
        assertNull(
            resolveMouseWheelEvent(
                width = 100,
                height = 80,
                displayWidth = 0,
                displayHeight = 100,
                event = MouseWheelEvent(wheelDelta = -120, eventX = 40, eventY = 20)
            )
        )
    }

    @Test
    fun resolveMouseWheelEventScalesDisplayCoordinatesIntoGuiSpace() {
        val resolved = resolveMouseWheelEvent(
            width = 100,
            height = 80,
            displayWidth = 200,
            displayHeight = 160,
            event = MouseWheelEvent(wheelDelta = -120, eventX = 50, eventY = 40)
        )

        assertEquals(ResolvedMouseWheelEvent(wheelDelta = -120, mouseX = 25, mouseY = 59), resolved)
    }

    @Test
    fun handleMouseInputRunsBaseFirstAndDispatchesToTopmostWheelTarget() {
        val runtime = ComposeGuiRuntime(onCompositionChanged = {})
        val events = mutableListOf<String>()
        val inputAdapter = ComposeGuiScreenInputAdapter(
            backDispatcher = ComposeBackDispatcher(),
            interactionState = ComposeGuiScreenInteractionState(MinecraftHostedWidgetRegistry()),
            renderedInputTargets = listOf(
                InputTarget(
                    kind = InputTargetKind.SCROLL_WHEEL,
                    bounds = Rect(0, 0, 80, 80),
                    onWheel = { _, _, _ ->
                        events += "bottom"
                        true
                    }
                ),
                InputTarget(
                    kind = InputTargetKind.SELECTABLE_LIST,
                    bounds = Rect(0, 0, 80, 80),
                    onWheel = { mouseX, mouseY, wheelDelta ->
                        events += "top:$mouseX,$mouseY,$wheelDelta:${events.firstOrNull()}"
                        true
                    }
                )
            ),
            runtimeSync = ComposeRenderRuntimeSync(runtime),
            mouseEventReader = object : MouseEventReader {
                override fun readWheelEvent(): MouseWheelEvent {
                    return MouseWheelEvent(wheelDelta = -120, eventX = 50, eventY = 40)
                }
            }
        )

        inputAdapter.handleMouseInput(
            width = 100,
            height = 80,
            displayWidth = 200,
            displayHeight = 160
        ) {
            events += "base"
        }

        assertEquals(listOf("base", "top:25,59,-120:base"), events)
    }

    @Test
    fun handleMouseInputIgnoresWheelDispatchWhenResolvedPointHasNoTarget() {
        val runtime = ComposeGuiRuntime(onCompositionChanged = {})
        var baseCalled = false
        var dispatched = false
        val inputAdapter = ComposeGuiScreenInputAdapter(
            backDispatcher = ComposeBackDispatcher(),
            interactionState = ComposeGuiScreenInteractionState(MinecraftHostedWidgetRegistry()),
            renderedInputTargets = listOf(
                InputTarget(
                    kind = InputTargetKind.SCROLL_WHEEL,
                    bounds = Rect(70, 70, 10, 10),
                    onWheel = { _, _, _ ->
                        dispatched = true
                        true
                    }
                )
            ),
            runtimeSync = ComposeRenderRuntimeSync(runtime),
            mouseEventReader = object : MouseEventReader {
                override fun readWheelEvent(): MouseWheelEvent {
                    return MouseWheelEvent(wheelDelta = -120, eventX = 10, eventY = 10)
                }
            }
        )

        inputAdapter.handleMouseInput(
            width = 100,
            height = 80,
            displayWidth = 100,
            displayHeight = 80
        ) {
            baseCalled = true
        }

        assertTrue(baseCalled)
        assertFalse(dispatched)
    }
}

