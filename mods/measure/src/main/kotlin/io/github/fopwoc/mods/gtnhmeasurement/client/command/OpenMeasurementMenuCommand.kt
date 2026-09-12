package io.github.fopwoc.mods.gtnhmeasurement.client.command

import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.client.ClientCommand
import io.github.fopwoc.mods.framework.client.ScreenOpener
import io.github.fopwoc.mods.gtnhmeasurement.client.gui.MeasurementModeScreen
import io.github.fopwoc.mods.gtnhmeasurement.client.measurement.MeasurementExchange
import io.github.fopwoc.mods.gtnhmeasurement.client.measurement.MeasurementPersistence

@SideOnly(Side.CLIENT)
object OpenMeasurementMenuCommand :
    ClientCommand(
        name = "measure",
        usage =
            "/measure | /measure export <name> | /measure import <name> | /measure exports | /measure move",
    ) {
  override fun run(args: List<String>): String? =
      when (args.firstOrNull()?.lowercase()) {
        null -> {
          ScreenOpener.open(::MeasurementModeScreen)
          null
        }
        "export" -> MeasurementExchange.export(args.drop(1).joinToString(" "))
        "import" -> MeasurementExchange.import(args.drop(1).joinToString(" "))
        "exports" -> MeasurementExchange.list()
        "move" -> MeasurementExchange.moveSelection()
        else -> usage
      }

  override fun complete(args: List<String>): List<String> =
      when {
        args.size == 1 -> listOf("export", "import", "exports", "move")
        args.size == 2 && args[0].equals("import", ignoreCase = true) ->
            MeasurementPersistence.listExports()
        else -> emptyList()
      }
}
