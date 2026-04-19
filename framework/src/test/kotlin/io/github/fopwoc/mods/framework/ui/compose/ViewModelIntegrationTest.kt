package io.github.fopwoc.mods.framework.ui.compose

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.minecraft.ComposeScreenViewModelOwner
import io.github.fopwoc.mods.framework.ui.compose.runtime.ComposeViewModelOwner
import io.github.fopwoc.mods.framework.ui.compose.node.NodeApplier
import io.github.fopwoc.mods.framework.ui.compose.node.RootNode
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ViewModelIntegrationTest {
    @Test
    fun composeScreenViewModelOwnerClearsAndroidxViewModels() {
        val owner = ComposeScreenViewModelOwner()
        val viewModel = ViewModelProvider.create(
            owner,
            owner.defaultViewModelProviderFactory,
            owner.defaultViewModelCreationExtras
        ).get(TrackingViewModel::class)

        owner.clear()

        assertTrue(viewModel.cleared)
    }

    @Test
    fun androidxComposeViewModelReturnsSameInstanceAcrossRecomposition() = runBlocking {
        val root = RootNode()
        val owner = ComposeScreenViewModelOwner()
        val trigger = mutableIntStateOf(0)
        val frameClock = BroadcastFrameClock()
        val recomposerContext = Dispatchers.Unconfined + frameClock
        val recomposer = Recomposer(recomposerContext)
        val composition = Composition(NodeApplier(root), recomposer)
        val recomposeJob = launch(recomposerContext, start = CoroutineStart.UNDISPATCHED) {
            recomposer.runRecomposeAndApplyChanges()
        }
        var first: TrackingViewModel? = null
        var second: TrackingViewModel? = null

        try {
            owner.onCreate()
            composition.setContent {
                CompositionLocalProvider(
                    LocalLifecycleOwner provides owner,
                    LocalViewModelStoreOwner provides owner
                ) {
                    val vm: TrackingViewModel = viewModel(TrackingViewModel::class)
                    if (trigger.intValue == 0) {
                        first = vm
                    } else {
                        second = vm
                    }
                    Text(text = "tick ${trigger.intValue} / ${vm.token}")
                }
            }
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(0L)
            recomposer.awaitIdle()

            trigger.intValue = 1
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(16L)
            recomposer.awaitIdle()

            assertSame(first, second)
        } finally {
            composition.dispose()
            owner.clear()
            recomposer.cancel()
            recomposeJob.cancelAndJoin()
        }
    }

    @Test
    fun lifecycleRuntimeComposeUsesScreenLifecycleOwnerForCollection() = runBlocking {
        val root = RootNode()
        val owner = ComposeScreenViewModelOwner()
        val upstream = MutableStateFlow(0)
        val frameClock = BroadcastFrameClock()
        val recomposerContext = Dispatchers.Unconfined + frameClock
        val recomposer = Recomposer(recomposerContext)
        val composition = Composition(NodeApplier(root), recomposer)
        val recomposeJob = launch(recomposerContext, start = CoroutineStart.UNDISPATCHED) {
            recomposer.runRecomposeAndApplyChanges()
        }
        var currentLifecycleOwner: LifecycleOwner? = null
        var latestValue = -1

        try {
            owner.onCreate()
            composition.setContent {
                CompositionLocalProvider(
                    LocalLifecycleOwner provides owner,
                    LocalViewModelStoreOwner provides owner
                ) {
                    val lifecycleOwner = LocalLifecycleOwner.current
                    currentLifecycleOwner = lifecycleOwner
                    val value by upstream.collectAsStateWithLifecycle(
                        lifecycleOwner = lifecycleOwner,
                        minActiveState = androidx.lifecycle.Lifecycle.State.STARTED
                    )
                    latestValue = value
                    Text(text = "value=$value")
                }
            }
            settle(frameClock, recomposer, 0L)

            assertSame(owner, currentLifecycleOwner)
            assertEquals(0, latestValue)

            upstream.value = 1
            settle(frameClock, recomposer, 16L)
            assertEquals(0, latestValue)

            owner.onStart()
            settle(frameClock, recomposer, 32L)
            assertEquals(1, latestValue)
        } finally {
            composition.dispose()
            owner.clear()
            recomposer.cancel()
            recomposeJob.cancelAndJoin()
        }
    }

    @Test
    fun freshOwnerCreatesFreshAndroidxViewModelInstance() {
        val firstOwner = ComposeViewModelOwner()
        firstOwner.onCreate()
        val firstViewModel = ViewModelProvider.create(
            firstOwner,
            firstOwner.defaultViewModelProviderFactory,
            firstOwner.defaultViewModelCreationExtras
        ).get("plain", TrackingViewModel::class)
        firstOwner.clear()

        val secondOwner = ComposeViewModelOwner()
        secondOwner.onCreate()
        val secondViewModel = ViewModelProvider.create(
            secondOwner,
            secondOwner.defaultViewModelProviderFactory,
            secondOwner.defaultViewModelCreationExtras
        ).get("plain", TrackingViewModel::class)

        try {
            assertTrue(firstViewModel.cleared)
            assertTrue(firstViewModel !== secondViewModel)
        } finally {
            secondOwner.clear()
        }
    }

    private suspend fun settle(
        frameClock: BroadcastFrameClock,
        recomposer: Recomposer,
        frameTimeNanos: Long
    ) {
        Snapshot.sendApplyNotifications()
        frameClock.sendFrame(frameTimeNanos)
        recomposer.awaitIdle()
    }

    class TrackingViewModel : ViewModel() {
        val token: Int = nextToken++
        var cleared: Boolean = false
            private set

        override fun onCleared() {
            cleared = true
        }

        private companion object {
            var nextToken: Int = 1
        }
    }

}

