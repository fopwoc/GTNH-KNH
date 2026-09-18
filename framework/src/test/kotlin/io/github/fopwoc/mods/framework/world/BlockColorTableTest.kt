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
            assertEquals(137, table.entryOf("minecraft:grass", 0) { 137 })
            // A "new resource pack" offers a different entry; the recorded one wins.
            assertEquals(137, table.entryOf("minecraft:grass", 0) { 12 })
            assertEquals(42, table.entryOf("minecraft:stone", 3) { 42 })
            assertTrue(table.isDirty)
            table.saveIfDirty(file)
            assertFalse(table.isDirty)
            assertEquals("minecraft:grass:0\t137\nminecraft:stone:3\t42\n", Files.readString(file))

            val reloaded = BlockColorTable.load(file)
            assertEquals(2, reloaded.size)
            assertEquals(137, reloaded.entryOf("minecraft:grass", 0) { error("known") })
            assertEquals(42, reloaded.entryOf("minecraft:stone", 3) { error("known") })
            assertFalse(reloaded.isDirty)

            // A botched merge from two machines: markers are skipped, the first entry wins.
            Files.writeString(
                file,
                "minecraft:grass:0\t137\n<<<<<<< ours\nmod:new:0\t7\n=======\nmod:new:0\t9\n>>>>>>> theirs\nminecraft:stone:3\t42\n",
            )
            val merged = BlockColorTable.load(file)
            assertEquals(3, merged.size)
            assertEquals(7, merged.entryOf("mod:new", 0) { error("known") })
        } finally {
            Files.walk(directory).use { files ->
                files.sorted(Comparator.reverseOrder()).forEach(Files::delete)
            }
        }
    }
}
