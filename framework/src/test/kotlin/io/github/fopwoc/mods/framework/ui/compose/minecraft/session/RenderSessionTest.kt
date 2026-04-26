package io.github.fopwoc.mods.framework.ui.compose.minecraft

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import io.github.fopwoc.mods.framework.ui.compose.minecraft.session.ComposeRenderSession
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

    private class TestRenderSession(
        content: @Composable () -> Unit = {}
    ) : ComposeRenderSession(content = content) {
        fun createComposition() {
            ensureCompositionCreated()
        }

        fun sendFrame(frameTimeNanos: Long) {
            advanceFrame(frameTimeNanos)
        }

        @Composable
        override fun ProvideCompositionLocals(
            owner: ComposeViewModelOwner,
            content: @Composable () -> Unit
        ) {
            content()
        }
    }
}

