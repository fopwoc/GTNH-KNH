package io.github.fopwoc.mods.testgui.client.hud

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import io.github.fopwoc.mods.framework.client.ClientBackend
import io.github.fopwoc.mods.framework.ui.compose.foundation.Box
import io.github.fopwoc.mods.framework.ui.compose.foundation.BoxScope
import io.github.fopwoc.mods.framework.ui.compose.foundation.Column
import io.github.fopwoc.mods.framework.ui.compose.foundation.Row
import io.github.fopwoc.mods.framework.ui.compose.foundation.Spacer
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.hud.HudLayer
import io.github.fopwoc.mods.framework.ui.compose.minecraft.HudAnchor
import io.github.fopwoc.mods.framework.ui.compose.minecraft.HudRect
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.Alignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.model.style.TextStyle
import io.github.fopwoc.mods.framework.ui.compose.text.MinecraftColor
import io.github.fopwoc.mods.framework.ui.compose.text.styledText
import io.github.fopwoc.mods.framework.ui.compose.unit.uu

/**
 * Showcase for [HudLayer]: a tick-driven readout, a frame-clock animation and the four anchor
 * corners. Toggle with `/testgui hud`.
 */
object TestGuiHudOverlay : HudLayer("testgui:demo") {
    private const val BOX_WIDTH = 150
    private const val BOX_HEIGHT = 46

    var enabled: Boolean = false
        private set

    private var model by mutableStateOf(HudModel())

    override val visible: Boolean
        get() = enabled

    fun toggle(): Boolean {
        enabled = !enabled
        return enabled
    }

    override fun beforeFrame() {
        val position = ClientBackend.current.playerPosition ?: return
        model =
            HudModel(width, height, "%.1f / %.1f / %.1f".format(position.x, position.y, position.z))
    }

    @Composable override fun Content() = Content(model)

    private data class HudModel(
        val screenWidth: Int = 0,
        val screenHeight: Int = 0,
        val position: String = "",
    )

    @Composable
    private fun Content(model: HudModel) {
        // Frame-clock animation: advances once per rendered frame, no ticks involved.
        var frames by mutableIntStateOf(0)
        LaunchedEffect(Unit) {
            while (true) {
                withFrameNanos { frames++ }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            corner(model, Alignment.TopStart, "top-start") {
                Text("frames $frames")
                Text(model.position, style = TextStyle(color = Color(0xFFB8D7FF)))
            }
            corner(model, Alignment.TopEnd, "top-end") {
                ProgressBar(fraction = (frames % 120) / 120f)
            }
            corner(model, Alignment.BottomStart, "bottom-start") {
                Text(
                    styledText {
                        withColor(MinecraftColor.Gold) { withBold { +"Bold gold" } }
                        +" and "
                        withItalic { +"italic" }
                    }
                )
            }
        }
    }

    @Composable
    private fun BoxScope.corner(
        model: HudModel,
        alignment: Alignment,
        title: String,
        content: @Composable () -> Unit,
    ) {
        HudAnchor(
            bounds =
                HudRect(
                    left = 4,
                    top = 4,
                    width = model.screenWidth - 8,
                    height = model.screenHeight - 30,
                ),
            contentAlignment = alignment,
        ) {
            Column(
                modifier =
                    Modifier.width(BOX_WIDTH.uu)
                        .height(BOX_HEIGHT.uu)
                        .background(Color(0xA0101018))
                        .border(Color(0xFF4A4A56))
                        .padding(4.uu),
                verticalArrangement = VerticalArrangement.spacedBy(3.uu),
            ) {
                Text(title, style = TextStyle(color = Color(0xFFFFD54A)))
                content()
            }
        }
    }

    @Composable
    private fun ProgressBar(fraction: Float) {
        Row(modifier = Modifier.fillMaxWidth().height(6.uu).background(Color(0xFF202028))) {
            Box(
                modifier =
                    Modifier.weight(fraction.coerceAtLeast(0.001f))
                        .fillMaxHeight()
                        .background(Color(0xFF55FF55))
            )
            Spacer(modifier = Modifier.weight((1f - fraction).coerceAtLeast(0.001f)))
        }
    }
}
