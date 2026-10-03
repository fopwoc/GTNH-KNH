package io.github.fopwoc.mods.palimpsest.prototype.volume.experiment

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.fileSize
import kotlin.io.path.isRegularFile

fun directoryBytes(directory: Path): Long =
    Files.walk(directory).use { files ->
        files.filter { it.isRegularFile() }.mapToLong { it.fileSize() }.sum()
    }
