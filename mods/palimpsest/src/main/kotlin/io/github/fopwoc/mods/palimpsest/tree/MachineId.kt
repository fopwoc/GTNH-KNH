package io.github.fopwoc.mods.palimpsest.tree

import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom

/**
 * A random 32-bit id per installation, kept in a local `.machine` file next to the map data and
 * never synced. Segments and block dictionaries are named by it, so two machines sharing a map
 * through git never write the same file.
 */
object MachineId {
    const val FILE_NAME = ".machine"

    fun load(directory: Path): Int {
        val file = directory.resolve(FILE_NAME)
        if (Files.isRegularFile(file)) {
            Files.readString(file).trim().toLongOrNull(16)?.let {
                return it.toInt()
            }
        }
        val id = SecureRandom().nextInt()
        Files.createDirectories(directory)
        Files.writeString(file, hex(id) + "\n")
        return id
    }

    fun hex(id: Int): String = "%08x".format(id)
}
