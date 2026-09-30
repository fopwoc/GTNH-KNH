package io.github.fopwoc.mods.palimpsest.tree

/** Header identity shared by region storage and read-only format discovery. */
internal object CurrentRegionFormat {
    const val VERSION = 2
    const val HEADER_BYTES = 8
    val MAGIC: ByteArray =
        ("PALCUR" + VERSION.toString().padStart(2, '0')).toByteArray(Charsets.US_ASCII)
}
