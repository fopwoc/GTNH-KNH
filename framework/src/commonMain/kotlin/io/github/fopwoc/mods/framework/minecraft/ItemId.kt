package io.github.fopwoc.mods.framework.minecraft

/** The registry name of an item, shared by every Minecraft target. */
@JvmInline
value class ItemId(val value: String) {
    init {
        require(IDENTIFIER.matches(value)) { "Invalid item id: $value" }
    }

    override fun toString(): String = value

    private companion object {
        val IDENTIFIER = Regex("[A-Za-z0-9_.-]+:[A-Za-z0-9_./-]+")
    }
}
