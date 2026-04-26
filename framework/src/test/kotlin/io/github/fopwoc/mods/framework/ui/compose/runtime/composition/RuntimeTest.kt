package io.github.fopwoc.mods.framework.ui.compose.runtime

import io.github.fopwoc.mods.framework.ui.compose.node.RootNode
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ComposeGuiRuntimeTest {
    @AfterTest
    fun tearDown() {
        ComposeMainDispatcherBridge.resetForTests()
    }

    @Test
    fun pumpFailsFastWhenComposeTasksNeverReachIdle() {
        val runtime = ComposeGuiRuntime(
            onCompositionChanged = {},
            maxPumpCycles = 8,
            maxComposeTaskExecutionsPerPump = 8
        )
        runtime.start(RootNode()) {}

        try {
            lateinit var selfReplicatingTask: Runnable
            selfReplicatingTask = object : Runnable {
                override fun run() {
                    thread(start = true, name = "compose-runtime-pump-requeue") {
                        ComposeMainDispatcher.dispatch(EmptyCoroutineContext, selfReplicatingTask)
                    }.join()
                }
            }
            thread(start = true, name = "compose-runtime-pump-seed") {
                ComposeMainDispatcher.dispatch(EmptyCoroutineContext, selfReplicatingTask)
            }.join()

            val error = assertFailsWith<IllegalStateException> {
                runtime.pump()
            }

            assertContains(error.message.orEmpty(), "compose task executions")
        } finally {
            runtime.dispose()
        }
    }

    @Test
    fun startFailureDisposesPartiallyCreatedRuntimeState() {
        val runtime = ComposeGuiRuntime(onCompositionChanged = {})

        assertFailsWith<IllegalStateException> {
            runtime.start(RootNode()) {
                error("boom")
            }
        }

        assertFalse(runtime.isStarted())

        runtime.start(RootNode()) {}
        runtime.dispose()

        val reboundFailure = AtomicReference<Throwable?>(null)
        val worker = thread(start = true, name = "compose-runtime-rebind") {
            try {
                ComposeMainDispatcherBridge.installForCurrentThread()
                assertFalse(ComposeMainDispatcher.isDispatchNeeded(EmptyCoroutineContext))
            } catch (throwable: Throwable) {
                reboundFailure.set(throwable)
            } finally {
                ComposeMainDispatcherBridge.releaseForCurrentThread()
            }
        }
        worker.join()

        assertNull(reboundFailure.get())
        assertTrue(worker.state == Thread.State.TERMINATED)
    }
}
