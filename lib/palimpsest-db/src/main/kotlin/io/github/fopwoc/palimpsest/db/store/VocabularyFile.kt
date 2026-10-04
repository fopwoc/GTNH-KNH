package io.github.fopwoc.palimpsest.db.store

import io.github.fopwoc.palimpsest.db.BlockId
import io.github.fopwoc.palimpsest.db.BlockVocabulary
import io.github.fopwoc.palimpsest.db.codec.ByteSink
import io.github.fopwoc.palimpsest.db.codec.ByteSource
import io.github.fopwoc.palimpsest.db.codec.string
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.util.concurrent.ConcurrentHashMap

/**
 * The world's block vocabulary and its append-only file: frames of new identities in id order. Ids
 * are handed out at once from any thread; the writer appends them before the commit that uses them.
 * Id 0 is air and is never stored.
 */
internal class VocabularyFile
private constructor(private val channel: FileChannel, stored: List<String>, length: Long) :
    BlockVocabulary, AutoCloseable {
    private val ids = ConcurrentHashMap<String, Int>()
    private val identities = ArrayList<String>()
    private var persisted: Int

    /** Bytes of the file that hold frames; the manifest commits this length on flush. */
    var length: Long = length
        private set

    init {
        identities += AIR
        identities += stored
        identities.forEachIndexed { id, identity -> ids[identity] = id }
        persisted = identities.size
    }

    override fun id(identity: String): BlockId =
        BlockId(
            ids[identity]
                ?: synchronized(this) {
                    ids.getOrPut(identity) { identities.size.also { identities += identity } }
                }
        )

    override fun identity(id: BlockId): String = synchronized(this) { identities[id.raw] }

    override val size: Int
        get() = synchronized(this) { identities.size }

    /**
     * Appends identities handed out since the last call; writer thread only. Returns bytes written.
     */
    fun persist(): Int {
        val fresh = synchronized(this) { identities.subList(persisted, identities.size).toList() }
        if (fresh.isEmpty()) return 0
        val sink = ByteSink(fresh.sumOf { it.length + 2 } + 4)
        sink.varint(fresh.size)
        fresh.forEach(sink::string)
        val written = Frames.write(channel, length, sink.toByteArray())
        length += written
        persisted += fresh.size
        return written
    }

    fun force() = channel.force(false)

    override fun close() = channel.close()

    companion object {
        const val AIR = "minecraft:air"

        /**
         * Reads the first [length] bytes and cuts anything after them: a crashed session's tail.
         */
        fun open(path: Path, length: Long): VocabularyFile {
            val channel = FileChannel.open(path, CREATE, READ, WRITE)
            if (channel.size() > length) channel.truncate(length)
            val stored = ArrayList<String>()
            var at = 0L
            while (at < length) {
                val payload = Frames.read(channel, at)
                val source = ByteSource(payload)
                repeat(source.varintInt()) { stored += source.string() }
                at += Frames.HEADER + payload.size
            }
            return VocabularyFile(channel, stored, length)
        }
    }
}
