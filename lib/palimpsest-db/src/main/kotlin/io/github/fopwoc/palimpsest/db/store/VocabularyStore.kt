package io.github.fopwoc.palimpsest.db.store

import io.github.fopwoc.palimpsest.db.BlockId
import io.github.fopwoc.palimpsest.db.BlockVocabulary
import io.github.fopwoc.palimpsest.db.codec.ByteSink
import io.github.fopwoc.palimpsest.db.codec.ByteSource
import io.github.fopwoc.palimpsest.db.codec.string
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.util.concurrent.ConcurrentHashMap

/**
 * The world's block vocabulary: earlier sessions' files read up to their committed lengths, in
 * manifest order, and this session's own file for new identities, created on first need. Ids are
 * handed out at once from any thread; the writer appends them before the commit that uses them. Id
 * 0 is air and is never stored.
 */
internal class VocabularyStore
private constructor(
    private val earlier: List<Manifest.FileEntry>,
    private val own: Path,
    stored: List<String>,
) : BlockVocabulary, AutoCloseable {
    private val ids = ConcurrentHashMap<String, Int>()
    private val identities = ArrayList<String>()
    private var persisted: Int
    private var channel: FileChannel? = null
    private var length = 0L

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
        val channel =
            channel
                ?: FileChannel.open(
                        own.also { Files.createDirectories(it.parent) },
                        CREATE_NEW,
                        READ,
                        WRITE,
                    )
                    .also { channel = it }
        val written = Frames.write(channel, length, sink.toByteArray())
        length += written
        persisted += fresh.size
        return written
    }

    fun force() {
        channel?.force(false)
    }

    /** The manifest's view: earlier files, then this session's own once it exists. */
    fun entries(seal: Boolean): List<Manifest.FileEntry> =
        earlier +
            listOfNotNull(
                channel?.let { Manifest.FileEntry(own.fileName.toString(), length, seal) }
            )

    override fun close() {
        channel?.close()
    }

    companion object {
        const val AIR = "minecraft:air"

        fun open(
            layout: WorldLayout,
            earlier: List<Manifest.FileEntry>,
            session: Manifest.Session,
        ): VocabularyStore {
            val stored = ArrayList<String>()
            for (file in earlier) {
                FileChannel.open(layout.vocabulary(file.name), READ).use { channel ->
                    Frames.readAll(channel, until = file.length) { payload ->
                        val source = ByteSource(payload)
                        repeat(source.varintInt()) { stored += source.string() }
                    }
                }
            }
            return VocabularyStore(earlier, layout.vocabulary("$session.voc"), stored)
        }
    }
}
