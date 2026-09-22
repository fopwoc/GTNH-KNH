package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

data class MeasurementClipboard(
    val operation: ClipboardOperation,
    val originAnchor: BlockSelection,
    val measurements: List<PersistedMeasurement>,
    val sourceMeasurementIds: Set<Long> = emptySet(),
    val resizeAnchorRole: MeasurementAnchorRole? = null,
)
