package io.github.fopwoc.mods.framework.ui.compose.model.style

data class ButtonStyle(
    val textColor: Int = 0xFFFFFF,
    val backgroundColor: Int = 0xFF3C3C46.toInt(),
    val hoverBackgroundColor: Int = 0xFF50505E.toInt(),
    val disabledBackgroundColor: Int = 0xFF29292F.toInt(),
    val borderColor: Int = 0xFF6F6F7C.toInt(),
    val disabledTextColor: Int = 0xFF8E8E96.toInt()
)


