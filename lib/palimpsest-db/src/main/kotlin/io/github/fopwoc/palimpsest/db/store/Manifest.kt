package io.github.fopwoc.palimpsest.db.store

import io.github.fopwoc.palimpsest.db.Depth
import io.github.fopwoc.palimpsest.db.DimensionId
import io.github.fopwoc.palimpsest.db.DimensionMode
import io.github.fopwoc.palimpsest.db.Retention
import io.github.fopwoc.palimpsest.db.codec.ByteSink
import io.github.fopwoc.palimpsest.db.codec.ByteSource
import io.github.fopwoc.palimpsest.db.codec.CorruptDataException
import io.github.fopwoc.palimpsest.db.codec.string
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE

/**
 * One session's commit point: every file of the world's history and how many of its bytes count. A
 * session rewrites only its own manifest, by atomic rename, and names its [parent]; the world is
 * the manifest no other one names as parent. Anything past a committed length, or any file no
 * manifest names, is not part of the world.
 */
internal data class Manifest(
    val generation: Int,
    val session: Session,
    val parent: Session?,
    val writtenAt: Long,
    val vocabulary: List<FileEntry>,
    val dimensions: List<DimensionEntry>,
) {
    /**
     * One run of the game that wrote to this world; [key] tells apart sessions with one [number].
     */
    data class Session(val number: Long, val key: String) {
        override fun toString(): String = "%06d-%s".format(number, key)

        companion object {
            fun parse(text: String): Session? {
                val dash = text.indexOf('-')
                if (dash <= 0) return null
                return text.substring(0, dash).toLongOrNull()?.let {
                    Session(it, text.substring(dash + 1))
                }
            }
        }
    }

    data class DimensionEntry(
        val id: DimensionId,
        val mode: DimensionMode,
        val segments: List<FileEntry>,
    )

    /**
     * [sealed] files never change again; an unsealed one belongs to a session that crashed or still
     * runs.
     */
    data class FileEntry(val name: String, val length: Long, val sealed: Boolean)

    fun write(layout: WorldLayout) {
        val sink = ByteSink(256)
        sink.fixed(MAGIC.toLong(), 4)
        sink.varint(generation)
        sink.string(session.toString())
        sink.string(parent?.toString().orEmpty())
        sink.varint(writtenAt)
        files(sink, vocabulary)
        sink.varint(dimensions.size)
        for (dimension in dimensions) {
            sink.string(dimension.id.key)
            sink.byte(dimension.mode.depth.ordinal)
            sink.byte(dimension.mode.time.ordinal)
            files(sink, dimension.segments)
        }
        Files.createDirectories(layout.manifests)
        val target = layout.manifest(session)
        val draft = target.resolveSibling("${target.fileName}.draft")
        FileChannel.open(draft, CREATE, WRITE, TRUNCATE_EXISTING).use { channel ->
            Frames.write(channel, 0, sink.toByteArray())
            channel.force(true)
        }
        Files.move(draft, target, ATOMIC_MOVE, REPLACE_EXISTING)
    }

    /** Every file the manifest names with its committed length. */
    fun files(layout: WorldLayout): List<Pair<Path, Long>> =
        vocabulary.map { layout.vocabulary(it.name) to it.length } +
            dimensions.flatMap { dimension ->
                dimension.segments.map { layout.segment(dimension.id, it.name) to it.length }
            }

    private fun files(sink: ByteSink, files: List<FileEntry>) {
        sink.varint(files.size)
        for (file in files) {
            sink.string(file.name)
            sink.varint(file.length)
            sink.byte(if (file.sealed) 1 else 0)
        }
    }

    companion object {
        /** "PLMP" */
        private const val MAGIC = 0x504C4D50

        /** The manifest at [path]; a damaged or half-synced one throws [CorruptDataException]. */
        fun read(path: Path): Manifest =
            FileChannel.open(path, READ).use { channel ->
                val source = ByteSource(Frames.read(channel, 0))
                if (source.fixed(4).toInt() != MAGIC)
                    throw CorruptDataException("Not a Palimpsest manifest: $path")
                val generation = source.varintInt()
                if (generation != io.github.fopwoc.palimpsest.db.PalimpsestDb.GENERATION)
                    return Manifest(generation, Session(0, ""), null, 0, emptyList(), emptyList())
                Manifest(
                    generation = generation,
                    session =
                        Session.parse(source.string())
                            ?: throw CorruptDataException("Bad session in $path"),
                    parent = Session.parse(source.string()),
                    writtenAt = source.varint(),
                    vocabulary = files(source),
                    dimensions =
                        List(source.varintInt()) {
                            DimensionEntry(
                                id = DimensionId(source.string()),
                                mode =
                                    DimensionMode(
                                        Depth.entries[source.byte()],
                                        Retention.entries[source.byte()],
                                    ),
                                segments = files(source),
                            )
                        },
                )
            }

        private fun files(source: ByteSource): List<FileEntry> =
            List(source.varintInt()) {
                FileEntry(source.string(), source.varint(), source.byte() == 1)
            }
    }
}
