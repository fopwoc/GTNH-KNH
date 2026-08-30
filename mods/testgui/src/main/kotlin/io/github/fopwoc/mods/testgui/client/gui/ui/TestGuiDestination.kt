package io.github.fopwoc.mods.testgui.client.gui.ui

import androidx.compose.runtime.saveable.Saver
import io.github.fopwoc.mods.framework.ui.compose.navigation.NavKey

sealed interface TestGuiDestination : NavKey {
  val id: String
  val title: String
  val summary: String

  data object Overview : TestGuiDestination {
    override val id: String = "overview"
    override val title: String = "Screens"
    override val summary: String = "Choose a focused framework demo and open it full-screen."
  }

  data object Controls : TestGuiDestination {
    override val id: String = "controls"
    override val title: String = "Controls"
    override val summary: String =
        "Buttons, checkbox, slider, segmented controls, tabs, and toggles."
  }

  data object TextAndTooltips : TestGuiDestination {
    override val id: String = "text-tooltips"
    override val title: String = "Text & Tooltips"
    override val summary: String =
        "Wrapped text, Minecraft colors, styled spans, and hover tooltips."
  }

  data object InputsAndLists : TestGuiDestination {
    override val id: String = "inputs-lists"
    override val title: String = "Inputs & Lists"
    override val summary: String =
        "Hosted text fields, focus state, list selection, and commit flows."
  }

  data object LayoutAndScroll : TestGuiDestination {
    override val id: String = "layout-scroll"
    override val title: String = "Layout & Scroll"
    override val summary: String =
        "Panel, Box, Row, Column, weight, alignment, offsets, and scrolling."
  }

  data object StateLab : TestGuiDestination {
    override val id: String = "state-lab"
    override val title: String = "State Lab"
    override val summary: String =
        "Real ViewModels plus route-local remember vs rememberSaveable behavior."
  }

  data object Navigation : TestGuiDestination {
    override val id: String = "navigation"
    override val title: String = "Navigation"
    override val summary: String =
        "Typed back stacks, duplicates, replace-top, pop, and nested NavHost demos."
  }

  data object HostedStress : TestGuiDestination {
    override val id: String = "hosted-stress"
    override val title: String = "Hosted Stress"
    override val summary: String =
        "Dense hosted widgets, focus churn, list syncing, and fast state mutation."
  }

  data object ScrollClipStress : TestGuiDestination {
    override val id: String = "scroll-clip-stress"
    override val title: String = "Scroll & Clip Stress"
    override val summary: String =
        "Long scroll regions, offset badges, clipping pressure, and layout density changes."
  }
}

val testGuiDestinations: List<TestGuiDestination> =
    listOf(
        TestGuiDestination.Overview,
        TestGuiDestination.Controls,
        TestGuiDestination.TextAndTooltips,
        TestGuiDestination.InputsAndLists,
        TestGuiDestination.LayoutAndScroll,
        TestGuiDestination.StateLab,
        TestGuiDestination.Navigation,
        TestGuiDestination.HostedStress,
        TestGuiDestination.ScrollClipStress,
    )

val testGuiDestinationSaver: Saver<TestGuiDestination, String> =
    Saver(
        save = { destination -> destination.id },
        restore = { savedId ->
          testGuiDestinations.firstOrNull { destination ->
            destination.id == savedId
          }
        },
    )
