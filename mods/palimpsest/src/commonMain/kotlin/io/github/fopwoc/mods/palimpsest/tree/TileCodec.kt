package io.github.fopwoc.mods.palimpsest.tree

/**
 * Bytes of one tile version. A **full** record carries every pixel of every channel; a **delta**
 * carries only the pixels that differ from the version it points to, so an edit costs its own size.
 * Reading a delta needs its base, so [decode] returns what it found and [Decoded.base] says what
 * else to fetch; the tree bounds chains by writing a full record every so often.
 *
 * Layout: kind byte; full: epoch (relative to the segment base), previous ref, four channels over
 * 256 values; delta: signed varint epoch minus base epoch, base ref, varint count, positions (a
 * byte each, or a 32-byte mask when more than 32 changed), a channel mask and only changed channels
 * over the covered values.
 */
object TileCodec {
    private const val FULL = 1
    private const val DELTA = 2
    private const val LINK = 3
    private const val MASK_THRESHOLD = 32
    private const val FULL_INDEX_BYTES = 12
    private const val MASK_BYTES = TileRecord.PIXELS / 8

    /**
     * A full record; a delta that still needs its [base] applied; or a link whose facts are those
     * of the full record at [base], at the link's own [epoch].
     */
    class Decoded(
        val record: TileRecord?,
        val base: Ref,
        val epochDelta: Long,
        val positions: IntArray,
        val values: Array<IntArray>,
        val isLink: Boolean = false,
        val epoch: Long = 0,
    ) {
        val isFull: Boolean
            get() = record != null

        /** The record this delta or link describes, given the base it named. */
        fun apply(base: TileRecord): TileRecord =
            if (isLink) base.withEpoch(epoch)
            else base.with(base.epoch + epochDelta, positions, values)

        /** The same decoding with block ids passed through [translate]. */
        fun mapBlocks(translate: (Int) -> Int): Decoded =
            when {
                record != null ->
                    Decoded(record.mapBlocks(translate), base, epochDelta, positions, values)
                isLink -> this
                else ->
                    Decoded(
                        null,
                        base,
                        epochDelta,
                        positions,
                        values.copyOf().also {
                            it[0] = IntArray(it[0].size) { index -> translate(it[0][index]) }
                        },
                    )
            }
    }

    fun encodeFull(
        sink: ByteSink,
        record: TileRecord,
        previous: Ref,
        refs: RefCoder = RefCoder.Direct,
    ) {
        sink.byte(FULL)
        refs.writeEpoch(sink, record.epoch)
        refs.write(sink, previous)
        for (channel in TileRecord.Channel.entries) {
            ChannelCodec.encode(sink, record.channel(channel), channel.bytes)
        }
    }

    /** Encodes [record] as a link to an identical full record already stored at [target]. */
    fun encodeLink(sink: ByteSink, record: TileRecord, previous: Ref, target: Ref, refs: RefCoder) {
        require(!target.isNull)
        sink.byte(LINK)
        refs.writeEpoch(sink, record.epoch)
        refs.write(sink, previous)
        refs.write(sink, target)
    }

    /**
     * Encodes changed pixels; false when unchanged. [preferFull] permits a smaller full record for
     * whole-tile or uniform edits, without speculative full coding for partial noisy edits.
     */
    fun encodeDelta(
        sink: ByteSink,
        record: TileRecord,
        base: TileRecord,
        baseRef: Ref,
        refs: RefCoder = RefCoder.Direct,
        preferFull: Boolean = false,
    ): Boolean {
        require(!baseRef.isNull)
        val positions = record.changedPositions(base)
        if (positions.isEmpty()) return false
        sink.byte(DELTA)
        sink.signed(record.epoch - base.epoch)
        refs.write(sink, baseRef)
        sink.varint(positions.size)
        if (positions.size > MASK_THRESHOLD) {
            val mask = ByteArray(MASK_BYTES)
            for (position in positions) mask[position ushr 3] =
                (mask[position ushr 3].toInt() or (1 shl (position and 7))).toByte()
            sink.bytes(mask)
        } else {
            for (position in positions) sink.byte(position)
        }
        val channelMask = record.changedChannels(base)
        sink.byte(channelMask)
        // Whole-tile deltas already encode 16x16 channels; reuse those bytes for the full
        // candidate.
        var full =
            if (preferFull && positions.size == TileRecord.PIXELS)
                ByteSink().apply {
                    byte(FULL)
                    refs.writeEpoch(this, record.epoch)
                    refs.write(this, baseRef)
                }
            else null
        for (channel in TileRecord.Channel.entries) {
            val changed = channelMask and (1 shl channel.ordinal) != 0
            if (!changed && full == null) continue
            val all = record.channel(channel)
            if (full != null) {
                val encoded = ByteSink()
                ChannelCodec.encode(encoded, all, channel.bytes)
                val bytes = encoded.toByteArray()
                full.bytes(bytes)
                if (changed) sink.bytes(bytes)
            } else {
                ChannelCodec.encode(
                    sink,
                    IntArray(positions.size) { all[positions[it]] },
                    channel.bytes,
                )
            }
        }
        // Uniform replacements are cheap to encode. Speculative full entropy coding for partial
        // noisy edits doubled mixed-workload write time, so keep those edits as deltas.
        if (full == null && preferFull && positions.size > MASK_THRESHOLD && record.isUniform()) {
            full = ByteSink().also { encodeFull(it, record, baseRef, refs) }
        }
        if (full != null && full.size + FULL_INDEX_BYTES < sink.size) {
            sink.clear()
            sink.bytes(full.toByteArray())
        }
        return true
    }

