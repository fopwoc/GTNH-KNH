package io.github.fopwoc.mods.palimpsest.tree

/**
 * Spells refs the way a segment file must. The first varint says where: 0 is null, 1 is this very
 * segment, 2 is a backward distance from the containing record, and `slot + 3` names a machine in
 * this segment's slot table followed by that machine's segment ordinal. Reading resolves back to
 * runtime segment indices through the [SegmentSet]; a ref into a segment this machine has never
 * seen is corruption (a sync that dropped files).
 */
class SlotRefCoder(
    private val segments: SegmentSet,
    private val index: Int,
    private val reader: SegmentReader,
    private val origin: Int = 0,
) : RefCoder {
    override val baseEpoch: Long = reader.baseEpoch

    override fun prepare(ref: Ref) {
        if (!ref.isNull && ref.segment != index && reader is SegmentWriter) {
            reader.slot(segments.handle(ref.segment).machineId)
        }
    }

    override fun at(offset: Int): RefCoder = SlotRefCoder(segments, index, reader, offset)

    override fun write(sink: ByteSink, ref: Ref) {
        when {
            ref.isNull -> {
                sink.varint(NULL)
                return
            }
            ref.segment == index && origin > ref.offset && origin - ref.offset < ref.offset -> {
                sink.varint(BACKWARD)
                sink.varint(origin - ref.offset)
                return
            }
            ref.segment == index -> sink.varint(THIS)
            else -> {
                val target = segments.handle(ref.segment)
                val slot =
                    if (reader is SegmentWriter) reader.slot(target.machineId)
                    else reader.slots.indexOf(target.machineId)
                check(slot >= 0) {
                    "Machine ${MachineId.hex(target.machineId)} has no slot in this segment"
                }
                sink.varint(slot + FIRST_SLOT)
                sink.varint(target.ordinal)
            }
        }
        sink.varint(ref.offset)
    }

    override fun read(source: ByteSource): Ref {
        val where = source.varintInt()
        if (where == NULL) return Ref.NULL
        if (where == THIS) return Ref(index, source.varintInt())
        if (where == BACKWARD) {
            val distance = source.varintInt()
            if (distance <= 0 || origin - distance < SegmentFormat.HEADER_BYTES)
                throw CorruptTreeException("Invalid backward reference $distance from $origin")
            return Ref(index, origin - distance)
        }
        val slots = reader.slots
        val slot = where - FIRST_SLOT
        if (slot >= slots.size)
            throw CorruptTreeException("Slot $slot beyond the segment's ${slots.size}")
        val machine = slots[slot]
        val ordinal = source.varintInt()
        val offset = source.varintInt()
        val segment = segments.indexOf(machine, ordinal)
        if (segment < 0)
            throw CorruptTreeException(
                "Ref into unknown segment ${MachineId.hex(machine)}#$ordinal"
            )
        return Ref(segment, offset)
    }

    private companion object {
        const val NULL = 0
        const val THIS = 1
        const val BACKWARD = 2
        const val FIRST_SLOT = 3
    }
}
