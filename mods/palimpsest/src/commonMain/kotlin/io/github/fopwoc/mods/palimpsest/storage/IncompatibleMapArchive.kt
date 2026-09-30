package io.github.fopwoc.mods.palimpsest.storage

import io.github.fopwoc.mods.framework.log.logger
import io.github.fopwoc.mods.palimpsest.tree.BlockDictionary
import io.github.fopwoc.mods.palimpsest.tree.MachineId
import io.github.fopwoc.mods.palimpsest.tree.MapInUseException
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import kotlin.io.path.name

/** Preserves unsupported slices and their dictionaries before a fresh store is opened. */
object IncompatibleMapArchive {
    private val logger = logger<IncompatibleMapArchive>()

    @Synchronized
    fun prepare(slice: Path): Path? {
        val formats = MapStorageFormats.inspect(slice)
        if (formats.all { it.supported }) return null
        val channels = ArrayList<FileChannel>()
        try {
            Files.walk(slice).use { files ->
                for (path in
                    files
                        .filter { Files.isRegularFile(it) && it.name.endsWith(".lock") }
                        .sorted()
                        .toList()) {
                    val channel = FileChannel.open(path, StandardOpenOption.WRITE)
                    channels += channel
                    val acquired =
                        try {
                            channel.tryLock()
                        } catch (_: OverlappingFileLockException) {
                            null
                        }
                    if (acquired == null) {
                        throw MapInUseException(
                            "Cannot archive $slice while it is open in another game"
                        )
                    }
                }
            }
            val checked = MapStorageFormats.inspect(slice)
            if (checked.all { it.supported }) return null
            val dimension = slice.toAbsolutePath().parent
            val archives = dimension.resolve("incompatible")
            Files.createDirectories(archives)
            val archive = Files.createTempDirectory(archives, "${slice.fileName}-")
            Files.list(dimension).use { files ->
                for (file in files.filter { Files.isRegularFile(it) }.toList()) {
                    if (
                        file.name == MachineId.FILE_NAME ||
                            BlockDictionary.machineOf(file.name) != null
                    )
                        Files.copy(file, archive.resolve(file.fileName))
                }
            }
            Files.writeString(
                archive.resolve("formats"),
                checked.sortedWith(compareBy({ it.kind }, { it.version })).joinToString(
                    "\n",
                    postfix = "\n",
                ) {
                    "${it.kind.name.lowercase()}=${it.version}"
                },
            )
            Files.move(slice, archive.resolve(slice.fileName), StandardCopyOption.ATOMIC_MOVE)
            logger.warn("Archived incompatible map slice {} to {}: {}", slice, archive, checked)
            return archive
        } finally {
            channels.asReversed().forEach { it.close() }
        }
    }
}
