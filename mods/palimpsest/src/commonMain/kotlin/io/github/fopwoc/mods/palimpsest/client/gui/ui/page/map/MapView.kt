package io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import io.github.fopwoc.mods.framework.minecraft.ItemId
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuCanvasState
import io.github.fopwoc.mods.framework.ui.compose.component.vanilla.Button
import io.github.fopwoc.mods.framework.ui.compose.foundation.Box
import io.github.fopwoc.mods.framework.ui.compose.foundation.Column
import io.github.fopwoc.mods.framework.ui.compose.foundation.GpuCanvas
import io.github.fopwoc.mods.framework.ui.compose.foundation.Row
import io.github.fopwoc.mods.framework.ui.compose.foundation.Spacer
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.Alignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.unit.uu
import io.github.fopwoc.mods.palimpsest.client.claim.ClaimMark
import io.github.fopwoc.mods.palimpsest.client.claim.ClaimTooltips
import io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map.component.MapHistoryStrip
import io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map.component.MapProspectingLayer
import io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map.component.MapWaypointLayer
import io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map.component.ViewControls
import io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map.component.ViewControlsLayout
import io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map.component.WaypointEditor
import io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map.component.WaypointList
import io.github.fopwoc.mods.palimpsest.client.prospecting.ProspectingLayers
import io.github.fopwoc.mods.palimpsest.client.prospecting.ProspectingMark
import io.github.fopwoc.mods.palimpsest.map.MapTime
import io.github.fopwoc.mods.palimpsest.render.iso.MarkProjection
import io.github.fopwoc.mods.palimpsest.waypoint.Waypoint
import java.util.UUID

/** Height of the bar under the map; the canvas and the history strip fill everything above it. */
internal const val MAP_BAR_HEIGHT = 22

/** Width of the history strip along the canvas' right edge. */
internal const val MAP_HISTORY_WIDTH = 112

internal const val MAP_EDITOR_WIDTH = 196

/** How much of the canvas' right edge the open side panel covers. */
internal fun mapPanelWidth(historyOpen: Boolean, waypointPanelOpen: Boolean): Int =
    when {
        historyOpen -> MAP_HISTORY_WIDTH
        waypointPanelOpen -> MAP_EDITOR_WIDTH
        else -> 0
    }

internal fun mapCanvasHeight(screenHeight: Int): Int =
    (screenHeight - MAP_BAR_HEIGHT).coerceAtLeast(1)

/**
 * Full-screen map: the canvas, with entity dots and the player's arrow over it, fills everything
 * above a one-line bar; the history strip sits over the canvas' right edge while open.
 */
