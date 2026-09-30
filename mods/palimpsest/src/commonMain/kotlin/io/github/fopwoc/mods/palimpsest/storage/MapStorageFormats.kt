package io.github.fopwoc.mods.palimpsest.storage

import io.github.fopwoc.mods.palimpsest.tree.CorruptTreeException
import io.github.fopwoc.mods.palimpsest.tree.CurrentRegionFormat
import io.github.fopwoc.mods.palimpsest.tree.SegmentFormat
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.name

/** Read-only format discovery, independent of opening, repairing or converting a map. */
object MapStorageFormats {
    enum class Kind {
        HISTORY,
        CURRENT_REGION,
        CURRENT_TILE,
    }

    data class Format(val kind: Kind, val version: Int) {
        val supported: Boolean
            get() =
                when (kind) {
                    Kind.HISTORY -> version == SegmentFormat.VERSION
                    Kind.CURRENT_REGION -> version == CurrentRegionFormat.VERSION
                    Kind.CURRENT_TILE -> false
                }
    }

    fun inspect(directory: Path): Set<Format> {
        if (!Files.exists(directory)) return emptySet()
        return Files.walk(directory).use { paths ->
            paths
                .filter(Files::isRegularFile)
                .map { path ->
                    when {
                        path.name.endsWith(".pseg") -> history(path)
                        path.name.endsWith(".preg") -> current(path)
                        path.name.endsWith(".tile") -> legacyTile(path)
                        else -> null
                    }
                }
                .filter { it != null }
                .map { checkNotNull(it) }
                .toList()
                .toSet()
        }
    }

    private fun history(path: Path): Format {
        val bytes = header(path, SegmentFormat.HEADER_BYTES)
        if (!bytes.copyOfRange(0, 8).contentEquals(SegmentFormat.MAGIC))
            throw CorruptTreeException("Invalid segment header in $path")
        val version = ByteBuffer.wrap(bytes, 8, 2).short.toInt() and 0xffff
        if (version == 0) throw CorruptTreeException("Invalid segment version in $path")
        return Format(Kind.HISTORY, version)
    }

    private fun current(path: Path): Format {
        val bytes = header(path, CurrentRegionFormat.HEADER_BYTES)
        if (!bytes.copyOfRange(0, 6).contentEquals(CurrentRegionFormat.MAGIC.copyOfRange(0, 6)))
            throw CorruptTreeException("Invalid current region header in $path")
        if (bytes[6].toInt() !in 48..57 || bytes[7].toInt() !in 48..57)
            throw CorruptTreeException("Invalid current region version in $path")
        val version = String(bytes, 6, 2, Charsets.US_ASCII).toIntOrNull()
        if (version == null || version <= 0)
            throw CorruptTreeException("Invalid current region version in $path")
        return Format(Kind.CURRENT_REGION, version)
    }

    private fun legacyTile(path: Path): Format {
        val bytes = header(path, 28)
        if (ByteBuffer.wrap(bytes).int != 0x504C5431)
            throw CorruptTreeException("Invalid legacy tile header in $path")
        return Format(Kind.CURRENT_TILE, 1)
    }

    private fun header(path: Path, size: Int): ByteArray =
        FileChannel.open(path, StandardOpenOption.READ).use { channel ->
            val buffer = ByteBuffer.allocate(size)
            while (buffer.hasRemaining()) {
                if (channel.read(buffer) <= 0)
                    throw CorruptTreeException("Truncated header in $path")
            }
            buffer.array()
        }
}
