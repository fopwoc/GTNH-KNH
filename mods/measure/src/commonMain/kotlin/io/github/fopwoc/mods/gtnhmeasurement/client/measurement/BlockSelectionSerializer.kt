package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

/** The shared IDs are strings; older GTNH JSON files stored the same IDs as numbers. */
object BlockSelectionSerializer : KSerializer<BlockSelection> {
    @Serializable
    private data class Wire(val x: Int, val y: Int, val z: Int, val dimensionId: String)

    override val descriptor: SerialDescriptor = Wire.serializer().descriptor

    override fun serialize(encoder: Encoder, value: BlockSelection) {
        encoder.encodeSerializableValue(
            Wire.serializer(),
            Wire(value.x, value.y, value.z, value.dimensionId),
        )
    }

    override fun deserialize(decoder: Decoder): BlockSelection {
        require(decoder is JsonDecoder) { "BlockSelection requires JSON" }
        val element = decoder.decodeJsonElement() as JsonObject
        val dimensionId = element["dimensionId"] as JsonPrimitive
        val normalized =
            if (dimensionId.isString) element
            else JsonObject(element + ("dimensionId" to JsonPrimitive(dimensionId.content)))
        val wire = decoder.json.decodeFromJsonElement<Wire>(normalized)
        return BlockSelection(wire.x, wire.y, wire.z, wire.dimensionId)
    }
}
