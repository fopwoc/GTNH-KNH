package io.github.fopwoc.palimpsest.inspect.history

import io.github.fopwoc.palimpsest.db.BlockId
import io.github.fopwoc.palimpsest.db.ChunkPos
import io.github.fopwoc.palimpsest.db.LogLevel
import io.github.fopwoc.palimpsest.inspect.TestHistory
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.exists
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.relativeTo
import kotlin.io.path.walk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalPathApi::class)
class HistoryCopyTest {
    private fun state(folder: Path): Map<String, Pair<Long, Long>> =
        folder
            .walk()
            .filter { it.isRegularFile() }
            .associate {
                it.relativeTo(folder).toString() to
                    (Files.size(it) to it.getLastModifiedTime().toMillis())
            }

    /** Copies of histories in the temporary directory, test histories aside. */
    private fun copies(): Set<String> =
        Path.of(System.getProperty("java.io.tmpdir"))
            .listDirectoryEntries("palimpsest-inspect-*")
            .map { it.name }
            .filterNot { it.startsWith("palimpsest-inspect-test-") }
            .toSet()

    @Test
    fun theOriginalIsOnlyReadEvenWhileAGameHoldsItsLock() {
        TestHistory().use { test ->
            test.write {
                val stone = id("minecraft:stone:0")
                commit(10, ChunkPos(0, 0) to { _, y, _ -> if (y < 4) stone else BlockId.AIR })
            }
            // A running game holds the lock; the copy leaves it behind and opens anyway.
            val lock =
                FileChannel.open(
                    test.history.resolve(".lock"),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                )
            lock.use { channel ->
                channel.lock()
                assertOnlyRead(test)
            }
            assertEquals(listOf("dim0"), HistoryCopy.dimensions(test.history).map { it.key })
        }
    }

    /** Copies [test]'s history, reads it, and checks nothing of the original changed. */
    private fun assertOnlyRead(test: TestHistory) {
        val before = state(test.history)
        val copiesBefore = copies()
        HistoryCopy.open(test.history, null, StderrLog(LogLevel.ERROR)).use { copy ->
            val dimension = copy.db.dimension(TestHistory.OVERWORLD)
            dimension.ready.get()
            assertEquals(1, dimension.timeline().size)
            assertEquals(copiesBefore.size + 1, copies().size)
        }
        assertEquals(before, state(test.history))
        assertEquals(copiesBefore, copies(), "the copy is deleted on close")
    }

    @Test
    fun aFolderThatIsNotAHistoryIsRefusedWithoutCopying() {
        TestHistory().use { test ->
            Files.createDirectories(test.history)
            val failure =
                assertFailsWith<IllegalArgumentException> {
                    HistoryCopy.open(test.history, null, StderrLog(LogLevel.ERROR))
                }
            assertTrue("not a Palimpsest history" in failure.message.orEmpty())
            assertFalse(test.history.resolve("manifests").exists())
        }
    }
}
