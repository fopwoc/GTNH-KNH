package io.github.fopwoc.mods.tabtps.protocol

import io.github.fopwoc.mods.framework.network.MessageCodec
import io.github.fopwoc.mods.framework.network.MessageReader
import io.github.fopwoc.mods.framework.network.MessageWriter

/** Server → client TPS snapshot. */
object TpsSnapshotCodec : MessageCodec<TpsSnapshot> {
    override fun encode(writer: MessageWriter, payload: TpsSnapshot) {
        writer.long(payload.requestId)
        writer.metrics(payload.server)
        writer.utf8(payload.currentDimensionId, MAX_DIMENSION_ID_LENGTH)

        val dimensions = payload.dimensions.take(MAX_DIMENSIONS_PER_SNAPSHOT)
        writer.short(dimensions.size)
        dimensions.forEach { dimension ->
            writer.utf8(dimension.dimensionId, MAX_DIMENSION_ID_LENGTH)
            writer.utf8(dimension.dimensionName, MAX_DIMENSION_NAME_LENGTH)
            writer.metrics(dimension.metrics)
        }
    }

    override fun decode(reader: MessageReader): TpsSnapshot {
        val requestId = reader.long()
        val server = reader.metrics()
        val currentDimensionId = reader.utf8(MAX_DIMENSION_ID_LENGTH)
        val dimensions =
            reader.list(MAX_DIMENSIONS_PER_SNAPSHOT, { unsignedShort() }) {
                DimensionTpsMetrics(
                    utf8(MAX_DIMENSION_ID_LENGTH),
                    utf8(MAX_DIMENSION_NAME_LENGTH),
                    metrics(),
                )
            }
        return TpsSnapshot(requestId, server, currentDimensionId, dimensions)
    }

    private fun MessageReader.metrics(): TpsMetrics = TpsMetrics(tps = double(), mspt = double())

    private fun MessageWriter.metrics(metrics: TpsMetrics) {
        double(metrics.tps)
        double(metrics.mspt)
    }
}
