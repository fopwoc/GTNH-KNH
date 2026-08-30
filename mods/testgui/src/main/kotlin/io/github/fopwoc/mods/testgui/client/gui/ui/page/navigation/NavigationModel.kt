package io.github.fopwoc.mods.testgui.client.gui.ui.page.navigation

import androidx.compose.runtime.saveable.Saver
import io.github.fopwoc.mods.framework.ui.compose.navigation.NavKey

data class NavigationModel(
    val selfPushes: Int = 0,
    val outerEvents: List<String> = listOf("Outer navigator ready"),
    val innerEvents: List<String> = listOf("Inner NavHost ready"),
)

sealed interface NavigationInnerDestination : NavKey {
  val label: String

  data object Home : NavigationInnerDestination {
    override val label: String = "Inner Home"
  }

  data class Detail(override val label: String) : NavigationInnerDestination
}

val navigationInnerDestinationSaver: Saver<NavigationInnerDestination, String> =
    Saver(
        save = { destination ->
          when (destination) {
            NavigationInnerDestination.Home -> "home"
            is NavigationInnerDestination.Detail -> "detail:${destination.label}"
          }
        },
        restore = { saved ->
          when {
            saved == "home" -> NavigationInnerDestination.Home
            saved.startsWith("detail:") ->
                NavigationInnerDestination.Detail(saved.removePrefix("detail:"))
            else -> null
          }
        },
    )
