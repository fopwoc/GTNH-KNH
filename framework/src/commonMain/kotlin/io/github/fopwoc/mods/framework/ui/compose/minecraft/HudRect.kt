package io.github.fopwoc.mods.framework.ui.compose.minecraft

data class HudRect(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
) {
    companion object {
        val Zero: HudRect =
            HudRect(
                left = 0,
                top = 0,
                width = 0,
                height = 0,
            )
    }
}
