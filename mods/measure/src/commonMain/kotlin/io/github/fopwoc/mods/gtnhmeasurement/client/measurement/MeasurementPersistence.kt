package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import io.github.fopwoc.mods.framework.log.logger
import io.github.fopwoc.mods.framework.platform.Platform
import io.github.fopwoc.mods.framework.serialization.FrameworkJson
import io.github.fopwoc.mods.framework.serialization.JsonFileStorage
import io.github.fopwoc.mods.framework.serialization.WorldScopedJsonStore
import io.github.fopwoc.mods.gtnhmeasurement.ModMetadata.MOD_ID
import java.io.File

/** One measurement set per local world or server, with portable export files. */
object MeasurementPersistence {
    private val logger = logger<MeasurementPersistence>()
    private val json = FrameworkJson.prettyConfig

    /** Per-world measurement sets; [MeasurementSaveCycle] loads and saves them. */
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
        val file =
            exportFile(name) ?: return Result.failure(IllegalArgumentException("Invalid name"))
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

    fun exportName(raw: String): String? = sanitize(raw.trim()).takeIf(String::isNotBlank)

    private fun exportFile(name: String): File? =
        exportName(name)?.let { File(exportsDirectory(), "$it.json") }

    private fun exportsDirectory(): File =
        JsonFileStorage.modConfigFile(Platform.gameDirectory, MOD_ID, "exports")

    private fun sanitize(raw: String): String = raw.replace(Regex("[^A-Za-z0-9._-]"), "_")
}
