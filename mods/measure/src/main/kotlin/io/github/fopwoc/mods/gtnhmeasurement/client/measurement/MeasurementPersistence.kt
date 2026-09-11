package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.serialization.FrameworkJson
import io.github.fopwoc.mods.framework.serialization.JsonFileStorage
import io.github.fopwoc.mods.gtnhmeasurement.MOD_ID
import io.github.fopwoc.mods.gtnhmeasurement.MeasurementMod
import java.io.File
import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import net.minecraft.world.World

@SideOnly(Side.CLIENT)
object MeasurementPersistence {
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
              MeasurementMod.logger.warn("Failed to load measurements from {}", storageFile, it)
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
          MeasurementMod.logger.warn("Failed to save measurements to {}", storageFile, it)
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
          MeasurementMod.logger.warn("Failed to read export {}", file, it)
        }
        .measurements
  }

  fun exportName(raw: String): String? = sanitize(raw.trim()).takeIf { it.isNotBlank() }

  private fun exportFile(name: String): File? =
      exportName(name)?.let { File(exportsDirectory(), "$it.json") }

  private fun exportsDirectory(): File =
      JsonFileStorage.modConfigFile(Minecraft.getMinecraft().mcDataDir, MOD_ID, "exports")

  fun resolveContextId(minecraft: Minecraft): String? {
    val world = minecraft.theWorld ?: return null
    val worldName = resolveWorldName(world)
    val serverDescriptor = resolveServerDescriptor(minecraft)
    return when {
      minecraft.isSingleplayer -> "singleplayer-${sanitize(worldName ?: "world")}"
      serverDescriptor != null -> "server-${sanitize(serverDescriptor)}"
      else -> "world-${sanitize(worldName ?: "world")}"
    }
  }

  private fun resolveWorldName(world: World): String? =
      runCatching {
            world.worldInfo.worldName
          }
          .getOrNull()
          ?.takeIf(String::isNotBlank)

  private fun storageFile(contextId: String): File =
      JsonFileStorage.modConfigFile(
          minecraftDirectory = Minecraft.getMinecraft().mcDataDir,
          modId = MOD_ID,
          "measurements",
          "$contextId.json",
      )

  private fun resolveServerDescriptor(minecraft: Minecraft): String? {
    // func_147104_D is Minecraft.getCurrentServerData; it has no MCP name in the 1.7.10 mappings.
    val serverData = minecraft.func_147104_D() ?: return null
    return listOf(serverData.serverIP, serverData.serverName).firstOrNull { !it.isNullOrBlank() }
  }

  private fun sanitize(value: String): String = value.replace(Regex("[^A-Za-z0-9._-]"), "_")
}

@Serializable
private data class PersistedMeasurementSet(
    val version: Int = 1,
    val measurements: List<PersistedMeasurement> = emptyList(),
)
