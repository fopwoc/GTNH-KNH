package io.github.fopwoc.mods.framework.render

import io.github.fopwoc.mods.framework.client.ClientBackend
import io.github.fopwoc.mods.framework.client.ModernClientBackend
import java.util.concurrent.CopyOnWriteArrayList
import net.minecraft.world.phys.Vec3

/**
 * Shapes drawn into the world every frame through [WorldShapes]. Each overlay gets the camera's
 * position. Client thread only.
 */
object WorldOverlays {
    private val overlays = CopyOnWriteArrayList<(eye: Vec3) -> Unit>()

    fun register(overlay: (eye: Vec3) -> Unit) {
        if (overlays.isEmpty())
            (ClientBackend.current as ModernClientBackend).installWorldOverlays()
        overlays += overlay
    }

    /*? if >=26 {*/
    /** Called by the loader's level extraction event. */
    internal fun render(eye: Vec3) = ModernWorldShapes.frame { overlays.forEach { it(eye) } }
    /*?} else {*/
    /*// Called by the loader's event after the level is rendered.
    internal fun render(camera: net.minecraft.client.Camera) =
        LegacyWorldShapes.frame(camera) { overlays.forEach { it(camera.position) } }
    */
    /*?}*/
}
