package io.github.fopwoc.mods.palimpsest.storage

import io.github.fopwoc.mods.palimpsest.tree.CorruptTreeException
import io.github.fopwoc.mods.palimpsest.tree.LatestTileStore
import io.github.fopwoc.mods.palimpsest.tree.MapInUseException
import io.github.fopwoc.mods.palimpsest.tree.MapTree
import io.github.fopwoc.mods.palimpsest.tree.SegmentFormat
import io.github.fopwoc.mods.palimpsest.tree.TileKey
import io.github.fopwoc.mods.palimpsest.tree.TileRecord
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IncompatibleMapArchiveTest {
    private inline fun withDimension(test: (Path) -> Unit) {
        val dimension = Files.createTempDirectory("map-archive-")
        try {
            test(dimension)
        } finally {
            Files.walk(dimension).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::delete)
            }
        }
    }

    private fun segment(slice: Path, name: String, version: Int): Path {
        Files.createDirectories(slice)
        val header = SegmentFormat.header(1, 0, 0)
        header[8] = (version ushr 8).toByte()
        header[9] = version.toByte()
        return Files.write(slice.resolve(name), header)
    }

    @Test
    fun compatibleAndEmptySlicesRemainUntouched() = withDimension { dimension ->
        val slice = dimension.resolve("y255")
        assertNull(IncompatibleMapArchive.prepare(slice))
        assertFalse(Files.exists(slice))
        val file = segment(slice, "active-00000001.pseg", SegmentFormat.VERSION)
        val original = Files.readAllBytes(file)
        LatestTileStore(slice.resolve("current"), 1).use { current ->
            current.write(10, mapOf(TileKey(0, 0) to TileRecord.solid(10, 4)))
        }
        assertEquals(
            setOf(MapStorageFormats.Kind.HISTORY, MapStorageFormats.Kind.CURRENT_REGION),
            MapStorageFormats.inspect(slice).map { it.kind }.toSet(),
        )
        assertNull(IncompatibleMapArchive.prepare(slice))
        assertContentEquals(original, Files.readAllBytes(file))
        assertFalse(Files.exists(dimension.resolve("incompatible")))
    }

    @Test
    fun archivePreservesEntireSliceAndVocabularyWithoutMovingWaypointsOrOtherSlices() =
        withDimension { dimension ->
            val slice = dimension.resolve("y255")
            val old = segment(slice, "old.pseg", 3)
            val current = segment(slice, "new.pseg", SegmentFormat.VERSION)
            val oldBytes = Files.readAllBytes(old)
            val currentBytes = Files.readAllBytes(current)
            Files.writeString(slice.resolve("segments.00000001.txt"), "old.pseg\nnew.pseg\n")
            Files.writeString(slice.resolve("created"), "100")
            Files.writeString(dimension.resolve("blocks.00000001.tsv"), "own vocabulary")
            Files.writeString(dimension.resolve("blocks.00000002.tsv"), "foreign vocabulary")
            Files.writeString(dimension.resolve(".machine"), "00000001")
            Files.createDirectories(dimension.resolve("waypoints"))
            Files.writeString(dimension.resolve("waypoints/home"), "home")
            segment(dimension.resolve("y64"), "cave.pseg", SegmentFormat.VERSION)
            val archive = assertNotNull(IncompatibleMapArchive.prepare(slice))
            assertEquals(dimension.resolve("incompatible"), archive.parent)
            assertFalse(Files.exists(slice))
            assertContentEquals(oldBytes, Files.readAllBytes(archive.resolve("y255/old.pseg")))
            assertContentEquals(currentBytes, Files.readAllBytes(archive.resolve("y255/new.pseg")))
            assertEquals("100", Files.readString(archive.resolve("y255/created")))
            assertEquals("own vocabulary", Files.readString(archive.resolve("blocks.00000001.tsv")))
            assertEquals(
                "foreign vocabulary",
                Files.readString(archive.resolve("blocks.00000002.tsv")),
            )
            assertEquals("00000001", Files.readString(archive.resolve(".machine")))
            assertEquals("home", Files.readString(dimension.resolve("waypoints/home")))
            assertTrue(Files.exists(dimension.resolve("y64/cave.pseg")))
            MapTree(slice, 1).use { tree -> assertEquals(-1, tree.latestEpoch) }
            assertNull(IncompatibleMapArchive.prepare(slice))
        }

    @Test
    fun legacyCurrentTilesAndFutureRegionsAreRecognized() = withDimension { dimension ->
        val slice = dimension.resolve("y255")
        val current = Files.createDirectories(slice.resolve("current/r0_0"))
        val tile = ByteArray(1564)
        java.nio.ByteBuffer.wrap(tile).putInt(0x504C5431)
        Files.write(current.resolve("0_0.tile"), tile)
        Files.write(slice.resolve("current/r1_0.preg"), "PALCUR03".toByteArray())
        val formats = MapStorageFormats.inspect(slice)
        assertEquals(
            setOf(MapStorageFormats.Kind.CURRENT_TILE, MapStorageFormats.Kind.CURRENT_REGION),
            formats.map { it.kind }.toSet(),
        )
        val archive = assertNotNull(IncompatibleMapArchive.prepare(slice))
        assertContentEquals(tile, Files.readAllBytes(archive.resolve("y255/current/r0_0/0_0.tile")))
        assertTrue(Files.readString(archive.resolve("formats")).contains("current_region=3"))
    }

    @Test
    fun archivesNeverOverwriteEarlierVersionsOrVocabularySnapshots() = withDimension { dimension ->
        val slice = dimension.resolve("y255")
        val dictionary = dimension.resolve("blocks.00000001.tsv")
        Files.writeString(dictionary, "first")
        segment(slice, "old.pseg", 3)
        val first = assertNotNull(IncompatibleMapArchive.prepare(slice))
        Files.writeString(dictionary, "second")
        segment(slice, "old.pseg", 3)
        val second = assertNotNull(IncompatibleMapArchive.prepare(slice))
        assertNotEquals(first, second)
        assertEquals("first", Files.readString(first.resolve("blocks.00000001.tsv")))
        assertEquals("second", Files.readString(second.resolve("blocks.00000001.tsv")))
    }

    @Test
    fun malformedHeaderDoesNotArchiveAnythingEvenWithAnOldSegment() = withDimension { dimension ->
        val slice = dimension.resolve("y255")
        segment(slice, "old.pseg", 3)
        val damaged = Files.write(slice.resolve("bad.pseg"), byteArrayOf(1, 2, 3))
        assertFailsWith<CorruptTreeException> { IncompatibleMapArchive.prepare(slice) }
        assertContentEquals(byteArrayOf(1, 2, 3), Files.readAllBytes(damaged))
        assertTrue(Files.exists(slice.resolve("old.pseg")))
        assertFalse(Files.exists(dimension.resolve("incompatible")))
    }

    @Test
    fun openWriterPreventsArchival() = withDimension { dimension ->
        val slice = dimension.resolve("y255")
        MapTree(slice, 1).use {
            segment(slice, "old.pseg", 3)
            assertFailsWith<MapInUseException> { IncompatibleMapArchive.prepare(slice) }
            assertTrue(Files.exists(slice.resolve("old.pseg")))
            assertFalse(Files.exists(dimension.resolve("incompatible")))
        }
        assertNotNull(IncompatibleMapArchive.prepare(slice))
    }
}
