package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import io.github.fopwoc.mods.framework.client.ClientBackend
import io.github.fopwoc.mods.gtnhmeasurement.measurement.MeasurementMode
import io.github.fopwoc.mods.gtnhmeasurement.measurement.MeasurementSession

/** Export/import of measurement sets, shared by the `/measure` command and the menu. */
object MeasurementExchange {
    fun export(name: String): String {
        val dimensionId = ClientBackend.current.currentDimensionId ?: return "Open a world first"
        val cleanName = MeasurementPersistence.exportName(name) ?: return "Give the export a name"
        val measurements = MeasurementSelectionState.exportCandidates(dimensionId)
        if (measurements.isEmpty()) {
            return "Nothing to export in this dimension"
        }
        return MeasurementPersistence.saveExport(cleanName, measurements)
            .fold(
                onSuccess = { "Exported ${measurements.size} to ${it.name}" },
                onFailure = { "Export failed: ${it.message}" },
            )
    }

    fun import(name: String): String {
        val dimensionId = ClientBackend.current.currentDimensionId ?: return "Open a world first"
        val cleanName = MeasurementPersistence.exportName(name) ?: return "Give the export a name"
        val measurements =
            MeasurementPersistence.loadExport(cleanName) ?: return "No export named $cleanName"
        val added = MeasurementSelectionState.importMeasurements(measurements, dimensionId)
        return when {
            measurements.isEmpty() -> "$cleanName is empty"
            added == 0 -> "All ${measurements.size} measurements from $cleanName already exist"
            else ->
                "Imported and selected $added of ${measurements.size} from $cleanName; use Move to place them"
        }
    }

    /** Begins a batch move of the selection; turns measuring on if it was off. */
    fun moveSelection(): String {
        val dimensionId = ClientBackend.current.currentDimensionId ?: return "Open a world first"
        if (!MeasurementSession.isActive) {
            MeasurementSession.switchTo(MeasurementMode.LINE)
        }
        return if (MeasurementSelectionState.beginMoveSelection(dimensionId)) {
            "Aim and click ${MeasurementShortcutScheme.CREATE_CLICK_LABEL} to place the selection"
        } else {
            "Nothing selected"
        }
    }

    fun list(): String {
        val names = MeasurementPersistence.listExports()
        return if (names.isEmpty()) "No exports yet" else "Exports: ${names.joinToString(", ")}"
    }
}
