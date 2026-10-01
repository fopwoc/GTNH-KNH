package io.github.fopwoc.mods.palimpsest.tree

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ImportedAppearanceTest {
    @Test
    fun importedAppearancesKeepSeparateIdsAndSurviveVocabularyReopen() {
        val directory = Files.createTempDirectory("appearance-")
        try {
            val foreign = BlockTable(directory, 1)
            val red = foreign.idOf("same:block", 0xFF0000, 1)
            foreign.saveIfDirty()
            val own = BlockTable(directory, 2)
            val blue = own.idOf("same:block", 0x0000FF, 0)
            val imported = own.translate(1, red)
            assertNotEquals(blue, imported)
            assertEquals(0xFF0000, own.color(imported))
            assertEquals(1, own.tint(imported))
            assertEquals(blue, own.idOf("same:block", 0x00FF00, 2))
            own.saveIfDirty()
            val reopened = BlockTable(directory, 2)
            assertEquals(imported, reopened.translate(1, red))
            assertEquals(blue, reopened.idOf("same:block"))
            assertEquals(0xFF0000, reopened.color(imported))
            assertEquals(3, reopened.idOf("next:block", 0x00FF00, 0))
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
