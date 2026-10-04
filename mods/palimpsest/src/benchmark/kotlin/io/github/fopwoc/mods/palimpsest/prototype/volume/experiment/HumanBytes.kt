package io.github.fopwoc.mods.palimpsest.prototype.volume.experiment

fun human(bytes: Long): String =
    when {
        bytes >= 1 shl 30 -> "%.2f GB".format(bytes / 1073741824.0)
        bytes >= 1 shl 20 -> "%.2f MB".format(bytes / 1048576.0)
        bytes >= 1 shl 10 -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
