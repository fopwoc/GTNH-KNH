package io.github.fopwoc.mods.framework.mixin;

import io.github.fopwoc.mods.framework.render.ModernWorldProjection;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/*? if >=26 {*/
@Mixin(net.minecraft.client.renderer.GameRenderer.class)
/*?} else {*/
/*@Mixin(net.minecraft.client.renderer.LevelRenderer.class)
 */
/*?}*/
/** Observes the exact projection used for the world draw. */
public abstract class WorldProjectionMixin {
    /*? if >=26 {*/
    @org.spongepowered.asm.mixin.injection.ModifyArg(
            method = "renderLevel",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"),
            index = 0)
    private Matrix4f knhcore$captureWorldProjection(Matrix4f projection) {
        ModernWorldProjection.capture(projection);
        return projection;
    }
    /*?} else {*/
    /*@org.spongepowered.asm.mixin.injection.Inject(method = "renderLevel", at = @At("HEAD"))
    private void knhcore$captureWorldProjection(
            net.minecraft.client.DeltaTracker delta,
            boolean outline,
            net.minecraft.client.Camera camera,
            net.minecraft.client.renderer.GameRenderer renderer,
            net.minecraft.client.renderer.LightTexture light,
            Matrix4f view,
            Matrix4f projection,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo callback) {
        ModernWorldProjection.capture(view, projection, camera);
    }
    */
    /*?}*/
}
