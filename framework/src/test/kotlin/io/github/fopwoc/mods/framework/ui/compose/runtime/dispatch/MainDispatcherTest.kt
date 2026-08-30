package io.github.fopwoc.mods.framework.ui.compose.runtime

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ComposeMainDispatcherTest {
  @AfterTest
  fun tearDown() {
    ComposeMainDispatcherBridge.resetForTests()
  }

  @Test
  fun resourcesDoNotPublishComposeMainDispatcherFactoryByDefault() {
    val resources =
        ComposeMainDispatcher::class
            .java
            .classLoader
            .getResources(MAIN_DISPATCHER_FACTORY_RESOURCE_PATH)
    val resourceContents = buildList {
      while (resources.hasMoreElements()) {
        val resource = resources.nextElement()
        add(resource.openStream().bufferedReader().use { it.readText() })
      }
    }

    assertTrue(resourceContents.none { it.contains(COMPOSE_MAIN_DISPATCHER_FACTORY_CLASS_NAME) })
  }

  @Test
  fun composeMainDispatcherUsesInstalledComposeThreadAndImmediateVariant() {
    ComposeMainDispatcherBridge.installForCurrentThread()
    val installedThread = Thread.currentThread()
    val dispatchedThread = AtomicReference<Thread?>(null)
    val dispatchNeededFromWorker = AtomicReference<Boolean?>(null)

    val worker =
        thread(start = true, name = "compose-main-dispatcher-test") {
          dispatchNeededFromWorker.set(
              ComposeMainDispatcher.isDispatchNeeded(EmptyCoroutineContext)
          )
          ComposeMainDispatcher.dispatch(EmptyCoroutineContext) {
            dispatchedThread.set(Thread.currentThread())
          }
        }
    worker.join()

    val drainedAny = ComposeMainDispatcherBridge.pump()

    assertFalse(ComposeMainDispatcher.isDispatchNeeded(EmptyCoroutineContext))
    assertSame(ComposeMainDispatcher, ComposeMainDispatcher.immediate)
    assertEquals(true, dispatchNeededFromWorker.get())
    assertTrue(drainedAny)
    assertSame(installedThread, dispatchedThread.get())
  }

  @Test
  fun releasingInstalledThreadAllowsRebindingComposeMainDispatcher() {
    ComposeMainDispatcherBridge.installForCurrentThread()
    ComposeMainDispatcherBridge.releaseForCurrentThread()

    val workerReady = CountDownLatch(1)
    val workerPumpSignal = CountDownLatch(1)
    val dispatchedThread = AtomicReference<Thread?>(null)

    val worker =
        thread(start = true, name = "compose-main-dispatcher-rebind") {
          ComposeMainDispatcherBridge.installForCurrentThread()
          workerReady.countDown()
          workerPumpSignal.await()
          ComposeMainDispatcherBridge.pump()
          ComposeMainDispatcherBridge.releaseForCurrentThread()
        }

    workerReady.await()
    assertTrue(ComposeMainDispatcher.isDispatchNeeded(EmptyCoroutineContext))
    ComposeMainDispatcher.dispatch(EmptyCoroutineContext) {
      dispatchedThread.set(Thread.currentThread())
    }
    workerPumpSignal.countDown()
    worker.join()

    assertEquals("compose-main-dispatcher-rebind", dispatchedThread.get()?.name)
  }

  @Test
  fun boundedPumpFailsFastWhenUnifiedQueueNeverReachesIdle() {
    ComposeMainDispatcherBridge.installForCurrentThread()
    lateinit var selfReplicatingTask: Runnable
    selfReplicatingTask =
        object : Runnable {
          override fun run() {
            thread(start = true, name = "compose-main-dispatcher-requeue") {
                  ComposeMainDispatcher.dispatch(EmptyCoroutineContext, selfReplicatingTask)
                }
                .join()
          }
        }
    thread(start = true, name = "compose-main-dispatcher-seed") {
          ComposeMainDispatcher.dispatch(EmptyCoroutineContext, selfReplicatingTask)
        }
        .join()

    val error =
        assertFailsWith<IllegalStateException> {
          ComposeMainDispatcherBridge.pump(maxTaskExecutions = 8) {
            "bounded pump exceeded limit"
          }
        }

    assertContains(error.message.orEmpty(), "bounded pump exceeded limit")
  }

  private companion object {
    private const val MAIN_DISPATCHER_FACTORY_RESOURCE_PATH =
        "META-INF/services/kotlinx.coroutines.internal.MainDispatcherFactory"
    private const val COMPOSE_MAIN_DISPATCHER_FACTORY_CLASS_NAME =
        "io.github.fopwoc.mods.framework.ui.compose.runtime.ComposeMainDispatcherFactory"
  }
}
