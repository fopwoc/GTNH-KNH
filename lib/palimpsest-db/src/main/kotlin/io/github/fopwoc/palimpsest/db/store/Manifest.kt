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
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE

/**
 * The world folder's single commit point: which files hold history and how many of their bytes
 * count. Anything past a committed length, or any file it does not name, is not part of the world.
 * It is replaced whole by an atomic rename, so a reader sees either the old or the new one.
 */
internal data class Manifest(
    val generation: Int,
    val session: Session,
    val parent: Session?,
    val vocabularyLength: Long,
    val dimensions: List<DimensionEntry>,
) {
    /**
     * One run of the game that wrote to this world; [key] tells apart sessions with one [number].
     */
    data class Session(val number: Long, val key: String) {
        override fun toString(): String = "%06d-%s".format(number, key)
    }

    data class DimensionEntry(
        val id: DimensionId,
        val mode: DimensionMode,
        val segments: List<SegmentEntry>,
    )

    /**
     * [sealed] segments never change again; an unsealed one belongs to a session that crashed or
     * still runs.
     */
    data class SegmentEntry(val name: String, val length: Long, val sealed: Boolean)

    fun write(layout: WorldLayout) {
        val sink = ByteSink(256)
        sink.fixed(MAGIC.toLong(), 4)
        sink.varint(generation)
        session.encode(sink)
        sink.byte(if (parent == null) 0 else 1)
        parent?.encode(sink)
        sink.varint(vocabularyLength)
        sink.varint(dimensions.size)
        for (dimension in dimensions) {
            sink.string(dimension.id.key)
            sink.byte(dimension.mode.depth.ordinal)
            sink.byte(dimension.mode.time.ordinal)
            sink.varint(dimension.segments.size)
            for (segment in dimension.segments) {
                sink.string(segment.name)
                sink.varint(segment.length)
                sink.byte(if (segment.sealed) 1 else 0)
            }
        }
        FileChannel.open(layout.manifestDraft, CREATE, WRITE, TRUNCATE_EXISTING).use { channel ->
            Frames.write(channel, 0, sink.toByteArray())
            channel.force(true)
        }
        Files.move(layout.manifestDraft, layout.manifest, ATOMIC_MOVE, REPLACE_EXISTING)
    }

    companion object {
        /** "PLMP" */
        private const val MAGIC = 0x504C4D50

        /** The manifest's storage generation only, readable whatever the rest of the format is. */
        fun generation(layout: WorldLayout): Int =
            FileChannel.open(layout.manifest, READ).use { channel ->
                val source = ByteSource(Frames.read(channel, 0))
                if (source.fixed(4).toInt() != MAGIC)
                    throw CorruptDataException("Not a Palimpsest manifest")
                source.varintInt()
            }

        fun read(layout: WorldLayout): Manifest =
            FileChannel.open(layout.manifest, READ).use { channel ->
                val source = ByteSource(Frames.read(channel, 0))
                if (source.fixed(4).toInt() != MAGIC)
                    throw CorruptDataException("Not a Palimpsest manifest")
                Manifest(
                    generation = source.varintInt(),
                    session = decodeSession(source),
                    parent = if (source.byte() == 1) decodeSession(source) else null,
                    vocabularyLength = source.varint(),
                    dimensions =
                        List(source.varintInt()) {
                            DimensionEntry(
                                id = DimensionId(source.string()),
                                mode =
                                    DimensionMode(
                                        Depth.entries[source.byte()],
                                        Retention.entries[source.byte()],
                                    ),
                                segments =
                                    List(source.varintInt()) {
                                        SegmentEntry(
                                            source.string(),
                                            source.varint(),
                                            source.byte() == 1,
                                        )
                                    },
                            )
                        },
                )
            }

        private fun Session.encode(sink: ByteSink) {
            sink.varint(number)
            sink.string(key)
        }

        private fun decodeSession(source: ByteSource) = Session(source.varint(), source.string())
    }
}
