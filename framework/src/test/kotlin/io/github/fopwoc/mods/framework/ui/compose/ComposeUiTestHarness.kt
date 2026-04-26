package io.github.fopwoc.mods.framework.ui.compose

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.snapshots.Snapshot
import io.github.fopwoc.mods.framework.ui.compose.node.NodeApplier
import io.github.fopwoc.mods.framework.ui.compose.node.RootNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch

internal class ComposeUiTestHarness {
    val root = RootNode()

    private val frameClock = BroadcastFrameClock()
    private val recomposerContext = Dispatchers.Unconfined + frameClock
    private val scope = CoroutineScope(SupervisorJob() + recomposerContext)
    private val recomposer = Recomposer(recomposerContext)
    private val composition = Composition(NodeApplier(root), recomposer)
    private val recomposeJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
        recomposer.runRecomposeAndApplyChanges()
    }

    fun setContent(content: @Composable () -> Unit) {
        composition.setContent(content)
    }

    suspend fun settle(frameTimeNanos: Long) {
        Snapshot.sendApplyNotifications()
        frameClock.sendFrame(frameTimeNanos)
        recomposer.awaitIdle()
    }

    suspend fun dispose() {
        composition.dispose()
        recomposer.cancel()
        recomposeJob.cancelAndJoin()
        scope.cancel()
    }
}

