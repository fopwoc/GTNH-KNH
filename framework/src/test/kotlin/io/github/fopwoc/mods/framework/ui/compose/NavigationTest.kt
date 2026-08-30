package io.github.fopwoc.mods.framework.ui.compose

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.navigation.NavHost
import io.github.fopwoc.mods.framework.ui.compose.navigation.NavKey
import io.github.fopwoc.mods.framework.ui.compose.navigation.entryProvider
import io.github.fopwoc.mods.framework.ui.compose.navigation.navBackStackOf
import io.github.fopwoc.mods.framework.ui.compose.navigation.navBackStackSaver
import io.github.fopwoc.mods.framework.ui.compose.navigation.navigator
import io.github.fopwoc.mods.framework.ui.compose.navigation.rememberNavBackStack
import io.github.fopwoc.mods.framework.ui.compose.node.ButtonNode
import io.github.fopwoc.mods.framework.ui.compose.node.TextNode
import io.github.fopwoc.mods.framework.ui.compose.runtime.BackHandler
import io.github.fopwoc.mods.framework.ui.compose.runtime.ComposeBackDispatcher
import io.github.fopwoc.mods.framework.ui.compose.runtime.LocalBackDispatcher
import io.github.fopwoc.mods.framework.ui.compose.runtime.rememberScrollState
import io.github.fopwoc.mods.framework.ui.compose.state.ScrollState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

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
  fun navBackStackSaverRestoresStableEntryIdsAndNextId() {
    val backStack = navBackStackOf<TestDestination>(TestDestination.Home)
    val firstDetail = backStack.push(TestDestination.Detail("Lantern Walk"))
    val topCounter = backStack.push(TestDestination.Counter("Cover"))

    val saver = navBackStackSaver(testDestinationSaver)
    val savedState = with(saver) { AlwaysSaveScope.save(backStack) }
    val restored:
        io.github.fopwoc.mods.framework.ui.compose.navigation.NavBackStack<TestDestination> =
        assertNotNull(savedState?.let(saver::restore))

    assertEquals(backStack.entries.toList(), restored.entries.toList())
    assertEquals(backStack.currentKey, restored.currentKey)

    val pushedAfterRestore = restored.push(TestDestination.Detail("After Restore"))
    assertTrue(pushedAfterRestore.id > topCounter.id)
    assertEquals(
        listOf(1L, firstDetail.id, topCounter.id, pushedAfterRestore.id),
        restored.entries.map { it.id },
    )
  }

  @Test
  fun navHostRendersCurrentTypedEntryFromBackStack() = runBlocking {
    val harness = ComposeUiTestHarness()
    val backStack = navBackStackOf<TestDestination>(TestDestination.Home)

    try {
      harness.setContent {
        NavHost(
            backStack = backStack,
            entryProvider =
                entryProvider {
                  entry<TestDestination.Home> {
                    Text(text = "Home")
                  }
                  entry<TestDestination.Detail> { detail ->
                    Text(text = "Detail ${detail.label}")
                  }
                  entry<TestDestination.Counter> { counter ->
                    Text(text = "Counter ${counter.label}")
                  }
                },
        )
      }
      harness.settle(0L)

      assertEquals("Home", harness.singleText())

      backStack.push(TestDestination.Detail("Lantern Walk"))
      harness.settle(16L)

      assertEquals("Detail Lantern Walk", harness.singleText())
    } finally {
      harness.dispose()
    }
  }

  @Test
  fun navHostRecomposesWhenNavigatorIsMutatedFromButtonCallback() = runBlocking {
    val harness = ComposeUiTestHarness()

    try {
      harness.setContent {
        val backStack = rememberNavBackStack<TestDestination>(TestDestination.Home)
        val navigator = backStack.navigator()

        androidx.compose.runtime.key(navigator.currentKey) {
          io.github.fopwoc.mods.framework.ui.compose.foundation.Column {
            io.github.fopwoc.mods.framework.ui.compose.component.native.Button(
                text = "Open detail",
                onClick = {
                  navigator.replaceTop(TestDestination.Detail("Lantern Walk"))
                },
            )
            NavHost(
                backStack = backStack,
                entryProvider =
                    entryProvider {
                      entry<TestDestination.Home> {
                        Text(text = "Home")
                      }
                      entry<TestDestination.Detail> { detail ->
                        Text(text = "Detail ${detail.label}")
                      }
                      entry<TestDestination.Counter> { counter ->
                        Text(text = "Counter ${counter.label}")
                      }
                    },
            )
          }
        }
      }
      harness.settle(0L)

      assertEquals(
          "Home",
          (harness.root.children.single()
                  as io.github.fopwoc.mods.framework.ui.compose.node.ColumnNode)
              .children[1]
              .let { it as TextNode }
              .text
              .plainText,
      )

      val column =
          harness.root.children.single()
              as io.github.fopwoc.mods.framework.ui.compose.node.ColumnNode
      val button = column.children[0] as ButtonNode
      button.onClick()
      harness.settle(16L)

      val updatedColumn =
          harness.root.children.single()
              as io.github.fopwoc.mods.framework.ui.compose.node.ColumnNode
      assertEquals("Detail Lantern Walk", (updatedColumn.children[1] as TextNode).text.plainText)
    } finally {
      harness.dispose()
    }
  }

  @Test
  fun navHostScopesViewModelsPerEntryAndClearsPoppedEntries() = runBlocking {
    val harness = ComposeUiTestHarness()
    val backStack = navBackStackOf<TestDestination>(TestDestination.Home)
    val homeInstances = mutableListOf<TrackingViewModel>()
    val detailInstances = mutableListOf<TrackingViewModel>()
    var currentHome: TrackingViewModel? = null
    var currentDetail: TrackingViewModel? = null
    var disposed = false

    try {
      harness.setContent {
        NavHost(
            backStack = backStack,
            entryProvider =
                entryProvider {
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
                },
        )
      }
      harness.settle(0L)

      val homeBeforePush = currentHome
      assertTrue(homeBeforePush != null)

      backStack.push(TestDestination.Detail("Lantern Walk"))
      harness.settle(16L)

      val firstDetail = currentDetail
      assertTrue(firstDetail != null)

      backStack.push(TestDestination.Detail("Lantern Walk"))
      harness.settle(32L)

      val secondDetail = currentDetail
      assertTrue(secondDetail != null)
      assertTrue(firstDetail !== secondDetail)

      backStack.pop()
      harness.settle(48L)

      assertSame(firstDetail, currentDetail)
      assertTrue(secondDetail.cleared)

      backStack.pop()
      harness.settle(64L)

      assertSame(homeBeforePush, currentHome)
      assertTrue(firstDetail.cleared)

      harness.dispose()
      disposed = true
      assertTrue(homeBeforePush.cleared)
    } finally {
      if (!disposed) {
        harness.dispose()
      }
    }
  }

  @Test
  fun navHostUpdatesLifecycleStateForCurrentAndCoveredEntries() = runBlocking {
    val harness = ComposeUiTestHarness()
    val backStack = navBackStackOf<TestDestination>(TestDestination.Home)
    var homeLifecycleOwner: LifecycleOwner? = null
    var detailLifecycleOwner: LifecycleOwner? = null

    try {
      harness.setContent {
        NavHost(
            backStack = backStack,
            entryProvider =
                entryProvider {
                  entry<TestDestination.Home> {
                    homeLifecycleOwner = LocalLifecycleOwner.current
                    Text(text = "Home")
                  }
                  entry<TestDestination.Detail> {
                    detailLifecycleOwner = LocalLifecycleOwner.current
                    Text(text = "Detail ${it.label}")
                  }
                },
        )
      }
      harness.settle(0L)

      assertEquals(Lifecycle.State.RESUMED, homeLifecycleOwner?.lifecycle?.currentState)

      backStack.push(TestDestination.Detail("Lantern Walk"))
      harness.settle(16L)

      assertEquals(Lifecycle.State.STARTED, homeLifecycleOwner?.lifecycle?.currentState)
      assertEquals(Lifecycle.State.RESUMED, detailLifecycleOwner?.lifecycle?.currentState)

      backStack.pop()
      harness.settle(32L)

      assertEquals(Lifecycle.State.RESUMED, homeLifecycleOwner?.lifecycle?.currentState)
      assertEquals(Lifecycle.State.DESTROYED, detailLifecycleOwner?.lifecycle?.currentState)
    } finally {
      harness.dispose()
    }
  }

  @Test
  fun navHostRetainsRememberSaveableStateForOptedInEntries() = runBlocking {
    val harness = ComposeUiTestHarness()
    val backStack = navBackStackOf<TestDestination>(TestDestination.Home)
    var saveableToken = -1
    var initializerCount = 0

    try {
      harness.setContent {
        NavHost(
            backStack = backStack,
            entryProvider =
                entryProvider {
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
                },
        )
      }
      harness.settle(0L)

      backStack.push(TestDestination.Detail("Lantern Walk"))
      harness.settle(16L)
      assertEquals(1, saveableToken)
      assertEquals(1, initializerCount)

      backStack.push(TestDestination.Counter("Cover"))
      harness.settle(32L)

      backStack.pop()
      harness.settle(48L)

      assertEquals(1, saveableToken)
      assertEquals(1, initializerCount)
    } finally {
      harness.dispose()
    }
  }

  @Test
  fun navHostDropsRememberSaveableStateByDefaultWhenEntryLeavesComposition() = runBlocking {
    val harness = ComposeUiTestHarness()
    val backStack = navBackStackOf<TestDestination>(TestDestination.Home)
    var saveableToken = -1
    var initializerCount = 0

    try {
      harness.setContent {
        NavHost(
            backStack = backStack,
            entryProvider =
                entryProvider {
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
                },
        )
      }
      harness.settle(0L)

      backStack.push(TestDestination.Detail("Lantern Walk"))
      harness.settle(16L)
      assertEquals(1, saveableToken)
      assertEquals(1, initializerCount)

      backStack.push(TestDestination.Counter("Cover"))
      harness.settle(32L)

      backStack.pop()
      harness.settle(48L)

      assertEquals(2, saveableToken)
      assertEquals(2, initializerCount)
    } finally {
      harness.dispose()
    }
  }

  @Test
  fun navHostRetainsRememberedScrollStateForOptedInEntries() = runBlocking {
    val harness = ComposeUiTestHarness()
    val backStack = navBackStackOf<TestDestination>(TestDestination.Home)
    var detailScrollState: ScrollState? = null

    try {
      harness.setContent {
        NavHost(
            backStack = backStack,
            entryProvider =
                entryProvider {
                  entry<TestDestination.Home> {
                    Text(text = "Home")
                  }
                  entry<TestDestination.Detail>(retainSaveableState = true) {
                    val scrollState = rememberScrollState()
                    detailScrollState = scrollState
                    Text(text = "Detail ${it.label} scroll=${scrollState.value}")
                  }
                  entry<TestDestination.Counter> {
                    Text(text = "Counter ${it.label}")
                  }
                },
        )
      }
      harness.settle(0L)

      backStack.push(TestDestination.Detail("Lantern Walk"))
      harness.settle(16L)

      val firstScrollState = detailScrollState
      assertNotNull(firstScrollState)
      firstScrollState.updateMaxValue(240)
      firstScrollState.scrollTo(73)
      harness.settle(32L)
      assertEquals("Detail Lantern Walk scroll=73", harness.singleText())

      backStack.push(TestDestination.Counter("Cover"))
      harness.settle(48L)

      backStack.pop()
      harness.settle(64L)

      assertEquals("Detail Lantern Walk scroll=73", harness.singleText())
      assertEquals(73, detailScrollState?.value)
    } finally {
      harness.dispose()
    }
  }

  @Test
  fun nestedNavHostsConsumeBackFromInnermostToOutermost() = runBlocking {
    val harness = ComposeUiTestHarness()
    val outerBackStack =
        navBackStackOf<NestedOuterDestination>(
            NestedOuterDestination.Home,
            NestedOuterDestination.Shell,
        )
    val backDispatcher = ComposeBackDispatcher()
    var innerBackStack:
        io.github.fopwoc.mods.framework.ui.compose.navigation.NavBackStack<
            NestedInnerDestination
        >? =
        null

    try {
      harness.setContent {
        CompositionLocalProvider(LocalBackDispatcher provides backDispatcher) {
          NavHost(
              backStack = outerBackStack,
              entryProvider =
                  entryProvider {
                    entry<NestedOuterDestination.Home> {
                      Text(text = "Outer Home")
                    }
                    entry<NestedOuterDestination.Shell> {
                      val rememberedInnerBackStack =
                          rememberNavBackStack<NestedInnerDestination>(
                              NestedInnerDestination.Home,
                              NestedInnerDestination.Detail,
                          )
                      innerBackStack = rememberedInnerBackStack
                      NavHost(
                          backStack = rememberedInnerBackStack,
                          entryProvider =
                              entryProvider {
                                entry<NestedInnerDestination.Home> {
                                  Text(text = "Inner Home")
                                }
                                entry<NestedInnerDestination.Detail> {
                                  Text(text = "Inner Detail")
                                }
                              },
                      )
                    }
                  },
          )
        }
      }
      harness.settle(0L)

      assertEquals(2, outerBackStack.size)
      assertEquals(2, innerBackStack?.size)

      assertTrue(backDispatcher.dispatchBack())
      harness.settle(16L)
      assertEquals(2, outerBackStack.size)
      assertEquals(1, innerBackStack?.size)

      assertTrue(backDispatcher.dispatchBack())
      harness.settle(32L)
      assertEquals(1, outerBackStack.size)
    } finally {
      harness.dispose()
    }
  }

  @Test
  fun unitReturningBackHandlerConvenienceConsumesBackWhenInvoked() = runBlocking {
    val harness = ComposeUiTestHarness()
    val backDispatcher = ComposeBackDispatcher()
    var backCount = 0

    try {
      harness.setContent {
        CompositionLocalProvider(LocalBackDispatcher provides backDispatcher) {
          BackHandler {
            backCount += 1
          }
          Text(text = "Back handler registered")
        }
      }
      harness.settle(0L)

      assertTrue(backDispatcher.dispatchBack())
      assertEquals(1, backCount)
    } finally {
      harness.dispose()
    }
  }

  private fun ComposeUiTestHarness.singleText(): String {
    return (root.children.single() as TextNode).text.plainText
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

  private companion object {
    val AlwaysSaveScope =
        object : SaverScope {
          override fun canBeSaved(value: Any): Boolean = true
        }

    val testDestinationSaver =
        Saver<TestDestination, String>(
            save = { destination ->
              when (destination) {
                TestDestination.Home -> "home"
                is TestDestination.Detail -> "detail:${destination.label}"
                is TestDestination.Counter -> "counter:${destination.label}"
              }
            },
            restore = { restored ->
              when {
                restored == "home" -> TestDestination.Home
                restored.startsWith("detail:") ->
                    TestDestination.Detail(restored.removePrefix("detail:"))
                restored.startsWith("counter:") ->
                    TestDestination.Counter(restored.removePrefix("counter:"))
                else -> null
              }
            },
        )
  }
}
