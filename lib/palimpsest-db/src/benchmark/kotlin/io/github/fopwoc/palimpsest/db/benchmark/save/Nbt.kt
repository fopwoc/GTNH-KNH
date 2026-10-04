package io.github.fopwoc.palimpsest.db.benchmark.save

import java.nio.ByteBuffer

/**
 * Minimal reader of Minecraft's named binary tags into plain Kotlin values: numbers, [String],
 * [ByteArray], [IntArray], [LongArray], [List] and compounds as [Map]. Enough to read saves
 * offline.
 */
object Nbt {
    fun read(bytes: ByteArray): Map<String, Any?> {
        val buffer = ByteBuffer.wrap(bytes)
        val type = buffer.get().toInt()
        require(type == COMPOUND) { "Root tag is $type, not a compound" }
        string(buffer)
        @Suppress("UNCHECKED_CAST")
        return value(buffer, COMPOUND) as Map<String, Any?>
    }

    private fun value(buffer: ByteBuffer, type: Int): Any? =
        when (type) {
            1 -> buffer.get()
            2 -> buffer.short
            3 -> buffer.int
            4 -> buffer.long
            5 -> buffer.float
            6 -> buffer.double
            7 -> ByteArray(buffer.int).also(buffer::get)
            8 -> string(buffer)
            9 -> {
                val element = buffer.get().toInt()
                List(buffer.int) { value(buffer, element) }
            }
            COMPOUND ->
                buildMap {
                    while (true) {
                        val tag = buffer.get().toInt()
                        if (tag == 0) break
                        put(string(buffer), value(buffer, tag))
                    }
                }
            11 -> IntArray(buffer.int) { buffer.int }
            12 -> LongArray(buffer.int) { buffer.long }
            else -> error("Unknown tag type $type")
        }

    private fun string(buffer: ByteBuffer): String {
        val bytes = ByteArray(buffer.short.toInt() and 0xFFFF).also(buffer::get)
        return String(bytes, Charsets.UTF_8)
    }

    private const val COMPOUND = 10
}

@Suppress("UNCHECKED_CAST")
fun Map<String, Any?>.compound(name: String): Map<String, Any?> = this[name] as Map<String, Any?>

@Suppress("UNCHECKED_CAST")
fun Map<String, Any?>.compounds(name: String): List<Map<String, Any?>> =
    this[name] as? List<Map<String, Any?>> ?: emptyList()