@Composable
internal fun MapView(
    model: MapModel,
    projection: MarkProjection,
    canvas: GpuCanvasState,
    claims: GpuCanvasState,
    dots: GpuCanvasState,
    marker: GpuCanvasState,
    cube: GpuCanvasState,
    waypoints: List<Waypoint>,
    prospectingMarks: List<ProspectingMark>,
    claimMarks: List<ClaimMark>,
    claimsEnabled: Boolean,
    claimsAvailable: Boolean,
    prospectingLayers: ProspectingLayers.Enabled,
    prospectingAvailable: Boolean,
    nodeTrackingAvailable: Boolean,
    waypointEditor: WaypointEditorModel?,
    waypointListOpen: Boolean,
    screenWidth: Int,
    screenHeight: Int,
    onOpenHistory: () -> Unit = {},
    onCloseHistory: () -> Unit = {},
    onSelectSnapshot: (Int) -> Unit = {},
    onHistoryScrolled: (Double) -> Unit = {},
    onAddWaypointAtPlayer: () -> Unit = {},
    onToggleWaypointList: () -> Unit = {},
    onToggleOre: () -> Unit = {},
    onToggleFluid: () -> Unit = {},
    onToggleNode: () -> Unit = {},
    onToggleClaims: () -> Unit = {},
    onSelectWaypoint: (UUID) -> Unit = {},
    onSaveWaypoint: (String, Int, Int, Int, ItemId, Boolean) -> Boolean = { _, _, _, _, _, _ ->
        false
    },
    onDeleteWaypoint: () -> Boolean = { false },
    onCloseWaypointEditor: () -> Unit = {},
    heldItemId: () -> ItemId? = { null },
    onToggle3d: () -> Unit = {},
    onTurn: (Int) -> Unit = {},
    onClose: () -> Unit = {},
) {
    val canvasHeight = mapCanvasHeight(screenHeight)
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.width(screenWidth.uu).height(canvasHeight.uu)) {
                GpuCanvas(
                    state = canvas,
                    modifier = Modifier.fillMaxSize().background(Color(0xFF0B0C12)),
                )
                GpuCanvas(state = claims, modifier = Modifier.fillMaxSize())
                GpuCanvas(state = dots, modifier = Modifier.fillMaxSize())
                GpuCanvas(state = marker, modifier = Modifier.fillMaxSize())
                val view = model.view
                if (model.time == MapTime.Live) {
                    MapProspectingLayer(projection, prospectingMarks, prospectingLayers)
                    if (claimsEnabled) ClaimTooltips(projection, claimMarks)
                    MapWaypointLayer(projection, waypoints)
                    if (prospectingAvailable || nodeTrackingAvailable || claimsAvailable) {
                        Row(
                            modifier = Modifier.align(Alignment.TopStart).padding(4.uu),
                            horizontalArrangement = HorizontalArrangement.spacedBy(4.uu),
                        ) {
                            if (prospectingAvailable) {
                                Button("Ores ${if (prospectingLayers.ore) "on" else "off"}") {
                                    onToggleOre()
                                }
                                Button("Fluids ${if (prospectingLayers.fluid) "on" else "off"}") {
                                    onToggleFluid()
                                }
                            }
                            if (nodeTrackingAvailable) {
                                Button("Nodes ${if (prospectingLayers.node) "on" else "off"}") {
                                    onToggleNode()
                                }
                            }
                            if (claimsAvailable) {
                                Button("Claims ${if (claimsEnabled) "on" else "off"}") {
                                    onToggleClaims()
                                }
                            }
                        }
                    }
                }
                run {
                    val panel =
                        mapPanelWidth(
                            model.history != null,
                            model.time == MapTime.Live &&
                                (waypointEditor != null || waypointListOpen),
                        )
                    ViewControls(
                        view = view,
                        cube = cube,
                        modifier =
                            Modifier.offset(
                                ViewControlsLayout.left(screenWidth, panel).uu,
                                ViewControlsLayout.TOP.uu,
                            ),
                        onTurn = onTurn,
                    )
                }
            }
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .height(MAP_BAR_HEIGHT.uu)
                        .background(Color(0xCC15161F))
                        .padding(horizontal = 4.uu),
                horizontalArrangement = HorizontalArrangement.spacedBy(4.uu),
                verticalAlignment = VerticalAlignment.CENTER,
            ) {
                val time = model.time
                Text(
                    "${model.centerX.toInt()}, ${model.centerZ.toInt()} · " +
                        zoomLabel(model.pixelsPerBlock)
                )
                Spacer(modifier = Modifier.weight(1f))
                Button(if (model.view.topDown) "3D" else "Top") { onToggle3d() }
                Text(if (time is MapTime.At) "At ${formatEpoch(time.observedAt)}" else "Live")
                Button("+ Waypoint", enabled = time == MapTime.Live) { onAddWaypointAtPlayer() }
                Button("Waypoints", enabled = time == MapTime.Live) { onToggleWaypointList() }
                Button("History", enabled = model.history == null) {
                    onOpenHistory()
                }
                Button("Close") { onClose() }
            }
        }
        model.history?.let { history ->
            MapHistoryStrip(
                model = history,
                width = MAP_HISTORY_WIDTH,
                height = canvasHeight,
                modifier = Modifier.align(Alignment.TopEnd),
                onSelect = onSelectSnapshot,
                onScrolled = onHistoryScrolled,
                onClose = onCloseHistory,
            )
        }
        if (model.time == MapTime.Live) {
            waypointEditor?.let { editor ->
                key(editor.id) {
                    WaypointEditor(
                        model = editor,
                        modifier =
                            Modifier.align(Alignment.TopEnd)
                                .width(MAP_EDITOR_WIDTH.uu)
                                .height(canvasHeight.uu),
                        heldItemId = heldItemId,
                        onSave = onSaveWaypoint,
                        onDelete = onDeleteWaypoint,
                        onClose = onCloseWaypointEditor,
                    )
                }
            }
            if (waypointEditor == null && waypointListOpen) {
                WaypointList(
                    waypoints = waypoints,
                    modifier =
                        Modifier.align(Alignment.TopEnd)
                            .width(MAP_EDITOR_WIDTH.uu)
                            .height(canvasHeight.uu),
                    onSelect = onSelectWaypoint,
                    onClose = onToggleWaypointList,
                )
            }
        }
    }
}

private fun zoomLabel(pixelsPerBlock: Double): String =
    if (pixelsPerBlock >= 1) "${pixelsPerBlock.toInt()} px/block"
    else "1 px = ${(1 / pixelsPerBlock).toInt()} blocks"
