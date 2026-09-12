package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.client.ClientWorldContext
import io.github.fopwoc.mods.framework.serialization.FrameworkJson
import io.github.fopwoc.mods.framework.serialization.JsonFileStorage
import io.github.fopwoc.mods.framework.serialization.WorldScopedJsonStore
import io.github.fopwoc.mods.gtnhmeasurement.MOD_ID
import java.io.File
import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import org.apache.logging.log4j.LogManager

@SideOnly(Side.CLIENT)
object MeasurementPersistence {
  private val logger = LogManager.getLogger(MeasurementPersistence::class.java)

  private val json = FrameworkJson.prettyConfig

  /** Per-world measurement sets; see [MeasurementClientController] for the sync cycle. */
  val measurements =
      WorldScopedJsonStore(
          modId = MOD_ID,
          directory = "measurements",
          serializer = PersistedMeasurementSet.serializer(),
          defaultValue = ::PersistedMeasurementSet,
      )

  // Exports are plain measurement sets shared between worlds and servers.

  fun listExports(): List<String> =
      exportsDirectory()
          .listFiles { file -> file.isFile && file.extension == "json" }
          .orEmpty()
          .map { it.nameWithoutExtension }
          .sorted()

  fun saveExport(name: String, measurements: List<PersistedMeasurement>): Result<File> {
    val file = exportFile(name) ?: return Result.failure(IllegalArgumentException("Invalid name"))
    return runCatching {
      JsonFileStorage.write(file, PersistedMeasurementSet(measurements = measurements), json)
      file
    }
  }

  fun loadExport(name: String): List<PersistedMeasurement>? {
    val file = exportFile(name)?.takeIf(File::isFile) ?: return null
    return JsonFileStorage.readOrDefault(file, json, ::PersistedMeasurementSet) {
          logger.warn("Failed to read export {}", file, it)
        }
        .measurements
  }

  fun exportName(raw: String): String? = sanitize(raw.trim()).takeIf { it.isNotBlank() }

  private fun exportFile(name: String): File? =
      exportName(name)?.let { File(exportsDirectory(), "$it.json") }

  private fun exportsDirectory(): File =
      JsonFileStorage.modConfigFile(Minecraft.getMinecraft().mcDataDir, MOD_ID, "exports")

  private fun sanitize(value: String): String = ClientWorldContext.sanitize(value)
}

@Serializable
data class PersistedMeasurementSet(
    val version: Int = 1,
    val measurements: List<PersistedMeasurement> = emptyList(),
)
