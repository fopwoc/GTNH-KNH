package io.github.fopwoc.palimpsest.db.codec

import java.io.IOException

/** Stored bytes that cannot be what the format says they are: a torn write or a damaged file. */
internal class CorruptDataException(message: String) : IOException(message)
