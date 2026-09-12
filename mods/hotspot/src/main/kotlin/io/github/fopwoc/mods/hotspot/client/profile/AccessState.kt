package io.github.fopwoc.mods.hotspot.client.profile

/** What the server said to the menu's access check; [blocker] is shown as a dialog when set. */
sealed class AccessState {
  data object Unknown : AccessState()

  data object Checking : AccessState()

  data object Granted : AccessState()

  data class Blocked(val blocker: String) : AccessState()
}
