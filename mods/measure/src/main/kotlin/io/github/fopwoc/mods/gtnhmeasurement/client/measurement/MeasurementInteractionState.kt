package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

object MeasurementInteractionState {
  private var hoveredTarget: MeasurementHoverTarget? = null

  val currentHoveredTarget: MeasurementHoverTarget?
    get() = hoveredTarget

  fun updateHoveredTarget(target: MeasurementHoverTarget?) {
    hoveredTarget = target
  }

  fun clearHoveredTarget() {
    hoveredTarget = null
  }
}
