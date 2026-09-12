package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.client.ClientWorldContext
import io.github.fopwoc.mods.framework.serialization.FrameworkJson
import io.github.fopwoc.mods.framework.serialization.JsonFileStorage
import io.github.fopwoc.mods.gtnhmeasurement.MOD_ID
import java.io.File
import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import org.apache.logging.log4j.LogManager

@SideOnly(Side.CLIENT)
object MeasurementPersistence {
  private val logger = LogManager.getLogger(MeasurementPersistence::class.java)

  private val json = FrameworkJson.prettyConfig

  fun loadMeasurements(contextId: String): List<PersistedMeasurement> {
    val storageFile = storageFile(contextId)
    if (!storageFile.isFile) {
      return emptyList()
    }

    return JsonFileStorage.readOrDefault(
            file = storageFile,
            json = json,
            defaultValue = ::PersistedMeasurementSet,
            onReadFailure = {
              logger.warn("Failed to load measurements from {}", storageFile, it)
            },
        )
        .measurements
  }

  fun saveMeasurements(contextId: String, measurements: List<PersistedMeasurement>) {
    val storageFile = storageFile(contextId)
    runCatching {
          JsonFileStorage.write(
              file = storageFile,
              value = PersistedMeasurementSet(measurements = measurements),
              json = json,
          )
        }
        .onFailure {
          logger.warn("Failed to save measurements to {}", storageFile, it)
        }
  }

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

  fun resolveContextId(minecraft: Minecraft): String? = ClientWorldContext.currentId(minecraft)

  private fun storageFile(contextId: String): File =
      JsonFileStorage.modConfigFile(
          minecraftDirectory = Minecraft.getMinecraft().mcDataDir,
          modId = MOD_ID,
          "measurements",
          "$contextId.json",
      )

  private fun sanitize(value: String): String = ClientWorldContext.sanitize(value)
}

@Serializable
private data class PersistedMeasurementSet(
    val version: Int = 1,
    val measurements: List<PersistedMeasurement> = emptyList(),
)
