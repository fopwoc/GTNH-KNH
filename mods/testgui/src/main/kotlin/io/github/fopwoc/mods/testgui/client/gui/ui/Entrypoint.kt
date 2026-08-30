package io.github.fopwoc.mods.testgui.client.gui.ui

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.navigation.NavHost
import io.github.fopwoc.mods.framework.ui.compose.navigation.entryProvider
import io.github.fopwoc.mods.framework.ui.compose.navigation.rememberNavBackStack
import io.github.fopwoc.mods.framework.ui.compose.navigation.rememberNavigator
import io.github.fopwoc.mods.testgui.client.gui.ui.chrome.TestGuiScaffold
import io.github.fopwoc.mods.testgui.client.gui.ui.page.controls.ControlsRoute
import io.github.fopwoc.mods.testgui.client.gui.ui.page.hostedstress.HostedStressRoute
import io.github.fopwoc.mods.testgui.client.gui.ui.page.inputs.InputsRoute
import io.github.fopwoc.mods.testgui.client.gui.ui.page.layout.LayoutRoute
import io.github.fopwoc.mods.testgui.client.gui.ui.page.navigation.NavigationRoute
import io.github.fopwoc.mods.testgui.client.gui.ui.page.overview.OverviewRoute
import io.github.fopwoc.mods.testgui.client.gui.ui.page.scrollclipstress.ScrollClipStressRoute
import io.github.fopwoc.mods.testgui.client.gui.ui.page.state.StateRoute
import io.github.fopwoc.mods.testgui.client.gui.ui.page.text.TextAndTooltipsRoute

@Composable
fun Entrypoint(
    screenWidth: Int,
    screenHeight: Int,
    debugStatus: String = "",
    onDebugEvent: (String) -> Unit = {},
    onClose: () -> Unit = {},
) {
  val backStack =
      rememberNavBackStack(
          TestGuiDestination.Overview,
          keySaver = testGuiDestinationSaver,
      )
  val navigator = rememberNavigator(backStack)
  val currentDestination = navigator.currentKey ?: TestGuiDestination.Overview

  TestGuiScaffold(
      screenWidth = screenWidth,
      screenHeight = screenHeight,
      currentDestination = currentDestination,
      canNavigateBack = navigator.canPop,
      debugStatus = debugStatus,
      onNavigateBack = {
        onDebugEvent("shell/back:${currentDestination.title}")
        navigator.pop()
      },
      onClose = {
        onDebugEvent("shell/close")
        onClose()
      },
  ) {
    NavHost(
        backStack = backStack,
        entryProvider =
            entryProvider {
              entry<TestGuiDestination.Overview>(retainSaveableState = true) {
                OverviewRoute(
                    featureCatalog = testGuiFeatureCatalog,
                    onOpenFeature = { feature ->
                      onDebugEvent("menu/open:${feature.title}")
                      navigator.push(feature.destination)
                    },
                )
              }
              entry<TestGuiDestination.Controls>(retainSaveableState = true) {
                ControlsRoute()
              }
              entry<TestGuiDestination.TextAndTooltips>(retainSaveableState = true) {
                TextAndTooltipsRoute()
              }
              entry<TestGuiDestination.InputsAndLists>(retainSaveableState = true) {
                InputsRoute()
              }
              entry<TestGuiDestination.LayoutAndScroll>(retainSaveableState = true) {
                LayoutRoute()
              }
              entry<TestGuiDestination.StateLab>(retainSaveableState = true) {
                StateRoute(scope = this)
              }
              entry<TestGuiDestination.Navigation>(retainSaveableState = true) {
                NavigationRoute(scope = this)
              }
              entry<TestGuiDestination.HostedStress>(retainSaveableState = true) {
                HostedStressRoute()
              }
              entry<TestGuiDestination.ScrollClipStress>(retainSaveableState = true) {
                ScrollClipStressRoute()
              }
            },
    )
  }
}
