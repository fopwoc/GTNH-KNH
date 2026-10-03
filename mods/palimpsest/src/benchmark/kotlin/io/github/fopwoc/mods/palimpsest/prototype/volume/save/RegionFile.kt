package io.github.fopwoc.mods.palimpsest.prototype.volume.save

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream

/** One `.mca` region file: up to 32×32 chunks, each a compressed NBT compound. */
class RegionFile(private val path: Path) {
    class Chunk(val x: Int, val z: Int, val storedBytes: Int, val tag: Map<String, Any?>)

    private val regionX: Int
    private val regionZ: Int

    init {
        val (x, z) = path.fileName.toString().split('.').let { it[1].toInt() to it[2].toInt() }
        regionX = x
        regionZ = z
    }

    fun chunks(): Sequence<Chunk> = sequence {
        val data = Files.readAllBytes(path)
        if (data.size < HEADER) return@sequence
        val buffer = ByteBuffer.wrap(data)
        for (index in 0 until 1024) {
            val location = buffer.getInt(index * 4)
            val sector = location ushr 8
            if (sector == 0) continue
            val start = sector * SECTOR
            val length = buffer.getInt(start)
            val payload = ByteArrayInputStream(data, start + 5, length - 1)
            val stream =
                when (data[start + 4].toInt()) {
                    1 -> GZIPInputStream(payload)
                    2 -> InflaterInputStream(payload)
                    else -> continue
                }
            val tag = stream.use { Nbt.read(it.readBytes()) }
            yield(Chunk(regionX * 32 + index % 32, regionZ * 32 + index / 32, length, tag))
        }
    }

    private companion object {
        const val SECTOR = 4096
        const val HEADER = 2 * SECTOR
    }
}
