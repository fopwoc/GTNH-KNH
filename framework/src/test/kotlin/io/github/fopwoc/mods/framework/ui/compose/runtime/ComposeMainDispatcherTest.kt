@file:OptIn(kotlinx.coroutines.InternalCoroutinesApi::class)

package io.github.fopwoc.mods.framework.ui.compose.runtime

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.internal.MainDispatcherFactory
import java.util.ServiceLoader
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ComposeMainDispatcherTest {
    @AfterTest
    fun tearDown() {
        ComposeMainDispatcherBridge.resetForTests()
    }

    @Test
    fun serviceLoaderPublishesComposeMainDispatcherFactory() {
        val factories = ServiceLoader.load(
            MainDispatcherFactory::class.java,
            ComposeMainDispatcherFactory::class.java.classLoader
        ).toList()

        assertTrue(factories.any { it is ComposeMainDispatcherFactory })
    }

    @Test
    fun dispatchersMainUsesInstalledComposeThreadAndImmediateVariant() {
        ComposeMainDispatcherBridge.installForCurrentThread()
        val installedThread = Thread.currentThread()
        val dispatchedThread = AtomicReference<Thread?>(null)
        val dispatchNeededFromWorker = AtomicReference<Boolean?>(null)

        val worker = thread(start = true, name = "compose-main-dispatcher-test") {
            dispatchNeededFromWorker.set(Dispatchers.Main.isDispatchNeeded(EmptyCoroutineContext))
            Dispatchers.Main.dispatch(EmptyCoroutineContext) {
                dispatchedThread.set(Thread.currentThread())
            }
        }
        worker.join()

        val drainedAny = ComposeMainDispatcherBridge.pump()

        assertFalse(Dispatchers.Main.isDispatchNeeded(EmptyCoroutineContext))
        assertSame(Dispatchers.Main, Dispatchers.Main.immediate)
        assertSame(ComposeMainDispatcher, Dispatchers.Main.immediate)
        assertEquals(true, dispatchNeededFromWorker.get())
        assertTrue(drainedAny)
        assertSame(installedThread, dispatchedThread.get())
    }

    @Test
    fun releasingInstalledThreadAllowsRebindingMainDispatcher() {
        ComposeMainDispatcherBridge.installForCurrentThread()
        ComposeMainDispatcherBridge.releaseForCurrentThread()

        val workerReady = CountDownLatch(1)
        val workerPumpSignal = CountDownLatch(1)
        val dispatchedThread = AtomicReference<Thread?>(null)

        val worker = thread(start = true, name = "compose-main-dispatcher-rebind") {
            ComposeMainDispatcherBridge.installForCurrentThread()
            workerReady.countDown()
            workerPumpSignal.await()
            ComposeMainDispatcherBridge.pump()
            ComposeMainDispatcherBridge.releaseForCurrentThread()
        }

        workerReady.await()
        assertTrue(Dispatchers.Main.isDispatchNeeded(EmptyCoroutineContext))
        Dispatchers.Main.dispatch(EmptyCoroutineContext) {
            dispatchedThread.set(Thread.currentThread())
        }
        workerPumpSignal.countDown()
        worker.join()

        assertEquals("compose-main-dispatcher-rebind", dispatchedThread.get()?.name)
    }
}

