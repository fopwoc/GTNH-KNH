package io.github.fopwoc.mods.framework.ui.compose

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.navigation.NavHost
import io.github.fopwoc.mods.framework.ui.compose.node.TextNode
import io.github.fopwoc.mods.framework.ui.compose.runtime.BackHandler
import io.github.fopwoc.mods.framework.ui.compose.runtime.ComposeBackDispatcher
import io.github.fopwoc.mods.framework.ui.compose.runtime.LocalBackDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class NavigationTest {
  @Test
  fun nativeBackStackSupportsDirectListMutation() {
    val stack = NavBackStack<Destination>(Destination.Home)
    stack += Destination.Detail("First")
    stack += Destination.Detail("First")
    assertEquals(3, stack.size)

    stack[stack.lastIndex] = Destination.Detail("Second")
    assertEquals(Destination.Detail("Second"), stack.last())
    stack.removeAt(stack.lastIndex)
    assertEquals(Destination.Detail("First"), stack.last())
  }

  @Test
  fun hostRendersNativeEntryProviderAfterStackMutation() = runBlocking {
    val harness = ComposeUiTestHarness()
    val stack = NavBackStack<Destination>(Destination.Home)
    try {
      harness.setContent {
        NavHost(
            backStack = stack,
            entryProvider =
                entryProvider {
                  entry<Destination.Home> { Text("Home") }
                  entry<Destination.Detail> { Text("Detail ${it.label}") }
                },
        )
      }
      harness.settle(0L)
      assertEquals("Home", harness.singleText())

      stack += Destination.Detail("Lantern Walk")
      harness.settle(16L)
      assertEquals("Detail Lantern Walk", harness.singleText())
    } finally {
      harness.dispose()
    }
  }

  @Test
  fun hostScopesViewModelsAndLifecycleByNavigation3ContentKey() = runBlocking {
    val harness = ComposeUiTestHarness()
    val stack = NavBackStack<Destination>(Destination.Home)
    var homeViewModel: TrackingViewModel? = null
    var detailViewModel: TrackingViewModel? = null
    var homeOwner: LifecycleOwner? = null
    var detailOwner: LifecycleOwner? = null

    try {
      harness.setContent {
        NavHost(
            backStack = stack,
            entryProvider =
                entryProvider {
                  entry<Destination.Home> {
                    homeOwner = LocalLifecycleOwner.current
                    homeViewModel = viewModel(TrackingViewModel::class)
                    Text("Home")
                  }
                  entry<Destination.Detail> {
                    detailOwner = LocalLifecycleOwner.current
                    detailViewModel = viewModel(TrackingViewModel::class)
                    Text("Detail ${it.label}")
                  }
                },
        )
      }
      harness.settle(0L)
      val home = assertNotNull(homeViewModel)
      assertEquals(Lifecycle.State.RESUMED, homeOwner?.lifecycle?.currentState)

      stack += Destination.Detail("Lantern Walk")
      harness.settle(16L)
      val detail = assertNotNull(detailViewModel)
      assertTrue(home !== detail)
      assertEquals(Lifecycle.State.STARTED, homeOwner?.lifecycle?.currentState)
      assertEquals(Lifecycle.State.RESUMED, detailOwner?.lifecycle?.currentState)

      stack.removeAt(stack.lastIndex)
      harness.settle(32L)
      assertSame(home, homeViewModel)
      assertEquals(Lifecycle.State.RESUMED, homeOwner?.lifecycle?.currentState)
      assertEquals(Lifecycle.State.DESTROYED, detailOwner?.lifecycle?.currentState)
      assertTrue(detail.cleared)
    } finally {
      harness.dispose()
    }
  }

  @Test
  fun navigation3DecoratorRetainsSaveableStateWhileEntryIsCovered() = runBlocking {
    val harness = ComposeUiTestHarness()
    val stack = NavBackStack<Destination>(Destination.Home)
    var token = -1
    var initializations = 0

    try {
      harness.setContent {
        NavHost(
            backStack = stack,
            entryProvider =
                entryProvider {
                  entry<Destination.Home> { Text("Home") }
                  entry<Destination.Detail> {
                    token = rememberSaveable { ++initializations }
                    Text("Detail ${it.label} token=$token")
                  }
                  entry<Destination.Cover> { Text("Cover") }
                },
        )
      }
      harness.settle(0L)
      stack += Destination.Detail("Lantern Walk")
      harness.settle(16L)
      assertEquals(1, token)

      stack += Destination.Cover
      harness.settle(32L)
      stack.removeAt(stack.lastIndex)
      harness.settle(48L)
      assertEquals(1, token)
      assertEquals(1, initializations)

      stack.removeAt(stack.lastIndex)
      harness.settle(64L)
      stack += Destination.Detail("Lantern Walk")
      harness.settle(80L)
      assertEquals(2, token)
      assertEquals(2, initializations)
    } finally {
      harness.dispose()
    }
  }

  @Test
  fun nestedHostsConsumeBackFromInnermostToOutermost() = runBlocking {
    val harness = ComposeUiTestHarness()
    val outer = NavBackStack<Outer>(Outer.Home, Outer.Shell)
    val dispatcher = ComposeBackDispatcher()
    var inner: NavBackStack<Inner>? = null

    try {
      harness.setContent {
        CompositionLocalProvider(LocalBackDispatcher provides dispatcher) {
          NavHost(
              backStack = outer,
              entryProvider =
                  entryProvider {
                    entry<Outer.Home> { Text("Outer Home") }
                    entry<Outer.Shell> {
                      val nested = remember { NavBackStack<Inner>(Inner.Home, Inner.Detail) }
                      inner = nested
                      NavHost(
                          backStack = nested,
                          entryProvider =
                              entryProvider {
                                entry<Inner.Home> { Text("Inner Home") }
                                entry<Inner.Detail> { Text("Inner Detail") }
                              },
                      )
                    }
                  },
          )
        }
      }
      harness.settle(0L)
      assertEquals(2, inner?.size)

      assertTrue(dispatcher.dispatchBack())
      harness.settle(16L)
      assertEquals(1, inner?.size)
      assertEquals(2, outer.size)

      assertTrue(dispatcher.dispatchBack())
      harness.settle(32L)
      assertEquals(1, outer.size)
    } finally {
      harness.dispose()
    }
  }

  @Test
  fun unitReturningBackHandlerConsumesBackWhenInvoked() = runBlocking {
    val harness = ComposeUiTestHarness()
    val dispatcher = ComposeBackDispatcher()
    var count = 0
    try {
      harness.setContent {
        CompositionLocalProvider(LocalBackDispatcher provides dispatcher) {
          BackHandler { count += 1 }
          Text("Ready")
        }
      }
      harness.settle(0L)
      assertTrue(dispatcher.dispatchBack())
      assertEquals(1, count)
    } finally {
      harness.dispose()
    }
  }

  private fun ComposeUiTestHarness.singleText(): String =
      (root.children.single() as TextNode).text.plainText

  private sealed interface Destination : NavKey {
    data object Home : Destination
    data class Detail(val label: String) : Destination
    data object Cover : Destination
  }

  private sealed interface Outer : NavKey {
    data object Home : Outer
    data object Shell : Outer
  }

  private sealed interface Inner : NavKey {
    data object Home : Inner
    data object Detail : Inner
  }

  class TrackingViewModel : ViewModel() {
    var cleared: Boolean = false
      private set

    override fun onCleared() {
      cleared = true
    }
  }
}
