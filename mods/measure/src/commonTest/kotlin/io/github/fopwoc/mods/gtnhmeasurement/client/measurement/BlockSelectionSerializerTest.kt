package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class BlockSelectionSerializerTest {
    @Test
    fun readsExistingNumericGtnhDimension() {
        val block =
            Json.decodeFromString<BlockSelection>("""{"x":2,"y":64,"z":-3,"dimensionId":-1}""")

        assertEquals(BlockSelection(2, 64, -3, "-1"), block)
    }

    @Test
    fun roundTripsNamespacedModernDimension() {
        val block = BlockSelection(2, 64, -3, "minecraft:the_nether")

        assertEquals(block, Json.decodeFromString<BlockSelection>(Json.encodeToString(block)))
    }
}
