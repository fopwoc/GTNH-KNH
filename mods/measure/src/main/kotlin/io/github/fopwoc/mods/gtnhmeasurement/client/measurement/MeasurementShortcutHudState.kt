package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.gtnhmeasurement.measurement.MeasurementMode

internal data class MeasurementShortcutHudHint(
    val text: String,
    val color: Color
)

internal data class MeasurementShortcutHudModel(
    val title: String,
    val hints: List<MeasurementShortcutHudHint>
)

internal data class MeasurementShortcutHudContext(
    val modeActive: Boolean,
    val selectedMode: MeasurementMode,
    val selectedMeasurementCount: Int,
    val hoveredMeasurementCount: Int,
    val hasDraftCreation: Boolean,
    val draftHasPreview: Boolean,
    val clipboardOperation: ClipboardOperation?,
    val pastePlacementActive: Boolean
)

internal object MeasurementShortcutHudResolver {
    fun resolve(context: MeasurementShortcutHudContext): MeasurementShortcutHudModel? {
        if (!context.modeActive) {
            return null
        }

        if (context.pastePlacementActive) {
            val operationLabel = when (context.clipboardOperation) {
                ClipboardOperation.MOVE -> "Move placement"
                ClipboardOperation.RESIZE -> "Resize placement"
                ClipboardOperation.COPY -> "Copy placement"
                ClipboardOperation.CUT -> "Cut placement"
                null -> "Placement"
            }
            return MeasurementShortcutHudModel(
                title = operationLabel,
                hints = listOf(
                    hint("${MeasurementShortcutScheme.createClickLabel()} place preview", MeasurementShortcutHudPalette.Primary),
                    hint("${MeasurementShortcutScheme.selectionClickLabel()} pick another measurement", MeasurementShortcutHudPalette.Secondary),
                    hint("${MeasurementShortcutScheme.constraintModifierLabel()} constrain movement to one axis", MeasurementShortcutHudPalette.Accent),
                    hint("${MeasurementShortcutScheme.transformClickLabel()} transform from hovered anchor · ${MeasurementShortcutScheme.cancelLabel()} cancel", MeasurementShortcutHudPalette.Secondary)
                )
            )
        }

        if (context.hasDraftCreation) {
            val draftTitle = when {
                context.selectedMode == MeasurementMode.SPHERE && context.draftHasPreview -> "Draft sphere"
                context.selectedMode == MeasurementMode.SPHERE -> "Choose radius anchor"
                context.draftHasPreview -> "Draft measurement"
                else -> "Choose second anchor"
            }
            return MeasurementShortcutHudModel(
                title = draftTitle,
                hints = buildList {
                    add(hint("${MeasurementShortcutScheme.createClickLabel()} confirm measurement", MeasurementShortcutHudPalette.Primary))
                    add(hint("${MeasurementShortcutScheme.cancelLabel()} / ${MeasurementShortcutScheme.deleteLabel()} cancel draft", MeasurementShortcutHudPalette.Warning))
                    add(hint("${MeasurementShortcutScheme.targetedCreateClickLabel()} place against block face", MeasurementShortcutHudPalette.Secondary))
                    if (context.selectedMode == MeasurementMode.LINE) {
                        add(hint("${MeasurementShortcutScheme.constraintModifierLabel()} constrain line to 90°", MeasurementShortcutHudPalette.Accent))
                    }
                    if (context.selectedMode == MeasurementMode.SPHERE) {
                        add(hint("First anchor is center · second anchor sets radius", MeasurementShortcutHudPalette.Accent))
                    }
                }
            )
        }

        if (context.selectedMeasurementCount > 0) {
            return MeasurementShortcutHudModel(
                title = if (context.selectedMeasurementCount == 1) "1 measurement selected" else "${context.selectedMeasurementCount} measurements selected",
                hints = listOf(
                    hint(MeasurementShortcutScheme.editClipboardSummary(), MeasurementShortcutHudPalette.Primary),
                    hint(MeasurementShortcutScheme.historySummary(), MeasurementShortcutHudPalette.Secondary),
                    hint("${MeasurementShortcutScheme.transformClickLabel()} move selected · ${MeasurementShortcutScheme.deleteLabel()} delete", MeasurementShortcutHudPalette.Accent)
                )
            )
        }

        if (context.hoveredMeasurementCount > 0) {
            return MeasurementShortcutHudModel(
                title = if (context.hoveredMeasurementCount == 1) "Measurement under cursor" else "${context.hoveredMeasurementCount} measurements under cursor",
                hints = listOf(
                    hint("${MeasurementShortcutScheme.selectionClickLabel()} select", MeasurementShortcutHudPalette.Primary),
                    hint("${MeasurementShortcutScheme.multiSelectionClickLabel()} add all at anchor", MeasurementShortcutHudPalette.Secondary),
                    hint("${MeasurementShortcutScheme.transformClickLabel()} move or resize", MeasurementShortcutHudPalette.Accent)
                )
            )
        }

        return null
    }

    private fun hint(text: String, color: Color): MeasurementShortcutHudHint = MeasurementShortcutHudHint(text, color)
}

internal object MeasurementShortcutHudPalette {
    val Primary: Color = Color.rgb(red = 0xE6, green = 0xE6, blue = 0xE6)
    val Secondary: Color = Color.rgb(red = 0xB8, green = 0xD7, blue = 0xFF)
    val Accent: Color = Color.rgb(red = 0x9A, green = 0xE2, blue = 0x8D)
    val Warning: Color = Color.rgb(red = 0xFF, green = 0xC7, blue = 0x6E)
}

