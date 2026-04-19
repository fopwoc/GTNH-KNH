package io.github.fopwoc.mods.framework.ui.compose.runtime

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.snapshots.ObserverHandle
import androidx.compose.runtime.snapshots.Snapshot
import io.github.fopwoc.mods.framework.ui.compose.node.NodeApplier
import io.github.fopwoc.mods.framework.ui.compose.node.RootNode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.ArrayDeque
import kotlin.coroutines.CoroutineContext

internal class ComposeGuiRuntime(
    private val onCompositionChanged: () -> Unit
) {
    private val frameClock = BroadcastFrameClock()
    private var compositionScope: CoroutineScope? = null
    private var recomposer: Recomposer? = null
    private var recomposeJob: Job? = null
    private var composition: Composition? = null
    private var snapshotApplyObserverHandle: ObserverHandle? = null
    private var snapshotWriteObserverHandle: ObserverHandle? = null
    private var snapshotNotificationsPending: Boolean = true
    private var composeUiThread: Thread? = null
    private val pendingComposeTasks = ArrayDeque<Runnable>()
    private val pendingComposeTasksLock = Any()
    private val composeUiDispatcher = object : CoroutineDispatcher() {
        override fun isDispatchNeeded(context: CoroutineContext): Boolean {
            return Thread.currentThread() !== composeUiThread
        }

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            if (Thread.currentThread() === composeUiThread) {
                block.run()
                return
            }

            synchronized(pendingComposeTasksLock) {
                pendingComposeTasks.addLast(block)
            }
        }
    }

    val hasPendingNotifications: Boolean
        get() = snapshotNotificationsPending

    fun isStarted(): Boolean {
        return composition != null
    }

    fun start(rootNode: RootNode, content: @Composable () -> Unit) {
        if (composition != null) {
            return
        }

        composeUiThread = Thread.currentThread()
        ComposeMainDispatcherBridge.installForCurrentThread()
        val scope = CoroutineScope(SupervisorJob() + composeUiDispatcher + frameClock)
        val recomposer = Recomposer(scope.coroutineContext)
        val composition = Composition(NodeApplier(rootNode), recomposer)
        val recomposeJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            recomposer.runRecomposeAndApplyChanges()
        }

        snapshotNotificationsPending = true
        snapshotWriteObserverHandle = Snapshot.registerGlobalWriteObserver {
            snapshotNotificationsPending = true
        }
        snapshotApplyObserverHandle = Snapshot.registerApplyObserver { _, _ ->
            onCompositionChanged()
        }

        composition.setContent(content)
        pump()

        compositionScope = scope
        this.recomposer = recomposer
        this.recomposeJob = recomposeJob
        this.composition = composition
    }

    fun sendFrame(frameTimeNanos: Long) {
        frameClock.sendFrame(frameTimeNanos)
    }

    fun pump() {
        var drainedMainDispatcherTasks: Boolean
        do {
            drainedMainDispatcherTasks = ComposeMainDispatcherBridge.pump()
            flushSnapshotNotifications()
        } while (drainComposeTasks() || snapshotNotificationsPending || drainedMainDispatcherTasks)
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
        composeUiThread = null
        synchronized(pendingComposeTasksLock) {
            pendingComposeTasks.clear()
        }
    }

    private fun flushSnapshotNotifications() {
        if (!snapshotNotificationsPending) {
            return
        }

        snapshotNotificationsPending = false
        Snapshot.sendApplyNotifications()
    }

    private fun drainComposeTasks(): Boolean {
        if (Thread.currentThread() !== composeUiThread) {
            return false
        }

        var drainedAny = false
        while (true) {
            val nextTask = synchronized(pendingComposeTasksLock) {
                if (pendingComposeTasks.isEmpty()) {
                    null
                } else {
                    pendingComposeTasks.removeFirst()
                }
            } ?: return drainedAny
            drainedAny = true
            nextTask.run()
        }
    }

}
