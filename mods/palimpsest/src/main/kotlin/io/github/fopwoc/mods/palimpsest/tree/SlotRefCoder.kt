package io.github.fopwoc.mods.palimpsest.tree

/**
 * Spells refs the way a segment file must: `slot ordinal offset`, where the slot names a machine
 * in this segment's slot table. Reading resolves back to runtime segment indices through the
 * [SegmentSet]; a ref into a segment this machine has never seen is corruption (a sync that
 * dropped files).
 */
class SlotRefCoder(private val segments: SegmentSet, private val reader: SegmentReader) : RefCoder {
    override fun write(sink: ByteSink, ref: Ref) {
        if (ref.isNull) {
            sink.varint(0)
            return
        }
        val target = segments.handle(ref.segment)
        val slot = if (reader is SegmentWriter) reader.slot(target.machineId) else reader.slots.indexOf(target.machineId)
        check(slot >= 0) { "Machine ${MachineId.hex(target.machineId)} has no slot in this segment" }
        sink.varint(slot + 1)
        sink.varint(target.ordinal)
        sink.varint(ref.offset)
    }

    override fun read(source: ByteSource): Ref {
        val slot = source.varintInt()
        if (slot == 0) return Ref.NULL
        val slots = reader.slots
        if (slot - 1 >= slots.size) throw CorruptTreeException("Slot $slot beyond the segment's ${slots.size}")
        val machine = slots[slot - 1]
        val ordinal = source.varintInt()
        val offset = source.varintInt()
        val index = segments.indexOf(machine, ordinal)
        if (index < 0) throw CorruptTreeException("Ref into unknown segment ${MachineId.hex(machine)}#$ordinal")
        return Ref(index, offset)
    }
}
