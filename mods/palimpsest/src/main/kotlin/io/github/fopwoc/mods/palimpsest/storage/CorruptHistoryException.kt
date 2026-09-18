package io.github.fopwoc.mods.palimpsest.storage

import java.io.IOException

/** Sealed data is structurally invalid, as opposed to a transient I/O failure. */
open class CorruptHistoryException(message: String) : IOException(message)

/** The local index sidecar is damaged; it is disposable and gets rebuilt from the segments. */
class IndexCacheException(message: String) : CorruptHistoryException(message)
