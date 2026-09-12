package io.github.fopwoc.mods.hotspot.client.profile

/** What the client is currently waiting for; shown in the menu header and the hotbar line. */
sealed class ProfileSessionStatus {
  data object Idle : ProfileSessionStatus()

  data object Waiting : ProfileSessionStatus()

  data class Profiling(val remainingTicks: Int, val totalTicks: Int) : ProfileSessionStatus()

  data object Receiving : ProfileSessionStatus()

  data class Failed(val reason: String) : ProfileSessionStatus()

  val isBusy: Boolean
    get() = this is Waiting || this is Profiling || this is Receiving
}