    fun decode(source: ByteSource, refs: RefCoder = RefCoder.Direct): Decoded =
        when (val kind = source.byte()) {
            FULL -> {
                val epoch = refs.readEpoch(source)
                val previous = refs.read(source)
                val channels =
                    TileRecord.Channel.entries.map {
                        ChannelCodec.decode(source, TileRecord.PIXELS, it.bytes)
                    }
                val record =
                    TileRecord(
                        epoch,
                        ShortArray(TileRecord.PIXELS) { channels[0][it].toShort() },
                        ByteArray(TileRecord.PIXELS) { channels[1][it].toByte() },
                        ByteArray(TileRecord.PIXELS) { channels[2][it].toByte() },
                        ShortArray(TileRecord.PIXELS) { channels[3][it].toShort() },
                    )
                Decoded(record, previous, 0, IntArray(0), emptyArray())
            }
            DELTA -> {
                val epochDelta = source.signed()
                val base = refs.read(source)
                if (base.isNull) throw CorruptTreeException("Delta without a base")
                val count = source.varintInt()
                if (count !in 1..TileRecord.PIXELS)
                    throw CorruptTreeException("Delta covers $count pixels")
                val positions =
                    if (count > MASK_THRESHOLD) {
                        val mask = source.bytes(MASK_BYTES)
                        val positions = IntArray(count)
                        var found = 0
                        for (position in 0 until TileRecord.PIXELS) {
                            if (mask[position ushr 3].toInt() and (1 shl (position and 7)) != 0) {
                                if (found == count)
                                    throw CorruptTreeException("Delta mask covers more than $count")
                                positions[found++] = position
                            }
                        }
                        if (found != count)
                            throw CorruptTreeException("Delta mask covers $found of $count")
                        positions
                    } else {
                        IntArray(count) { source.byte() }
                    }
                val channelMask = source.byte()
                if (channelMask !in 1..15)
                    throw CorruptTreeException("Invalid tile channel mask $channelMask")
                val values =
                    Array(TileRecord.Channel.entries.size) { channel ->
                        if (channelMask and (1 shl channel) == 0) IntArray(0)
                        else
                            ChannelCodec.decode(
                                source,
                                count,
                                TileRecord.Channel.entries[channel].bytes,
                            )
                    }
                Decoded(null, base, epochDelta, positions, values)
            }
            LINK -> {
                val epoch = refs.readEpoch(source)
                refs.read(source)
                val target = refs.read(source)
                if (target.isNull) throw CorruptTreeException("Link without a target")
                Decoded(null, target, 0, IntArray(0), emptyArray(), isLink = true, epoch = epoch)
            }
            else -> throw CorruptTreeException("Unknown tile record kind $kind")
        }

    /** Whether the record at the source's position is a full one; consumes the kind byte. */
    fun isFull(source: ByteSource): Boolean = source.byte() == FULL

    /** Only the previous-version link, without decoding pixels; for history walks. */
    fun previousOf(source: ByteSource, refs: RefCoder = RefCoder.Direct): Ref =
        when (val kind = source.byte()) {
            FULL -> {
                refs.readEpoch(source)
                refs.read(source)
            }
            DELTA -> {
                source.signed()
                refs.read(source)
            }
            LINK -> {
                refs.readEpoch(source)
                refs.read(source)
            }
            else -> throw CorruptTreeException("Unknown tile record kind $kind")
        }
}
