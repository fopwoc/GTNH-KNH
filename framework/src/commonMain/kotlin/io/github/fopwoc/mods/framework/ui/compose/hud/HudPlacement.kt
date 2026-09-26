package io.github.fopwoc.mods.framework.ui.compose.hud

/** Where a [HudLayer] is drawn among the game's own HUD. */
enum class HudPlacement {
    /** Over everything the game draws, chat and the player list included. */
    TOP,

    /** Under the F3 debug screen, so its text stays readable over the layer. */
    BELOW_DEBUG,
}
