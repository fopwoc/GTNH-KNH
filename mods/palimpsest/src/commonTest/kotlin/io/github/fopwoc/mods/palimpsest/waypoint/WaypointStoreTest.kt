package io.github.fopwoc.mods.palimpsest.waypoint

import io.github.fopwoc.mods.framework.minecraft.ItemId
import java.nio.file.Files
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WaypointStoreTest {
    @Test
    fun savesEditsAndDeletesWaypointsAcrossReopen() {
        val directory = Files.createTempDirectory("palimpsest-waypoints-")
        try {
            val id = UUID.randomUUID()
            val first = Waypoint(id, "Base", 12, 65, -30, ItemId("minecraft:compass"))
            WaypointStore(directory).save(first)
            assertEquals(listOf(first), WaypointStore(directory).entries.value)

            val edited =
                first.copy(
                    name = "Workshop",
                    y = 70,
                    icon = ItemId("minecraft:chest"),
                    tracked = false,
                )
            WaypointStore(directory).save(edited)
            assertEquals(listOf(edited), WaypointStore(directory).entries.value)

            WaypointStore(directory).delete(id)
            assertTrue(WaypointStore(directory).entries.value.isEmpty())
        } finally {
            Files.walk(directory).use { files ->
                files.sorted(Comparator.reverseOrder()).forEach(Files::delete)
            }
        }
    }
}
