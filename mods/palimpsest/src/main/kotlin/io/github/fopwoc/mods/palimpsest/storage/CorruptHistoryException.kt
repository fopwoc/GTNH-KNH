package io.github.fopwoc.mods.palimpsest.storage

import java.io.IOException

/** Sealed data is structurally invalid, as opposed to a transient I/O failure. */
class CorruptHistoryException(message: String) : IOException(message)
