package io.github.fopwoc.mods.framework.ui.compose.runtime

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.snapshots.ObserverHandle
import androidx.compose.runtime.snapshots.Snapshot
import io.github.fopwoc.mods.framework.ui.compose.node.NodeApplier
import io.github.fopwoc.mods.framework.ui.compose.node.RootNode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.apache.logging.log4j.LogManager

internal class ComposeGuiRuntime(
    private val onCompositionChanged: () -> Unit,
    private val maxPumpCycles: Int = DEFAULT_MAX_PUMP_CYCLES,
    private val maxComposeTaskExecutionsPerPump: Int = DEFAULT_MAX_COMPOSE_TASK_EXECUTIONS_PER_PUMP,
) {
  init {
    require(maxPumpCycles > 0) { "maxPumpCycles must be greater than zero" }
    require(maxComposeTaskExecutionsPerPump > 0) {
      "maxComposeTaskExecutionsPerPump must be greater than zero"
    }
  }

  private val frameClock = BroadcastFrameClock()
  private var compositionScope: CoroutineScope? = null
  private var recomposer: Recomposer? = null
  private var recomposeJob: Job? = null
  private var composition: Composition? = null
  private var snapshotApplyObserverHandle: ObserverHandle? = null
  private var snapshotWriteObserverHandle: ObserverHandle? = null
  private var snapshotNotificationsPending: Boolean = true
  private var pendingFailure: Throwable? = null

  val hasPendingNotifications: Boolean
    get() = snapshotNotificationsPending

  fun isStarted(): Boolean {
    return composition != null
  }

  fun start(rootNode: RootNode, content: @Composable () -> Unit) {
    if (composition != null) {
      return
    }

    ComposeMainDispatcherBridge.installForCurrentThread()
    try {
      val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        recordFailure("Unhandled exception in a Compose coroutine", throwable)
      }
      val scope =
          CoroutineScope(SupervisorJob() + ComposeMainDispatcher + frameClock + exceptionHandler)
      val recomposer = Recomposer(scope.coroutineContext)
      val composition = Composition(NodeApplier(rootNode), recomposer)
      val recomposeJob =
          scope.launch(start = CoroutineStart.UNDISPATCHED) {
            recomposer.runRecomposeAndApplyChanges()
          }
      recomposeJob.invokeOnCompletion { throwable ->
        if (throwable != null && throwable !is CancellationException) {
          recordFailure("Recomposition loop terminated", throwable)
        }
      }

      compositionScope = scope
      this.recomposer = recomposer
      this.recomposeJob = recomposeJob
      this.composition = composition

      snapshotNotificationsPending = true
      snapshotWriteObserverHandle = Snapshot.registerGlobalWriteObserver {
        snapshotNotificationsPending = true
      }
      snapshotApplyObserverHandle = Snapshot.registerApplyObserver { _, _ ->
        onCompositionChanged()
      }

      composition.setContent(content)
      pump()
    } catch (throwable: Throwable) {
      dispose()
      throw throwable
    }
  }

  fun sendFrame(frameTimeNanos: Long) {
    frameClock.sendFrame(frameTimeNanos)
  }

  /**
   * Rethrows a failure recorded from the recomposition loop or a runtime coroutine. Failures are
   * logged when they happen, but a dead recomposer would otherwise leave the UI silently frozen, so
   * hosts call this from their render path to turn the failure into a regular crash.
   */
  fun rethrowPendingFailure() {
    val failure = pendingFailure ?: return
    pendingFailure = null
    throw IllegalStateException("Compose runtime failed; see the logged cause", failure)
  }

  fun pump() {
    var pumpCycles = 0
    var drainedMainDispatcherTasks: Boolean
    var flushedSnapshotNotifications: Boolean
    do {
      pumpCycles += 1
      check(pumpCycles <= maxPumpCycles) {
        "ComposeGuiRuntime.pump() exceeded $maxPumpCycles cycles without reaching an idle state"
      }
      drainedMainDispatcherTasks =
          ComposeMainDispatcherBridge.pump(maxComposeTaskExecutionsPerPump) {
            "ComposeGuiRuntime.pump() exceeded $maxComposeTaskExecutionsPerPump compose task executions without reaching an idle state"
          }
      flushedSnapshotNotifications = flushSnapshotNotifications()
    } while (
        snapshotNotificationsPending || drainedMainDispatcherTasks || flushedSnapshotNotifications
    )
  }

  fun dispose() {
    composition?.dispose()
    composition = null

    recomposer?.cancel()
    recomposer = null

    recomposeJob?.cancel()
    recomposeJob = null

    compositionScope?.cancel()
    compositionScope = null

    snapshotApplyObserverHandle?.dispose()
    snapshotApplyObserverHandle = null
    snapshotWriteObserverHandle?.dispose()
    snapshotWriteObserverHandle = null

    snapshotNotificationsPending = true
    pendingFailure = null
    ComposeMainDispatcherBridge.releaseForCurrentThread()
  }

  private fun recordFailure(message: String, throwable: Throwable) {
    logger.error(message, throwable)
    if (pendingFailure == null) {
      pendingFailure = throwable
    }
  }

  private fun flushSnapshotNotifications(): Boolean {
    if (!snapshotNotificationsPending) {
      return false
    }

    snapshotNotificationsPending = false
    Snapshot.sendApplyNotifications()
    return true
  }

  private companion object {
    private val logger = LogManager.getLogger(ComposeGuiRuntime::class.java)
    private const val DEFAULT_MAX_PUMP_CYCLES = 1_024
    private const val DEFAULT_MAX_COMPOSE_TASK_EXECUTIONS_PER_PUMP = 16_384
  }
}
