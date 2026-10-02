package io.github.fopwoc.mods.palimpsest.tree

import io.github.fopwoc.mods.palimpsest.storage.StorageWrites
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StorageWritesTest {
    @Test
    fun historyAccountsForHeadersTrailersAndManifestsAndSkipsUnchangedCommits() {
        val directory = Files.createTempDirectory("history-writes-")
        try {
            val writes = StorageWrites()
            MapTree(directory, 1, sealBytes = 1024, writes = writes).use { tree ->
                val key = TileKey(0, 0)
                tree.commit(1, mapOf(key to TileRecord.solid(1, 7)))
                val before = writes.snapshot().totalBytes
                tree.commit(2, mapOf(key to TileRecord.solid(2, 7)))
                assertEquals(before, writes.snapshot().totalBytes)
                assertEquals(0, writes.snapshot().flushes)
                tree.seal()
                assertEquals(1, writes.snapshot().flushes)
                val retained =
                    Files.list(directory).use { files ->
                        files
                            .filter {
                                it.fileName.toString().endsWith(".pseg") ||
                                    it.fileName.toString().startsWith("segments.")
                            }
                            .mapToLong(Files::size)
                            .sum()
                    }
                assertEquals(retained, writes.snapshot().totalBytes)
                assertTrue(writes.snapshot().bytes.getValue(StorageWrites.Kind.MANIFEST) > 0)
            }
        } finally {
            Files.walk(directory).use {
                it.sorted(Comparator.reverseOrder()).forEach(Files::delete)
            }
        }
    }

    @Test
    fun vocabularyCountsReplacementsInBytesAndCleanSavesWriteNothing() {
        val directory = Files.createTempDirectory("vocabulary-writes-")
        try {
            val blocks = BlockTable(directory, 1)
            blocks.idOf("test:stone", 0x123456, 0)
            blocks.saveIfDirty()
            val file = directory.resolve(BlockDictionary.fileName(1))
            val first = Files.size(file)
            assertEquals(first, blocks.writes.snapshot().totalBytes)
            blocks.saveIfDirty()
            assertEquals(first, blocks.writes.snapshot().totalBytes)
            blocks.idOf("test:é", 0x654321, 0)
            blocks.saveIfDirty()
            assertEquals(first + Files.size(file), blocks.writes.snapshot().totalBytes)
        } finally {
            Files.walk(directory).use {
                it.sorted(Comparator.reverseOrder()).forEach(Files::delete)
            }
        }
    }
}
