package io.github.fopwoc.mods.palimpsest.tree

/**
 * Spells refs the way a segment file must. The first varint says where: 0 is null, 1 is this very
 * segment (the common case: the record written a moment ago), and `slot + 2` names a machine in
 * this segment's slot table followed by that machine's segment ordinal. Reading resolves back to
 * runtime segment indices through the [SegmentSet]; a ref into a segment this machine has never
 * seen is corruption (a sync that dropped files).
 */
class SlotRefCoder(
    private val segments: SegmentSet,
    private val index: Int,
    private val reader: SegmentReader,
) : RefCoder {
    override val baseEpoch: Long = reader.baseEpoch

    override fun write(sink: ByteSink, ref: Ref) {
        when {
            ref.isNull -> {
                sink.varint(NULL)
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
        const val FIRST_SLOT = 2
    }
}
