package io.github.fopwoc.mods.framework.world

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlockColorTableTest {
    @Test
    fun firstSightingIsRecordedAndLaterPacksCannotChangeIt() {
        val directory = Files.createTempDirectory("block-colors-")
        try {
            val file = directory.resolve("blocks.bin")
            val table = BlockColorTable.load(file)
            assertEquals(0, table.size)
            assertEquals(
                0xFF00FF00.toInt(),
                table.colorOf("minecraft:grass", 0) { 0xFF00FF00.toInt() },
            )
            // A "new resource pack" offers a different color; the recorded one wins.
            assertEquals(
                0xFF00FF00.toInt(),
                table.colorOf("minecraft:grass", 0) { 0xFFFF00FF.toInt() },
            )
            assertEquals(
                0xFF808080.toInt(),
                table.colorOf("minecraft:stone", 3) { 0xFF808080.toInt() },
            )
            assertTrue(table.isDirty)
            table.saveIfDirty(file)
            assertFalse(table.isDirty)

            val reloaded = BlockColorTable.load(file)
            assertEquals(2, reloaded.size)
            assertEquals(
                0xFF00FF00.toInt(),
                reloaded.colorOf("minecraft:grass", 0) { error("known") },
            )
            assertEquals(
                0xFF808080.toInt(),
                reloaded.colorOf("minecraft:stone", 3) { error("known") },
            )
            assertFalse(reloaded.isDirty)
            reloaded.saveIfDirty(file)
        } finally {
            Files.walk(directory).use { files ->
                files.sorted(Comparator.reverseOrder()).forEach(Files::delete)
            }
        }
    }
}
