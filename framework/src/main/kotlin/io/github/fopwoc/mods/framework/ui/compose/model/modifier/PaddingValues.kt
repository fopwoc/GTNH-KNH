package io.github.fopwoc.mods.framework.ui.compose.model.modifier

data class PaddingValues(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0
) {
    val horizontal: Int
        get() = left + right

    val vertical: Int
        get() = top + bottom

    companion object {
        val Zero = PaddingValues()
    }
}


