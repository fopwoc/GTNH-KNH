package io.github.fopwoc.mods.palimpsest.tree

/** Bytes of a [NodeRecord]: varint max epoch, then per quarter a ref and, if present, a sample. */
object NodeCodec {
    fun encode(sink: ByteSink, node: NodeRecord, refs: RefCoder = RefCoder.Direct) {
        sink.varint(node.maxEpoch)
        for (quarter in 0 until NodeRecord.QUARTERS) {
            val child = node.child(quarter)
            refs.write(sink, child)
            if (!child.isNull) Sample.write(sink, node.sample(quarter))
        }
    }

    fun decode(source: ByteSource, refs: RefCoder = RefCoder.Direct): NodeRecord {
        val maxEpoch = source.varint()
        val children = LongArray(NodeRecord.QUARTERS)
        val samples = LongArray(NodeRecord.QUARTERS)
        for (quarter in 0 until NodeRecord.QUARTERS) {
            val child = refs.read(source)
            children[quarter] = child.packed
            samples[quarter] = if (child.isNull) Sample.NONE.packed else Sample.read(source).packed
        }
        return NodeRecord(children, samples, maxEpoch)
    }
}
