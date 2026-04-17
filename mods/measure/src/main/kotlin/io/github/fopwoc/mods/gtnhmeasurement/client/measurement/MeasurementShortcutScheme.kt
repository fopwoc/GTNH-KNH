package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import org.lwjgl.input.Keyboard

object MeasurementShortcutScheme {
    private val isMacOs = System.getProperty("os.name")
        ?.lowercase()
        ?.contains("mac") == true

    fun selectionModifierDown(): Boolean =
        Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)

    fun targetModifierDown(): Boolean =
        Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)

    fun transformModifierDown(): Boolean =
        Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU)

    fun editorModifierDown(): Boolean = if (isMacOs) {
        Keyboard.isKeyDown(Keyboard.KEY_LMETA) || Keyboard.isKeyDown(Keyboard.KEY_RMETA)
    } else {
        Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)
    }

    fun redoPrimaryPressed(keyPressed: (Int) -> Boolean): Boolean = when {
        isMacOs -> editorModifierDown() && selectionModifierDown() && keyPressed(Keyboard.KEY_Z)
        else -> editorModifierDown() && keyPressed(Keyboard.KEY_Y)
    }

    fun redoSecondaryPressed(keyPressed: (Int) -> Boolean): Boolean =
        !isMacOs && editorModifierDown() && selectionModifierDown() && keyPressed(Keyboard.KEY_Z)

    fun footerText(): String = if (isMacOs) {
        "MMB create · Ctrl offset · Shift select · Option move/resize · ⌘C/X/V/Z"
    } else {
        "MMB create · Ctrl offset · Shift select · Alt move/resize · Ctrl+C/X/V/Z/Y"
    }
}

