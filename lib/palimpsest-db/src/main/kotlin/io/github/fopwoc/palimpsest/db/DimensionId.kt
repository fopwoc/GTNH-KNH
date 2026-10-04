package io.github.fopwoc.palimpsest.db

/** A dimension's stable key; it names a directory, so it is limited to path-safe characters. */
@JvmInline
value class DimensionId(val key: String) {
    init {
        require(key.isNotEmpty() && key.all { it in SAFE }) {
            "Dimension key is not path-safe: $key"
        }
    }

    private companion object {
        val SAFE = ('a'..'z').toSet() + ('0'..'9') + setOf('_', '-', '.')
    }
}
