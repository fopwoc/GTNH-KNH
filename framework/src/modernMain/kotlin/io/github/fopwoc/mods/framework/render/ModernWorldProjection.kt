package io.github.fopwoc.mods.framework.render

import io.github.fopwoc.mods.framework.minecraft.eye
import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import org.joml.Matrix4f

/** Captured before the world draw, before hand/GUI rendering replaces the projection. */
object ModernWorldProjection {
    private var world: Any? = null
    private var snapshot: WorldProjection? = null

    val current: WorldProjection?
        get() =
            Minecraft.getInstance().let {
                snapshot.takeIf { _ -> it.level != null && world === it.level }
            }

    @JvmStatic
    fun capture(view: Matrix4f, projection: Matrix4f, camera: Camera) {
        val eye = camera.eye
        snapshot =
            WorldProjection(
                eye.x,
                eye.y,
                eye.z,
                view.get(FloatArray(16)),
                projection.get(FloatArray(16)),
            )
        world = Minecraft.getInstance().level
    }

    /*? if >=26 {*/
    @JvmStatic
    fun capture(projection: Matrix4f) {
        val renderer = Minecraft.getInstance().gameRenderer
        /*? if >=26.2 {*/
        val state = renderer.gameRenderState().levelRenderState.cameraRenderState
        /*?} else {*/
        /*val state = renderer.gameRenderState.levelRenderState.cameraRenderState
         */
        /*?}*/
        snapshot =
            WorldProjection(
                state.pos.x,
                state.pos.y,
                state.pos.z,
                state.viewRotationMatrix.get(FloatArray(16)),
                projection.get(FloatArray(16)),
            )
        world = Minecraft.getInstance().level
    }
    /*?}*/
}
