package io.github.fopwoc.palimpsest.db

import io.github.fopwoc.palimpsest.db.TestWorld.Companion.OVERWORLD
import io.github.fopwoc.palimpsest.db.TestWorld.Companion.VOLUME
import io.github.fopwoc.palimpsest.db.TestWorld.Companion.await
import io.github.fopwoc.palimpsest.db.TestWorld.Companion.chunk
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SyncTest {
    private val origin = ChunkPos(0, 0)

    /** One session that commits [top] on the origin chunk at [tick]. */
    private fun TestWorld.play(at: Path, tick: Long, top: String) =
        open(at).use { db ->
            val stone = db.vocabulary.id("minecraft:stone:0")
            db.dimension(OVERWORLD, VOLUME)
                .commit(WorldTick(tick), listOf(chunk(origin, stone, db.vocabulary.id(top))))
                .await()
        }

    private fun TestWorld.topAt(at: Path, tick: Long): String =
        open(at).use { db ->
            db.vocabulary.identity(
                db.dimension(OVERWORLD, VOLUME)
                    .at(WorldTick(tick))
                    .volume(origin)
                    .result
                    .await()!!
                    .block(0, 64, 0)
            )
        }

    /** What a cloud folder does: every file of [from] that [to] lacks arrives in [to]. */
    private fun sync(from: Path, to: Path) {
        Files.walk(from).use { files ->
            files
                .filter(Files::isRegularFile)
                .filter { it.name != ".lock" }
                .forEach { file ->
                    val target = to.resolve(from.relativize(file))
                    if (!Files.exists(target)) {
                        Files.createDirectories(target.parent)
                        Files.copy(file, target)
                    }
                }
        }
    }

    @Test
    fun `a chain of sessions keeps one manifest`() {
        TestWorld().use { world ->
            world.play(world.world, 1, "a:grass")
            world.play(world.world, 2, "b:grass")
            world.play(world.world, 3, "c:grass")
            assertEquals(1, world.world.resolve("manifests").listDirectoryEntries("*.mf").size)
            assertEquals("b:grass", world.topAt(world.world, 2))
        }
    }

    @Test
    fun `two computers continuing apart must pick a branch`() {
        TestWorld().use { world ->
            val laptop = world.root.resolve("laptop")
            world.play(world.world, 1, "both:grass")
            world.world.toFile().copyRecursively(laptop.toFile())
            world.play(world.world, 2, "desktop:grass")
            world.play(laptop, 5, "laptop:grass")
            sync(laptop, world.world)

            val diverged =
                assertIs<OpenResult.Diverged>(PalimpsestDb.open(world.world, world.config))
            assertEquals(2, diverged.branches.size)
            val copy = world.root.resolve("copy")
            world.world.toFile().copyRecursively(copy.toFile())

            val laptopBranch =
                diverged.branches.single { branch ->
                    Files.exists(laptop.resolve("manifests/${branch.session}.mf"))
                }
            assertTrue(PalimpsestDb.keep(world.world, laptopBranch))
            world.config.cacheDirectory.toFile().deleteRecursively()
            assertEquals("laptop:grass", world.topAt(world.world, 5))
            assertTrue(world.world.resolve("abandoned").listDirectoryEntries().isNotEmpty())

            val desktopBranch = diverged.branches.single { it !== laptopBranch }
            assertTrue(PalimpsestDb.keep(copy, desktopBranch))
            world.config.cacheDirectory.toFile().deleteRecursively()
            assertEquals("desktop:grass", world.topAt(copy, 5))
        }
    }

    @Test
    fun `a half-arrived manifest waits for the sync`() {
        TestWorld().use { world ->
            world.play(world.world, 1, "a:grass")
            val manifest = world.world.resolve("manifests").listDirectoryEntries("*.mf").single()
            manifest.writeBytes(Files.readAllBytes(manifest).copyOf(10))
            assertIs<OpenResult.SyncIncomplete>(PalimpsestDb.open(world.world, world.config))
        }
    }

    @Test
    fun `files no manifest names do not block`() {
        TestWorld().use { world ->
            world.play(world.world, 1, "a:grass")
            val stray = world.world.resolve("dimensions/overworld/segments/000009-deadbeef.seg")
            Files.write(stray, ByteArray(100))
            assertEquals("a:grass", world.topAt(world.world, 1))
        }
    }
}
