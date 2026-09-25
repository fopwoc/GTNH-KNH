package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import io.github.fopwoc.mods.framework.client.ClientBackend
import io.github.fopwoc.mods.framework.ui.compose.input.Key
import io.github.fopwoc.mods.gtnhmeasurement.config.MeasurementConfig

object MeasurementShortcutScheme {
    val platformProfile: MeasurementPlatformProfile
        get() = MeasurementConfig.resolvePlatformProfile(System.getProperty("os.name"))

    fun selectionModifierLabel(): String =
        if (platformProfile == MeasurementPlatformProfile.MAC) "⇧" else "Shift"

    fun constraintModifierLabel(): String = selectionModifierLabel()

    fun targetModifierLabel(): String =
        if (platformProfile == MeasurementPlatformProfile.MAC) "⌃" else "Ctrl"

    fun transformModifierLabel(): String =
        if (platformProfile == MeasurementPlatformProfile.MAC) {
            "⌥"
        } else {
            "Alt"
        }

    fun editorModifierLabel(): String =
        if (platformProfile == MeasurementPlatformProfile.MAC) {
            "⌘"
        } else {
            "Ctrl"
        }

    const val CREATE_CLICK_LABEL: String = "MMB"

    fun targetedCreateClickLabel(): String = chord(targetModifierLabel(), CREATE_CLICK_LABEL)

    fun selectionClickLabel(): String = chord(selectionModifierLabel(), CREATE_CLICK_LABEL)

    fun multiSelectionClickLabel(): String =
        chord(selectionModifierLabel(), targetModifierLabel(), CREATE_CLICK_LABEL)

    fun transformClickLabel(): String = chord(transformModifierLabel(), CREATE_CLICK_LABEL)

    fun deleteLabel(): String =
        if (platformProfile == MeasurementPlatformProfile.MAC) "⌫" else "Del/Backspace"

    fun cancelLabel(): String =
        if (platformProfile == MeasurementPlatformProfile.MAC) "⎋" else "Esc"

    fun undoLabel(): String = chord(editorModifierLabel(), "Z")

    fun redoLabel(): String =
        if (platformProfile == MeasurementPlatformProfile.MAC) {
            chord(selectionModifierLabel(), editorModifierLabel(), "Z")
        } else {
            "${chord(editorModifierLabel(), "Y")} / ${chord(editorModifierLabel(), selectionModifierLabel(), "Z")}"
        }

    fun historySummary(): String = "${undoLabel()} undo · ${redoLabel()} redo"

    fun editClipboardKeys(): String =
        if (platformProfile == MeasurementPlatformProfile.MAC) {
            "${editorModifierLabel()}C/X/V"
        } else {
            "${editorModifierLabel()}+C/X/V"
        }

    fun editClipboardSummary(): String =
        if (platformProfile == MeasurementPlatformProfile.MAC) {
            "${editorModifierLabel()}C/X/V clipboard"
        } else {
            "${editorModifierLabel()}+C/X/V clipboard"
        }

    fun selectionModifierDown(): Boolean =
        ClientBackend.current.isKeyDown(Key.LeftShift) ||
            ClientBackend.current.isKeyDown(Key.RightShift)

    fun targetModifierDown(): Boolean =
        ClientBackend.current.isKeyDown(Key.LeftCtrl) ||
            ClientBackend.current.isKeyDown(Key.RightCtrl)

    fun transformModifierDown(): Boolean =
        ClientBackend.current.isKeyDown(Key.LeftAlt) ||
            ClientBackend.current.isKeyDown(Key.RightAlt)

    fun editorModifierDown(): Boolean =
        if (platformProfile == MeasurementPlatformProfile.MAC) {
            ClientBackend.current.isKeyDown(Key.LeftMeta) ||
                ClientBackend.current.isKeyDown(Key.RightMeta)
        } else {
            ClientBackend.current.isKeyDown(Key.LeftCtrl) ||
                ClientBackend.current.isKeyDown(Key.RightCtrl)
        }

    fun currentKeyboardSnapshot(keyPressed: (Key) -> Boolean): MeasurementInputSnapshot {
        val editorModifierDown = editorModifierDown()
        val selectionModifierDown = selectionModifierDown()
        return MeasurementInputSnapshot(
            selectionModifierDown = selectionModifierDown,
            targetModifierDown = targetModifierDown(),
            transformModifierDown = transformModifierDown(),
            editorModifierDown = editorModifierDown,
            escapeTriggered = keyPressed(Key.Escape),
            redoPrimaryTriggered =
                when (platformProfile) {
                    MeasurementPlatformProfile.MAC ->
                        editorModifierDown && selectionModifierDown && keyPressed(Key.Z)
                    MeasurementPlatformProfile.STANDARD -> editorModifierDown && keyPressed(Key.Y)
                },
            redoSecondaryTriggered =
                platformProfile == MeasurementPlatformProfile.STANDARD &&
                    editorModifierDown &&
                    selectionModifierDown &&
                    keyPressed(Key.Z),
            undoTriggered = editorModifierDown && keyPressed(Key.Z),
            copyTriggered = editorModifierDown && keyPressed(Key.C),
            cutTriggered = editorModifierDown && keyPressed(Key.X),
            pasteTriggered = editorModifierDown && keyPressed(Key.V),
            deleteTriggered = keyPressed(Key.Delete) || keyPressed(Key.Backspace),
        )
    }

    fun currentWorldClickSnapshot(): MeasurementInputSnapshot =
        MeasurementInputSnapshot(
            selectionModifierDown = selectionModifierDown(),
            targetModifierDown = targetModifierDown(),
            transformModifierDown = transformModifierDown(),
            editorModifierDown = editorModifierDown(),
        )

    fun footerText(): String =
        if (platformProfile == MeasurementPlatformProfile.MAC) {
            "${CREATE_CLICK_LABEL} create · ${targetModifierLabel()} offset · ${selectionModifierLabel()} select · ${transformModifierLabel()} move/resize · ${editorModifierLabel()}C/X/V · ${undoLabel()} · ${redoLabel()} · ${cancelLabel()} cancel"
        } else {
            "${CREATE_CLICK_LABEL} create · ${targetModifierLabel()} offset · ${selectionModifierLabel()} select · ${transformModifierLabel()} move/resize · ${editorModifierLabel()}+C/X/V · ${undoLabel()} · ${redoLabel()} · ${cancelLabel()} cancel"
        }

    private fun chord(vararg keys: String): String =
        when (platformProfile) {
            MeasurementPlatformProfile.MAC -> {
                val mouseKey = keys.lastOrNull()?.takeIf { it == CREATE_CLICK_LABEL }
                if (mouseKey != null) {
                    keys.dropLast(1).joinToString(separator = "") + " " + mouseKey
                } else {
                    keys.joinToString(separator = "")
                }
            }
            MeasurementPlatformProfile.STANDARD -> keys.joinToString(separator = "+")
        }
}
