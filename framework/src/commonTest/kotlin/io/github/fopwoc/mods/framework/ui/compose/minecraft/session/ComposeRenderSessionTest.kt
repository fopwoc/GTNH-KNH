package io.github.fopwoc.mods.framework.ui.compose.minecraft.session

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputTarget
import io.github.fopwoc.mods.framework.ui.compose.layout.render.RenderContext
import io.github.fopwoc.mods.framework.ui.compose.layout.render.TextFieldHost
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import io.github.fopwoc.mods.framework.ui.compose.runtime.ComposeViewModelOwner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ComposeRenderSessionTest {
    @Test
    fun hasCompositionTracksCreateAndDisposeLifecycle() {
        val session = TestRenderSession()

        try {
            assertFalse(session.hasComposition)

            session.createComposition()
            assertTrue(session.hasComposition)

            session.dispose()
            assertFalse(session.hasComposition)
        } finally {
            session.dispose()
        }
    }

    @Test
    fun advanceFrameDeliversComposeFrameTime() {
        var observedFrameTimeNanos: Long? = null
        val session = TestRenderSession {
            FrameAwaitingComposable {
                observedFrameTimeNanos = it
            }
        }

        try {
            session.sendFrame(42L)

            assertEquals(42L, observedFrameTimeNanos)
        } finally {
            session.dispose()
        }
    }

    @Composable
    private fun FrameAwaitingComposable(onFrame: (Long) -> Unit) {
        LaunchedEffect(Unit) {
            onFrame(withFrameNanos { it })
        }
    }

    private class TestRenderSession(content: @Composable () -> Unit = {}) :
        ComposeRenderSession(NoSurface, content) {
        fun createComposition() {
            ensureCompositionCreated()
        }

        fun sendFrame(frameTimeNanos: Long) {
            advanceFrame(frameTimeNanos)
        }

        @Composable
        override fun ProvideCompositionLocals(
            owner: ComposeViewModelOwner,
            content: @Composable () -> Unit,
        ) {
            content()
        }
    }
}

private object NoSurface : RenderSurface {
    override fun beginFrame(
        width: Int,
        height: Int,
        mouseX: Int,
        mouseY: Int,
        inputTargets: MutableList<InputTarget>,
        textFields: TextFieldHost,
    ): RenderContext = error("Rendering is not part of these tests")

    override fun endFrame() = Unit

    override fun dispose() = Unit
}
