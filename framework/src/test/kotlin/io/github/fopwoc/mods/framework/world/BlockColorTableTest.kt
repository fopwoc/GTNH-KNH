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
            val file = directory.resolve(BlockColorTable.FILE_NAME)
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
            assertEquals(
                "minecraft:grass:0\tFF00FF00\nminecraft:stone:3\tFF808080\n",
                Files.readString(file),
            )

            // A botched merge from two machines: markers are skipped, the first color wins.
            Files.writeString(
                file,
                "minecraft:grass:0\tFF00FF00\n<<<<<<< ours\nmod:new:0\tFF111111\n=======\nmod:new:0\tFF222222\n>>>>>>> theirs\nminecraft:stone:3\tFF808080\n",
            )
            val merged = BlockColorTable.load(file)
            assertEquals(3, merged.size)
            assertEquals(0xFF111111.toInt(), merged.colorOf("mod:new", 0) { error("known") })
        } finally {
            Files.walk(directory).use { files ->
                files.sorted(Comparator.reverseOrder()).forEach(Files::delete)
            }
        }
    }
}
