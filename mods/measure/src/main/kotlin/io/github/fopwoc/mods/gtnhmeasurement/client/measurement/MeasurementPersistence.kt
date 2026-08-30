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

  fun resolveContextId(minecraft: Minecraft): String? {
    val world = minecraft.theWorld ?: return null
    val worldName = resolveWorldName(world)
    val serverDescriptor = resolveServerDescriptor(minecraft)
    val baseId =
        when {
          minecraft.isSingleplayer -> "singleplayer-${sanitize(worldName ?: "world")}"
          serverDescriptor != null -> "server-${sanitize(serverDescriptor)}"
          else -> "world-${sanitize(worldName ?: "world")}"
        }
    return baseId
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

  private fun resolveServerDescriptor(minecraft: Minecraft): String? =
      runCatching {
            val accessor =
                minecraft.javaClass.methods.firstOrNull { method ->
                  method.parameterCount == 0 &&
                      (method.name == "func_147104_D" || method.name == "getCurrentServerData")
                } ?: return@runCatching null
            val serverData = accessor.invoke(minecraft) ?: return@runCatching null
            readStringProperty(serverData, "serverIP")
                ?: readStringProperty(serverData, "serverName")
          }
          .getOrNull()
          ?.takeIf(String::isNotBlank)

  private fun readStringProperty(instance: Any, propertyName: String): String? =
      runCatching {
            val field =
                instance.javaClass.fields.firstOrNull { it.name == propertyName }
                    ?: return@runCatching null
            field.get(instance) as? String
          }
          .getOrNull()

  private fun sanitize(value: String): String = value.replace(Regex("[^A-Za-z0-9._-]"), "_")
}

@Serializable
private data class PersistedMeasurementSet(
    val version: Int = 1,
    val measurements: List<PersistedMeasurement> = emptyList(),
)
