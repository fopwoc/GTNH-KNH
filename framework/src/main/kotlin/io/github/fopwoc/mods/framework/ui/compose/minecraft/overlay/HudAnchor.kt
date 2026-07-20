package io.github.fopwoc.mods.framework.ui.compose.minecraft

import androidx.compose.runtime.Composable
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.ui.compose.foundation.Box
import io.github.fopwoc.mods.framework.ui.compose.foundation.BoxScope
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.Alignment
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.unit.uu

@SideOnly(Side.CLIENT)
data class HudRect(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int
) {
    companion object {
        val Zero: HudRect = HudRect(
            left = 0,
            top = 0,
            width = 0,
            height = 0
        )
    }
}

@SideOnly(Side.CLIENT)
data class HudPlacement(
    val alignment: Alignment = Alignment.TopStart,
    val offsetX: Int = 0,
    val offsetY: Int = 0
)

@Composable
@SideOnly(Side.CLIENT)
fun BoxScope.HudAnchor(
    bounds: HudRect,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .width(bounds.width.coerceAtLeast(0).uu)
            .height(bounds.height.coerceAtLeast(0).uu)
            .offset(x = bounds.left.uu, y = bounds.top.uu)
            .align(Alignment.TopStart),
        contentAlignment = contentAlignment,
        content = content
    )
}

@Composable
@SideOnly(Side.CLIENT)
@Deprecated("Use contentAlignment together with standard child modifiers like Modifier.offset for HUD placement.")
fun BoxScope.HudAnchor(
    bounds: HudRect,
    placement: HudPlacement,
    content: @Composable BoxScope.() -> Unit
) {
    HudAnchor(bounds = bounds, contentAlignment = placement.alignment) {
        Box(
            modifier = Modifier
                .offset(x = placement.offsetX.uu, y = placement.offsetY.uu)
                .align(placement.alignment),
            content = content
        )
    }
}

