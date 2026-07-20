package io.github.fopwoc.mods.framework.ui.compose.runtime

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.snapshots.ObserverHandle
import androidx.compose.runtime.snapshots.Snapshot
import io.github.fopwoc.mods.framework.ui.compose.minecraft.session.FrameworkRuntimeDebug
import io.github.fopwoc.mods.framework.ui.compose.node.NodeApplier
import io.github.fopwoc.mods.framework.ui.compose.node.RootNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

internal class ComposeGuiRuntime(
    private val onCompositionChanged: () -> Unit,
    private val maxPumpCycles: Int = DEFAULT_MAX_PUMP_CYCLES,
    private val maxComposeTaskExecutionsPerPump: Int = DEFAULT_MAX_COMPOSE_TASK_EXECUTIONS_PER_PUMP
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
        FrameworkRuntimeDebug.updateDispatcherStatus(
            "startThread=${Thread.currentThread().name} bound=${ComposeMainDispatcherBridge.boundThreadName()}"
        )
        try {
            val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
                FrameworkRuntimeDebug.updateRuntimeFailure(
                    "${throwable::class.simpleName}:${throwable.message ?: "no-message"}"
                )
            }
            val scope = CoroutineScope(SupervisorJob() + ComposeMainDispatcher + frameClock + exceptionHandler)
            val recomposer = Recomposer(scope.coroutineContext)
            val composition = Composition(NodeApplier(rootNode), recomposer)
            val recomposeJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                recomposer.runRecomposeAndApplyChanges()
            }
            recomposeJob.invokeOnCompletion { throwable ->
                FrameworkRuntimeDebug.updateRuntimeFailure(
                    throwable?.let {
                        "recomposeJob:${it::class.simpleName}:${it.message ?: "no-message"}"
                    } ?: "none"
                )
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
        FrameworkRuntimeDebug.updateDispatcherStatus(
            "sendFrameThread=${Thread.currentThread().name} bound=${ComposeMainDispatcherBridge.boundThreadName()}"
        )
        frameClock.sendFrame(frameTimeNanos)
    }

    fun pump() {
        FrameworkRuntimeDebug.updateRuntimeStatus(
            "beforePump thread=${Thread.currentThread().name} active=${recomposeJob?.isActive == true} cancelled=${recomposeJob?.isCancelled == true} completed=${recomposeJob?.isCompleted == true} pending=$snapshotNotificationsPending"
        )
        FrameworkRuntimeDebug.updateDispatcherStatus(
            "pumpThread=${Thread.currentThread().name} bound=${ComposeMainDispatcherBridge.boundThreadName()} dispatchNeeded=${ComposeMainDispatcherBridge.isDispatchNeeded()}"
        )
        var pumpCycles = 0
        var drainedMainDispatcherTasks: Boolean
        var flushedSnapshotNotifications: Boolean
        do {
            pumpCycles += 1
            check(pumpCycles <= maxPumpCycles) {
                "ComposeGuiRuntime.pump() exceeded $maxPumpCycles cycles without reaching an idle state"
            }
            drainedMainDispatcherTasks = ComposeMainDispatcherBridge.pump(maxComposeTaskExecutionsPerPump) {
                "ComposeGuiRuntime.pump() exceeded $maxComposeTaskExecutionsPerPump compose task executions without reaching an idle state"
            }
            flushedSnapshotNotifications = flushSnapshotNotifications()
        } while (snapshotNotificationsPending || drainedMainDispatcherTasks || flushedSnapshotNotifications)
        FrameworkRuntimeDebug.updateRuntimeStatus(
            "afterPump thread=${Thread.currentThread().name} active=${recomposeJob?.isActive == true} cancelled=${recomposeJob?.isCancelled == true} completed=${recomposeJob?.isCompleted == true} pending=$snapshotNotificationsPending"
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
        ComposeMainDispatcherBridge.releaseForCurrentThread()
        FrameworkRuntimeDebug.updateRuntimeStatus("disposed")
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
        private const val DEFAULT_MAX_PUMP_CYCLES = 1_024
        private const val DEFAULT_MAX_COMPOSE_TASK_EXECUTIONS_PER_PUMP = 16_384
    }

}
