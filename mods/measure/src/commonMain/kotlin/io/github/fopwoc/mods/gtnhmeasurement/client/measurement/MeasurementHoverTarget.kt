package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

enum class MeasurementHoverTargetKind {
    DIRECT,
    OFFSET,
    ANCHOR,
}

data class MeasurementHoverTarget(
    val block: BlockSelection,
    val kind: MeasurementHoverTargetKind,
)
