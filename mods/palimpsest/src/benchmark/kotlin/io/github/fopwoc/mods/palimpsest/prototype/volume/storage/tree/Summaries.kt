package io.github.fopwoc.mods.palimpsest.prototype.volume.storage.tree

import io.github.fopwoc.mods.palimpsest.tree.ByteSink
import io.github.fopwoc.mods.palimpsest.tree.ByteSource
import io.github.fopwoc.mods.palimpsest.tree.ChannelCodec
import io.github.fopwoc.mods.palimpsest.tree.TileRecord

/**
 * The 2D part of a chunk version: what it looks like from above (block, height, liquid depth) and
 * its column biomes, encoded with the production channel coder. This is the only 2D data the tree
 * keeps; identical summaries are stored once.
 */
class Summaries(private val pack: Pack, private val cache: RefCache<TileRecord>) {
    private val byHash = HashMap<Long, Long>()

    var bytes = 0L
        private set

    fun write(summary: TileRecord): Long {
        val hash = summary.factsHash()
        byHash[hash]?.let {
            return it
        }
        val sink = ByteSink(256)
        for (channel in TileRecord.Channel.entries) ChannelCodec.encode(
            sink,
            summary.channel(channel),
            channel.bytes,
        )
        val record = sink.toByteArray()
        bytes += record.size
        return pack.append(record).also {
            byHash[hash] = it
            cache.put(it, summary.withEpoch(0))
        }
    }

    /** The summary's facts; the epoch is the version's, supplied by the caller. */
    fun read(ref: Long): TileRecord =
        cache.get(ref) {
            val source = ByteSource(pack.read(ref))
            val channels =
                TileRecord.Channel.entries.map {
                    ChannelCodec.decode(source, TileRecord.PIXELS, it.bytes)
                }
            TileRecord.build(
                0,
                channels[0]::get,
                channels[1]::get,
                channels[2]::get,
                channels[3]::get,
            )
        }
}
