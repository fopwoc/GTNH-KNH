package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.ui.compose.foundation.Box
import io.github.fopwoc.mods.framework.ui.compose.foundation.Column
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.minecraft.ComposeHudOverlay
import io.github.fopwoc.mods.framework.ui.compose.minecraft.HudAnchor
import io.github.fopwoc.mods.framework.ui.compose.minecraft.HudRect
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.Alignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.model.style.TextStyle
import io.github.fopwoc.mods.framework.ui.compose.unit.uu
import io.github.fopwoc.mods.gtnhmeasurement.measurement.MeasurementSession
import net.minecraft.client.Minecraft
import net.minecraftforge.client.event.RenderGameOverlayEvent

@SideOnly(Side.CLIENT)
object MeasurementShortcutHudOverlay {
    private const val BOX_PADDING = 5
    private const val BOX_SPACING = 6
    private const val BORDER_WIDTH = 1

    private val overlayHost = ComposeHudOverlay {
        OverlayContent(overlayState)
    }

    private var overlayState by mutableStateOf(OverlayState())

    @SubscribeEvent
    fun onRender(event: RenderGameOverlayEvent.Post) {
        if (event.type != RenderGameOverlayEvent.ElementType.HOTBAR) {
            return
        }

        val minecraft = Minecraft.getMinecraft()
        if (!MeasurementSession.isActive || minecraft.currentScreen != null || minecraft.gameSettings.hideGUI) {
            hideOverlay()
            return
        }

        val fontRenderer = minecraft.fontRenderer ?: run {
            hideOverlay()
            return
        }
        val model = buildModel(minecraft) ?: run {
            hideOverlay()
            return
        }
        val lines = listOf(model.title) + model.hints.map(MeasurementShortcutHudHint::text)
        val boxWidth = (lines.maxOfOrNull(fontRenderer::getStringWidth) ?: 0) + BOX_PADDING * 2 + BORDER_WIDTH * 2
        val boxHeight = lines.size * (fontRenderer.FONT_HEIGHT + 1) + BOX_PADDING * 2 + BORDER_WIDTH * 2 - 1
        if (boxWidth <= 0 || boxHeight <= 0) {
            hideOverlay()
            return
        }

        overlayState = OverlayState(
            anchorBounds = overlayBounds(
                screenWidth = event.resolution.scaledWidth,
                screenHeight = event.resolution.scaledHeight,
                boxWidth = boxWidth,
                boxHeight = boxHeight
            ),
            width = boxWidth,
            height = boxHeight,
            model = model
        )
        overlayHost.render(
            client = minecraft,
            font = fontRenderer,
            width = event.resolution.scaledWidth,
            height = event.resolution.scaledHeight
        )
    }

    private fun buildModel(minecraft: Minecraft): MeasurementShortcutHudModel? {
        val world = minecraft.theWorld ?: return null
        val hoveredAnchor = MeasurementInteractionState.currentHoveredTarget?.block
        val hoveredMeasurementCount = hoveredAnchor
            ?.let(MeasurementSelectionState::measurementsContainingBlock)
            ?.size
            ?: 0
        val currentDimensionId = world.provider.dimensionId
        val selectedMeasurementCount = MeasurementSelectionState.selectedMeasurementsForDimension(currentDimensionId).size
        return MeasurementShortcutHudResolver.resolve(
            MeasurementShortcutHudContext(
                modeActive = MeasurementSession.isActive,
                selectedMode = MeasurementSession.mode,
                selectedMeasurementCount = selectedMeasurementCount,
                hoveredMeasurementCount = hoveredMeasurementCount,
                hasDraftCreation = MeasurementSelectionState.hasActiveDraftCreation,
                draftHasPreview = MeasurementSelectionState.draftSecond != null,
                clipboardOperation = MeasurementSelectionState.activeClipboard?.operation,
                pastePlacementActive = MeasurementSelectionState.isPastePlacementActive
            )
        )
    }

    private fun overlayBounds(screenWidth: Int, screenHeight: Int, boxWidth: Int, boxHeight: Int): HudRect {
        val hotbarTop = screenHeight - 22
        return HudRect(
            left = screenWidth / 2 - boxWidth / 2,
            top = hotbarTop - BOX_SPACING - boxHeight,
            width = boxWidth,
            height = boxHeight
        )
    }

    private fun hideOverlay() {
        overlayState = OverlayState()
        overlayHost.dispose()
    }

    @Composable
    private fun OverlayContent(state: OverlayState) {
        val model = state.model ?: return
        Box(modifier = Modifier.fillMaxSize()) {
            HudAnchor(bounds = state.anchorBounds, contentAlignment = Alignment.TopStart) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x7C000000))
                        .border(Color(0xAA4A4A56))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(BOX_PADDING.uu),
                        verticalArrangement = VerticalArrangement.spacedBy(1.uu)
                    ) {
                        Text(
                            text = model.title,
                            modifier = Modifier.fillMaxWidth(),
                            style = TextStyle(color = Color.rgb(red = 0xFF, green = 0xD5, blue = 0x4A))
                        )
                        model.hints.forEach { hint ->
                            Text(
                                text = hint.text,
                                modifier = Modifier.fillMaxWidth(),
                                style = TextStyle(color = hint.color)
                            )
                        }
                    }
                }
            }
        }
    }

    private data class OverlayState(
        val anchorBounds: HudRect = HudRect.Zero,
        val width: Int = 0,
        val height: Int = 0,
        val model: MeasurementShortcutHudModel? = null
    )
}

