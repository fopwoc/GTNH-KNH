package io.github.fopwoc.mods.palimpsest.tree

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SegmentSetTest {
    private inline fun withDirectory(test: (Path) -> Unit) {
        val directory = Files.createTempDirectory("palimpsest-segments-")
        try {
            test(directory)
        } finally {
            Files.walk(directory).use { files ->
                files.sorted(Comparator.reverseOrder()).forEach(Files::delete)
            }
        }
    }

    private fun SegmentSet.writeTile(record: TileRecord, previous: Ref = Ref.NULL): Ref {
        val refs = refs(activeSegment)
        active.beginGroup()
        val offset =
            active.record(SegmentFormat.RecordType.TILE) {
                TileCodec.encodeFull(it, record, previous, refs)
            }
        active.commitGroup()
        return Ref(activeSegment, offset)
    }

    private fun SegmentSet.readTile(ref: Ref): TileCodec.Decoded {
        val record = reader(ref.segment).record(ref.offset)
        assertEquals(SegmentFormat.RecordType.TILE, record.type)
        return TileCodec.decode(record.source, refs(ref.segment))
    }

    @Test
    fun recordsAreReadableAcrossGroupsReopenAndSeal() = withDirectory { directory ->
        val first = TileRecord.solid(100, block = 1)
        val second = TileRecord.solid(200, block = 2)
        val firstRef: Ref
        val secondRef: Ref
        SegmentSet(directory, machineId = 0x1234abcd, sealBytes = 1 shl 20).use { segments ->
            firstRef = segments.writeTile(first)
            secondRef = segments.writeTile(second, previous = firstRef)
            assertEquals(first, segments.readTile(firstRef).record)
            assertEquals(firstRef, segments.readTile(secondRef).base)
        }
        SegmentSet(directory, machineId = 0x1234abcd).use { segments ->
            assertEquals(second, segments.readTile(secondRef).record)
            segments.seal()
            assertEquals(2, segments.size)
            assertEquals(second, segments.readTile(secondRef).record)
            val sealed =
                Files.list(directory).use {
                    it.filter { path ->
                            path.name.endsWith(".pseg") && !path.name.startsWith("active-")
                        }
                        .toList()
                }
            assertEquals(1, sealed.size)
            assertEquals(
                listOf(sealed.single().name),
                Files.readAllLines(directory.resolve("segments.1234abcd.txt")),
            )
        }
        SegmentSet(directory, machineId = 0x1234abcd).use { segments ->
            assertEquals(2, segments.size)
            assertEquals(first, segments.readTile(firstRef).record)
            assertEquals(second, segments.readTile(secondRef).record)
        }
    }

    @Test
    fun tornTailIsTruncatedOnReopen() = withDirectory { directory ->
        val ref: Ref
        SegmentSet(directory, machineId = 7).use { segments ->
            ref = segments.writeTile(TileRecord.solid(5, block = 3))
            segments.writeTile(TileRecord.solid(6, block = 4))
        }
        val active = directory.resolve("active-00000007.pseg")
        val bytes = Files.readAllBytes(active)
        Files.write(active, bytes.copyOf(bytes.size - 3), StandardOpenOption.TRUNCATE_EXISTING)
        SegmentSet(directory, machineId = 7).use { segments ->
            assertEquals(TileRecord.solid(5, block = 3), segments.readTile(ref).record)
            assertTrue(segments.active.size < bytes.size)
            val again = segments.writeTile(TileRecord.solid(9, block = 1))
            assertEquals(TileRecord.solid(9, block = 1), segments.readTile(again).record)
        }
    }

    @Test
    fun rootsSurviveSealAndReopen() = withDirectory { directory ->
        SegmentSet(directory, machineId = 1).use { segments ->
            val tile = segments.writeTile(TileRecord.solid(1, block = 1))
            segments.active.beginGroup()
            segments.active.root(
                RootRecord(1_000, 1, 0, 0, tile),
                segments.refs(segments.activeSegment),
            )
            segments.active.commitGroup()
            segments.seal()
            val later = segments.writeTile(TileRecord.solid(2, block = 2))
            segments.active.beginGroup()
            segments.active.root(
                RootRecord(2_000, 1, 0, 0, later),
                segments.refs(segments.activeSegment),
            )
            segments.active.commitGroup()
            assertEquals(listOf(1_000L, 2_000L), segments.roots().map { it.epoch }.sorted())
        }
        SegmentSet(directory, machineId = 1).use { segments ->
            val roots = segments.roots().sortedBy { it.epoch }
            assertEquals(listOf(1_000L, 2_000L), roots.map { it.epoch })
            assertEquals(TileRecord.solid(2, block = 2), segments.readTile(roots[1].ref).record)
        }
    }

    @Test
    fun refsIntoAnotherMachinesSegmentUseSlots() = withDirectory { directory ->
        val theirs: Ref
        SegmentSet(directory, machineId = 0x0a).use { segments ->
            theirs = segments.writeTile(TileRecord.solid(1, block = 1))
            segments.seal()
        }
        Files.delete(
            directory.resolve(MachineId.FILE_NAME).takeIf { Files.exists(it) }
                ?: directory.resolve("nothing").also { Files.createFile(it) }
        )
        SegmentSet(directory, machineId = 0x0b).use { segments ->
            val theirIndex = segments.indexOf(0x0a, 0)
            assertTrue(theirIndex >= 0)
            val mine =
                segments.writeTile(
                    TileRecord.solid(2, block = 2),
                    previous = Ref(theirIndex, theirs.offset),
                )
            assertEquals(Ref(theirIndex, theirs.offset), segments.readTile(mine).base)
            assertEquals(intArrayOf(0x0b, 0x0a).toList(), segments.active.slots.toList())
            segments.seal()
        }
        SegmentSet(directory, machineId = 0x0b).use { segments ->
            val mineIndex = segments.indexOf(0x0b, 0)
            val theirIndex = segments.indexOf(0x0a, 0)
            val sealed = segments.reader(mineIndex) as SegmentReader.Sealed
            val offset = sealed.trailer.roots.firstOrNull()?.offset
            assertEquals(null, offset)
            var found: Ref? = null
            sealed.scan { at, record ->
                if (record.type == SegmentFormat.RecordType.TILE) found = Ref(mineIndex, at)
            }
            assertEquals(
                Ref(theirIndex, theirs.offset),
                segments.readTile(checkNotNull(found)).base,
            )
        }
    }

    @Test
    fun orphanSealedSegmentIsAdopted() = withDirectory { directory ->
        SegmentSet(directory, machineId = 3).use { segments ->
            segments.writeTile(TileRecord.solid(1, block = 1))
            segments.seal()
        }
        Files.delete(directory.resolve("segments.00000003.txt"))
        SegmentSet(directory, machineId = 3).use { segments ->
            assertEquals(2, segments.size)
            assertEquals(1, Files.readAllLines(directory.resolve("segments.00000003.txt")).size)
        }
    }

    @Test
    fun damagedRecordIsReportedNotRead() = withDirectory { directory ->
        SegmentSet(directory, machineId = 4).use { segments ->
            assertFailsWith<CorruptTreeException> {
                segments.reader(segments.activeSegment).record(SegmentFormat.HEADER_BYTES + 1)
            }
        }
    }

    @Test
    fun oneProcessWritesAMachinesSegmentsAtATime() = withDirectory { directory ->
        SegmentSet(directory, machineId = 5).use {
            assertFailsWith<MapInUseException> { SegmentSet(directory, machineId = 5) }
            // Another machine's segments in the same directory are not locked out.
            SegmentSet(directory, machineId = 6).close()
        }
        SegmentSet(directory, machineId = 5).close()
    }
}
