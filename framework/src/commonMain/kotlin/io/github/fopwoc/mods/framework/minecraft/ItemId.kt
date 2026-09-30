package io.github.fopwoc.mods.framework.minecraft

/** An item registry name and its legacy metadata variant (zero on modern targets). */
data class ItemId(val value: String, val metadata: Int = 0) {
    init {
        require(metadata >= 0) { "Invalid item metadata: $metadata" }
        require(IDENTIFIER.matches(value)) { "Invalid item id: $value" }
    }

    override fun toString(): String = value

    private companion object {
        val IDENTIFIER = Regex("[A-Za-z0-9_.-]+:[A-Za-z0-9_./-]+")
    }
}
