package io.github.fopwoc.mods.palimpsest.tree

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class BlockDictionaryTest {
    @Test
    fun aRealSightReplacesAGuessUnderTheSameId() {
        val table = BlockTable(Files.createTempDirectory("blocks").resolve("blocks.tsv"))
        val guessed = table.guess("minecraft:grass:0", 0x959595, 0)

        assertEquals(0, table.idOf("minecraft:grass:0"))
        assertEquals(guessed, table.idOf("minecraft:grass:0", 0x7F7F7F, 1))
        assertEquals(0x7F7F7F, table.color(guessed))
        assertEquals(1, table.tint(guessed))
        assertEquals(guessed, table.idOf("minecraft:grass:0"))
    }

    @Test
    fun aGuessNeverRepaintsWhatWasSeen() {
        val table = BlockTable(Files.createTempDirectory("blocks").resolve("blocks.tsv"))
        val seen = table.idOf("minecraft:leaves:0", 0x7F7F7F, 2)

        assertEquals(seen, table.guess("minecraft:leaves:0", 0x8D8D8D, 0))
        assertEquals(0x7F7F7F, table.color(seen))
        assertEquals(2, table.tint(seen))
    }

    @Test
    fun guessesStayGuessesAcrossSaves() {
        val file = Files.createTempDirectory("blocks").resolve("blocks.tsv")
        val first = BlockTable(file)
        val stone = first.idOf("minecraft:stone:0", 0x7D7D7D, 0)
        val grass = first.guess("minecraft:grass:0", 0x959595, 0)
        first.saveIfDirty()

        val reloaded = BlockTable(file)
        assertEquals(stone, reloaded.idOf("minecraft:stone:0"))
        assertEquals(0, reloaded.idOf("minecraft:grass:0"))
        assertEquals(grass, reloaded.idOf("minecraft:grass:0", 0x7F7F7F, 1))
        assertEquals(1, reloaded.tint(grass))
    }
}
