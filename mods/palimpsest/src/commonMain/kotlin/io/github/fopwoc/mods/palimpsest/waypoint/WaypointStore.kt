package io.github.fopwoc.mods.palimpsest.waypoint

import io.github.fopwoc.mods.framework.log.logger
import io.github.fopwoc.mods.framework.minecraft.ItemId
import io.github.fopwoc.mods.framework.serialization.FrameworkJson
import io.github.fopwoc.mods.framework.serialization.JsonFileStorage
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/** One JSON file per waypoint, so additions from separate installations merge cleanly in Git. */
class WaypointStore(private val directory: Path) {
    private val logger = logger<WaypointStore>()
    private val mutableEntries = MutableStateFlow(load())

    val entries: StateFlow<List<Waypoint>> = mutableEntries.asStateFlow()

    fun save(waypoint: Waypoint) {
        val target = file(waypoint.id)
        JsonFileStorage.writeText(
            target.toFile(),
            FrameworkJson.prettyConfig.encodeToString(waypoint.record()),
        )
        mutableEntries.value =
            (mutableEntries.value.filterNot { it.id == waypoint.id } + waypoint).sortedWith(
                compareBy(String.CASE_INSENSITIVE_ORDER, Waypoint::name)
            )
    }

    fun delete(id: UUID) {
        Files.deleteIfExists(file(id))
        mutableEntries.value = mutableEntries.value.filterNot { it.id == id }
    }

    private fun load(): List<Waypoint> {
        if (!Files.isDirectory(directory)) return emptyList()
        return Files.list(directory).use { files ->
            files
                .filter { it.fileName.toString().endsWith(".json") }
                .map { path ->
                    runCatching {
                        val id = UUID.fromString(path.fileName.toString().removeSuffix(".json"))
                        val record =
                            FrameworkJson.prettyConfig.decodeFromString<WaypointRecord>(
                                Files.readString(path)
                            )
                        record.waypoint(id)
                    }
                        .onFailure { logger.warn("Could not read waypoint {}", path, it) }
                        .getOrNull()
                }
                .filter { it != null }
                .map { checkNotNull(it) }
                .toList()
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, Waypoint::name))
        }
    }

    private fun file(id: UUID): Path = directory.resolve("$id.json")
}

@Serializable
private data class WaypointRecord(
    val name: String,
    val x: Int,
    val y: Int,
    val z: Int,
    val icon: String,
    val tracked: Boolean,
) {
    fun waypoint(id: UUID) = Waypoint(id, name, x, y, z, ItemId(icon), tracked)
}

private fun Waypoint.record() = WaypointRecord(name, x, y, z, icon.value, tracked)
