package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import net.minecraft.client.Minecraft

/** Export/import of measurement sets, shared by the `/measure` command and the menu. */
@SideOnly(Side.CLIENT)
object MeasurementExchange {
  fun export(name: String, minecraft: Minecraft = Minecraft.getMinecraft()): String {
    val dimensionId = minecraft.theWorld?.provider?.dimensionId ?: return "Open a world first"
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
    val cleanName = MeasurementPersistence.exportName(name) ?: return "Give the export a name"
    val measurements =
        MeasurementPersistence.loadExport(cleanName) ?: return "No export named $cleanName"
    val added = MeasurementSelectionState.importMeasurements(measurements)
    return when {
      measurements.isEmpty() -> "$cleanName is empty"
      added == 0 -> "All ${measurements.size} measurements from $cleanName already exist"
      else -> "Imported $added of ${measurements.size} from $cleanName"
    }
  }

  fun list(): String {
    val names = MeasurementPersistence.listExports()
    return if (names.isEmpty()) "No exports yet" else "Exports: ${names.joinToString(", ")}"
  }
}
