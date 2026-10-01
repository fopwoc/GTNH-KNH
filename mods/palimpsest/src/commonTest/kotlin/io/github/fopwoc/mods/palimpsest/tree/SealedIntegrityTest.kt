package io.github.fopwoc.mods.palimpsest.tree

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFailsWith

class SealedIntegrityTest {
    @Test
    fun validLookingPayloadAndHeaderDamageCannotHideBehindUnchangedTrailer() {
        for (header in listOf(false, true)) {
            val directory = Files.createTempDirectory("sealed-integrity-")
            try {
                MapTree(directory, 1).use {
                    it.commit(1, mapOf(TileKey(0, 0) to TileRecord.solid(1, 7, 64)))
                    it.seal()
                }
                val path =
                    Files.list(directory).use { paths ->
                        paths
                            .filter {
                                it.fileName.toString().endsWith(".pseg") &&
                                    !it.fileName.toString().startsWith("active-")
                            }
                            .findFirst()
                            .orElseThrow()
                    }
                val bytes = Files.readAllBytes(path)
                val at =
                    if (header) SegmentFormat.HEADER_BYTES - 1
                    else SegmentFormat.HEADER_BYTES + SegmentFormat.FRAME_BYTES + 8
                bytes[at] = (bytes[at].toInt() xor 1).toByte()
                Files.write(path, bytes)
                assertFailsWith<CorruptTreeException> { MapTree(directory, 1).close() }
                // Failed verification releases the writer lock so repaired data can reopen.
                bytes[at] = (bytes[at].toInt() xor 1).toByte()
                Files.write(path, bytes)
                MapTree(directory, 1).close()
            } finally {
                directory.toFile().deleteRecursively()
            }
        }
    }
}
