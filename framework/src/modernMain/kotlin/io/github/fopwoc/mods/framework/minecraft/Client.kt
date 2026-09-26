package io.github.fopwoc.mods.framework.minecraft

import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.world.phys.Vec3
import org.joml.Vector3fc

// Client state that 26.x turned into accessors, and 26.2 moved behind the GUI.

/** The open screen, if any. */
val Minecraft.currentScreen: Screen?
    get() {
        /*? if >=26.2 {*/
        return gui.screen()
        /*?} else {*/
        /*return screen
         */
        /*?}*/
    }

/** Whether the player hid the HUD (F1). */
val Minecraft.isHudHidden: Boolean
    get() {
        /*? if >=26.2 {*/
        return gui.hud.isHidden()
        /*?} else {*/
        /*return options.hideGui
         */
        /*?}*/
    }

/** The camera the level is rendered from. */
val Minecraft.mainCamera: Camera
    get() {
        /*? if >=26.2 {*/
        return gameRenderer.mainCamera()
        /*?} else {*/
        /*return gameRenderer.mainCamera
         */
        /*?}*/
    }

/** The camera's position in the world. */
val Camera.eye: Vec3
    get() {
        /*? if >=26 {*/
        return position()
        /*?} else {*/
        /*return position
         */
        /*?}*/
    }

/** The unit vector the camera looks along. */
val Camera.forward: Vector3fc
    get() {
        /*? if >=26 {*/
        return forwardVector()
        /*?} else {*/
        /*return lookVector
         */
        /*?}*/
    }
