package io.github.fopwoc.mods.framework.ui.compose

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.minecraft.ComposeGuiScreenContext
import io.github.fopwoc.mods.framework.ui.compose.minecraft.ComposeGuiScreenInputAdapter
import io.github.fopwoc.mods.framework.ui.compose.minecraft.ComposeGuiScreenRuntimeSync
import io.github.fopwoc.mods.framework.ui.compose.navigation.NavHost
import io.github.fopwoc.mods.framework.ui.compose.navigation.NavKey
import io.github.fopwoc.mods.framework.ui.compose.navigation.entryProvider
import io.github.fopwoc.mods.framework.ui.compose.navigation.navBackStackOf
import io.github.fopwoc.mods.framework.ui.compose.navigation.navigator
import io.github.fopwoc.mods.framework.ui.compose.navigation.rememberNavBackStack
import io.github.fopwoc.mods.framework.ui.compose.node.NodeApplier
import io.github.fopwoc.mods.framework.ui.compose.node.RootNode
import io.github.fopwoc.mods.framework.ui.compose.node.TextNode
import io.github.fopwoc.mods.framework.ui.compose.runtime.BackCallback
import io.github.fopwoc.mods.framework.ui.compose.runtime.ComposeBackDispatcher
import io.github.fopwoc.mods.framework.ui.compose.runtime.ComposeGuiRuntime
import io.github.fopwoc.mods.framework.ui.compose.runtime.LocalBackDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.lwjgl.input.Keyboard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class NavigationTest {
    @Test
    fun navBackStackSupportsDuplicateKeysAndStackMutation() {
        val backStack = navBackStackOf<TestDestination>(TestDestination.Home)

        val firstDetail = backStack.push(TestDestination.Detail("Lantern Walk"))
        val duplicateDetail = backStack.push(TestDestination.Detail("Lantern Walk"))

        assertEquals(3, backStack.size)
        assertTrue(firstDetail.id != duplicateDetail.id)
        assertEquals(TestDestination.Detail("Lantern Walk"), backStack.currentKey)

        backStack.replaceTop(TestDestination.Counter("Borrowed line"))
        assertEquals(TestDestination.Counter("Borrowed line"), backStack.currentKey)

        backStack.popToRoot()
        assertEquals(1, backStack.size)
        assertEquals(TestDestination.Home, backStack.currentKey)
    }

    @Test
    fun navigatorHelperDelegatesToBackStack() {
        val backStack = navBackStackOf<TestDestination>(TestDestination.Home)
        val navigator = backStack.navigator()

        navigator.navigate(TestDestination.Detail("Lantern Walk"))
        assertTrue(navigator.canPop)
        assertEquals(TestDestination.Detail("Lantern Walk"), navigator.currentKey)

        assertTrue(navigator.navigateBack())
        assertEquals(TestDestination.Home, navigator.currentKey)
        assertFalse(navigator.canPop)
    }

    @Test
    fun navHostRendersCurrentTypedEntryFromBackStack() = runBlocking {
        val root = RootNode()
        val backStack = navBackStackOf<TestDestination>(TestDestination.Home)
        val frameClock = BroadcastFrameClock()
        val recomposerContext = Dispatchers.Unconfined + frameClock
        val recomposer = Recomposer(recomposerContext)
        val composition = Composition(NodeApplier(root), recomposer)
        val recomposeJob = launch(recomposerContext, start = CoroutineStart.UNDISPATCHED) {
            recomposer.runRecomposeAndApplyChanges()
        }

        try {
            composition.setContent {
                NavHost(
                    backStack = backStack,
                    entryProvider = entryProvider {
                        entry<TestDestination.Home> {
                            Text(text = "Home")
                        }
                        entry<TestDestination.Detail> { detail ->
                            Text(text = "Detail ${detail.label}")
                        }
                        entry<TestDestination.Counter> { counter ->
                            Text(text = "Counter ${counter.label}")
                        }
                    }
                )
            }
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(0L)
            recomposer.awaitIdle()

            assertEquals("Home", root.singleText())

            backStack.push(TestDestination.Detail("Lantern Walk"))
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(16L)
            recomposer.awaitIdle()

            assertEquals("Detail Lantern Walk", root.singleText())
        } finally {
            composition.dispose()
            recomposer.cancel()
            recomposeJob.cancelAndJoin()
        }
    }

    @Test
    fun navHostScopesViewModelsPerEntryAndClearsPoppedEntries() = runBlocking {
        val root = RootNode()
        val backStack = navBackStackOf<TestDestination>(TestDestination.Home)
        val homeInstances = mutableListOf<TrackingViewModel>()
        val detailInstances = mutableListOf<TrackingViewModel>()
        var currentHome: TrackingViewModel? = null
        var currentDetail: TrackingViewModel? = null
        val frameClock = BroadcastFrameClock()
        val recomposerContext = Dispatchers.Unconfined + frameClock
        val recomposer = Recomposer(recomposerContext)
        val composition = Composition(NodeApplier(root), recomposer)
        val recomposeJob = launch(recomposerContext, start = CoroutineStart.UNDISPATCHED) {
            recomposer.runRecomposeAndApplyChanges()
        }
        var disposed = false

        try {
            composition.setContent {
                NavHost(
                    backStack = backStack,
                    entryProvider = entryProvider {
                        entry<TestDestination.Home> {
                            val vm: TrackingViewModel = viewModel(TrackingViewModel::class)
                            currentHome = vm
                            if (homeInstances.none { it === vm }) {
                                homeInstances += vm
                            }
                            Text(text = "Home ${vm.token}")
                        }
                        entry<TestDestination.Detail> {
                            val vm: TrackingViewModel = viewModel(TrackingViewModel::class)
                            currentDetail = vm
                            if (detailInstances.none { it === vm }) {
                                detailInstances += vm
                            }
                            Text(text = "Detail ${it.label} ${vm.token}")
                        }
                    }
                )
            }
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(0L)
            recomposer.awaitIdle()

            val homeBeforePush = currentHome
            assertTrue(homeBeforePush != null)

            backStack.push(TestDestination.Detail("Lantern Walk"))
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(16L)
            recomposer.awaitIdle()

            val firstDetail = currentDetail
            assertTrue(firstDetail != null)

            backStack.push(TestDestination.Detail("Lantern Walk"))
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(32L)
            recomposer.awaitIdle()

            val secondDetail = currentDetail
            assertTrue(secondDetail != null)
            assertTrue(firstDetail !== secondDetail)

            backStack.pop()
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(48L)
            recomposer.awaitIdle()

            assertSame(firstDetail, currentDetail)
            assertTrue(secondDetail.cleared)

            backStack.pop()
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(64L)
            recomposer.awaitIdle()

            assertSame(homeBeforePush, currentHome)
            assertTrue(firstDetail.cleared)

            composition.dispose()
            disposed = true
            assertTrue(homeBeforePush.cleared)
        } finally {
            if (!disposed) {
                composition.dispose()
            }
            recomposer.cancel()
            recomposeJob.cancelAndJoin()
        }
    }

    @Test
    fun navHostUpdatesLifecycleStateForCurrentAndCoveredEntries() = runBlocking {
        val root = RootNode()
        val backStack = navBackStackOf<TestDestination>(TestDestination.Home)
        val frameClock = BroadcastFrameClock()
        val recomposerContext = Dispatchers.Unconfined + frameClock
        val recomposer = Recomposer(recomposerContext)
        val composition = Composition(NodeApplier(root), recomposer)
        val recomposeJob = launch(recomposerContext, start = CoroutineStart.UNDISPATCHED) {
            recomposer.runRecomposeAndApplyChanges()
        }
        var homeLifecycleOwner: LifecycleOwner? = null
        var detailLifecycleOwner: LifecycleOwner? = null

        try {
            composition.setContent {
                NavHost(
                    backStack = backStack,
                    entryProvider = entryProvider {
                        entry<TestDestination.Home> {
                            homeLifecycleOwner = LocalLifecycleOwner.current
                            Text(text = "Home")
                        }
                        entry<TestDestination.Detail> {
                            detailLifecycleOwner = LocalLifecycleOwner.current
                            Text(text = "Detail ${it.label}")
                        }
                    }
                )
            }
            settle(frameClock, recomposer, 0L)

            assertEquals(Lifecycle.State.RESUMED, homeLifecycleOwner?.lifecycle?.currentState)

            backStack.push(TestDestination.Detail("Lantern Walk"))
            settle(frameClock, recomposer, 16L)

            assertEquals(Lifecycle.State.STARTED, homeLifecycleOwner?.lifecycle?.currentState)
            assertEquals(Lifecycle.State.RESUMED, detailLifecycleOwner?.lifecycle?.currentState)

            backStack.pop()
            settle(frameClock, recomposer, 32L)

            assertEquals(Lifecycle.State.RESUMED, homeLifecycleOwner?.lifecycle?.currentState)
            assertEquals(Lifecycle.State.DESTROYED, detailLifecycleOwner?.lifecycle?.currentState)
        } finally {
            composition.dispose()
            recomposer.cancel()
            recomposeJob.cancelAndJoin()
        }
    }

    @Test
    fun navHostRetainsRememberSaveableStateForOptedInEntries() = runBlocking {
        val root = RootNode()
        val backStack = navBackStackOf<TestDestination>(TestDestination.Home)
        val frameClock = BroadcastFrameClock()
        val recomposerContext = Dispatchers.Unconfined + frameClock
        val recomposer = Recomposer(recomposerContext)
        val composition = Composition(NodeApplier(root), recomposer)
        val recomposeJob = launch(recomposerContext, start = CoroutineStart.UNDISPATCHED) {
            recomposer.runRecomposeAndApplyChanges()
        }
        var saveableToken = -1
        var initializerCount = 0

        try {
            composition.setContent {
                NavHost(
                    backStack = backStack,
                    entryProvider = entryProvider {
                        entry<TestDestination.Home> {
                            Text(text = "Home")
                        }
                        entry<TestDestination.Detail>(retainSaveableState = true) {
                            saveableToken = rememberSaveable {
                                initializerCount += 1
                                initializerCount
                            }
                            Text(text = "Detail ${it.label} token=$saveableToken")
                        }
                        entry<TestDestination.Counter> {
                            Text(text = "Counter ${it.label}")
                        }
                    }
                )
            }
            settle(frameClock, recomposer, 0L)

            backStack.push(TestDestination.Detail("Lantern Walk"))
            settle(frameClock, recomposer, 16L)
            assertEquals(1, saveableToken)
            assertEquals(1, initializerCount)

            backStack.push(TestDestination.Counter("Cover"))
            settle(frameClock, recomposer, 32L)

            backStack.pop()
            settle(frameClock, recomposer, 48L)

            assertEquals(1, saveableToken)
            assertEquals(1, initializerCount)
        } finally {
            composition.dispose()
            recomposer.cancel()
            recomposeJob.cancelAndJoin()
        }
    }

    @Test
    fun navHostDropsRememberSaveableStateByDefaultWhenEntryLeavesComposition() = runBlocking {
        val root = RootNode()
        val backStack = navBackStackOf<TestDestination>(TestDestination.Home)
        val frameClock = BroadcastFrameClock()
        val recomposerContext = Dispatchers.Unconfined + frameClock
        val recomposer = Recomposer(recomposerContext)
        val composition = Composition(NodeApplier(root), recomposer)
        val recomposeJob = launch(recomposerContext, start = CoroutineStart.UNDISPATCHED) {
            recomposer.runRecomposeAndApplyChanges()
        }
        var saveableToken = -1
        var initializerCount = 0

        try {
            composition.setContent {
                NavHost(
                    backStack = backStack,
                    entryProvider = entryProvider {
                        entry<TestDestination.Home> {
                            Text(text = "Home")
                        }
                        entry<TestDestination.Detail> {
                            saveableToken = rememberSaveable {
                                initializerCount += 1
                                initializerCount
                            }
                            Text(text = "Detail ${it.label} token=$saveableToken")
                        }
                        entry<TestDestination.Counter> {
                            Text(text = "Counter ${it.label}")
                        }
                    }
                )
            }
            settle(frameClock, recomposer, 0L)

            backStack.push(TestDestination.Detail("Lantern Walk"))
            settle(frameClock, recomposer, 16L)
            assertEquals(1, saveableToken)
            assertEquals(1, initializerCount)

            backStack.push(TestDestination.Counter("Cover"))
            settle(frameClock, recomposer, 32L)

            backStack.pop()
            settle(frameClock, recomposer, 48L)

            assertEquals(2, saveableToken)
            assertEquals(2, initializerCount)
        } finally {
            composition.dispose()
            recomposer.cancel()
            recomposeJob.cancelAndJoin()
        }
    }

    @Test
    fun nestedNavHostsConsumeBackFromInnermostToOutermost() = runBlocking {
        val root = RootNode()
        val outerBackStack = navBackStackOf<NestedOuterDestination>(
            NestedOuterDestination.Home,
            NestedOuterDestination.Shell
        )
        val backDispatcher = ComposeBackDispatcher()
        val frameClock = BroadcastFrameClock()
        val recomposerContext = Dispatchers.Unconfined + frameClock
        val recomposer = Recomposer(recomposerContext)
        val composition = Composition(NodeApplier(root), recomposer)
        val recomposeJob = launch(recomposerContext, start = CoroutineStart.UNDISPATCHED) {
            recomposer.runRecomposeAndApplyChanges()
        }
        var innerBackStack: io.github.fopwoc.mods.framework.ui.compose.navigation.NavBackStack<NestedInnerDestination>? = null

        try {
            composition.setContent {
                CompositionLocalProvider(LocalBackDispatcher provides backDispatcher) {
                    NavHost(
                        backStack = outerBackStack,
                        entryProvider = entryProvider {
                            entry<NestedOuterDestination.Home> {
                                Text(text = "Outer Home")
                            }
                            entry<NestedOuterDestination.Shell> {
                                val rememberedInnerBackStack = rememberNavBackStack<NestedInnerDestination>(
                                    NestedInnerDestination.Home,
                                    NestedInnerDestination.Detail
                                )
                                innerBackStack = rememberedInnerBackStack
                                NavHost(
                                    backStack = rememberedInnerBackStack,
                                    entryProvider = entryProvider {
                                        entry<NestedInnerDestination.Home> {
                                            Text(text = "Inner Home")
                                        }
                                        entry<NestedInnerDestination.Detail> {
                                            Text(text = "Inner Detail")
                                        }
                                    }
                                )
                            }
                        }
                    )
                }
            }
            settle(frameClock, recomposer, 0L)

            assertEquals(2, outerBackStack.size)
            assertEquals(2, innerBackStack?.size)

            assertTrue(backDispatcher.dispatchBack())
            settle(frameClock, recomposer, 16L)
            assertEquals(2, outerBackStack.size)
            assertEquals(1, innerBackStack?.size)

            assertTrue(backDispatcher.dispatchBack())
            settle(frameClock, recomposer, 32L)
            assertEquals(1, outerBackStack.size)
        } finally {
            composition.dispose()
            recomposer.cancel()
            recomposeJob.cancelAndJoin()
        }
    }

    @Test
    fun inputAdapterUsesBackDispatcherForEscapeBeforeFallback() {
        val runtime = ComposeGuiRuntime(onCompositionChanged = {})
        val context = ComposeGuiScreenContext()
        val inputAdapter = ComposeGuiScreenInputAdapter(context, ComposeGuiScreenRuntimeSync(runtime))
        var backCount = 0
        var fallbackCalled = false
        val registration = context.backDispatcher.register(
            BackCallback(
                enabled = true,
                onBack = {
                    backCount += 1
                    true
                }
            )
        )

        try {
            inputAdapter.keyTyped('\u0000', Keyboard.KEY_ESCAPE) {
                fallbackCalled = true
            }

            assertEquals(1, backCount)
            assertFalse(fallbackCalled)
        } finally {
            registration.dispose()
        }
    }

    private fun RootNode.singleText(): String {
        return (children.single() as TextNode).text.plainText
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

    private sealed interface TestDestination : NavKey {
        data object Home : TestDestination
        data class Detail(val label: String) : TestDestination
        data class Counter(val label: String) : TestDestination
    }

    private sealed interface NestedOuterDestination : NavKey {
        data object Home : NestedOuterDestination
        data object Shell : NestedOuterDestination
    }

    private sealed interface NestedInnerDestination : NavKey {
        data object Home : NestedInnerDestination
        data object Detail : NestedInnerDestination
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

