package io.github.fopwoc.mods.palimpsest.prototype.volume.model

/**
 * Every block of one chunk as vocabulary ids, plus its column biomes. A section is 16³ ids in
 * Minecraft's YZX order, or null when it holds only air. Instances are treated as immutable; [edit]
 * copies only the sections it touches.
 */
class ChunkVolume(sections: Array<IntArray?>, biomes: IntArray) {
    private val sections = sections.copyOf()
    private val biomes = biomes.copyOf()

    init {
        require(sections.size == SECTIONS && biomes.size == COLUMNS)
        require(sections.all { it == null || it.size == SECTION_BLOCKS })
    }

    fun section(index: Int): IntArray? = sections[index]

    fun block(x: Int, y: Int, z: Int): Int = sections[y shr 4]?.get(index(x, y, z)) ?: 0

    fun biome(x: Int, z: Int): Int = biomes[z * SIDE + x]

    fun biomes(): IntArray = biomes.copyOf()

    fun edit(block: Editor.() -> Unit): ChunkVolume = Editor().apply(block).build()

    inner class Editor internal constructor() {
        private val copied = BooleanArray(SECTIONS)
        private val next = sections.copyOf()
        private val nextBiomes = biomes.copyOf()

        fun block(x: Int, y: Int, z: Int): Int = next[y shr 4]?.get(index(x, y, z)) ?: 0

        fun set(x: Int, y: Int, z: Int, id: Int) {
            val section = y shr 4
            if (!copied[section]) {
                next[section] = next[section]?.copyOf() ?: IntArray(SECTION_BLOCKS)
                copied[section] = true
            }
            next[section]!![index(x, y, z)] = id
        }

        fun biome(x: Int, z: Int, biome: Int) {
            nextBiomes[z * SIDE + x] = biome
        }

        internal fun build(): ChunkVolume {
            for (section in 0 until SECTIONS) if (
                copied[section] && next[section]!!.all { it == 0 }
            )
                next[section] = null
            return ChunkVolume(next, nextBiomes)
        }
    }

    companion object {
        const val SIDE = 16
        const val SECTIONS = 16
        const val HEIGHT = SECTIONS * SIDE
        const val COLUMNS = SIDE * SIDE
        const val SECTION_BLOCKS = SIDE * SIDE * SIDE

        fun index(x: Int, y: Int, z: Int): Int = ((y and 15) shl 8) or (z shl 4) or x
    }
}
