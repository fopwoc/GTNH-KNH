package io.github.fopwoc.mods.framework.fabric.mixin;

import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Fabric has no HUD layer order on 1.21.1 and runs its HUD callback after the debug screen, so the
 * layers meant to sit under it are drawn here, just before it. Newer versions draw the debug
 * screen after the whole HUD on their own.
 */
@Mixin(DebugScreenOverlay.class)
public abstract class DebugScreenOverlayMixin {
    /*? if <26 {*/
    /*@org.spongepowered.asm.mixin.injection.Inject(
            method = "render",
            at = @org.spongepowered.asm.mixin.injection.At("HEAD"))
    private void knhcore$beforeDebugScreen(
            net.minecraft.client.gui.GuiGraphics graphics,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo callback) {
        ((io.github.fopwoc.mods.framework.client.FabricClientBackend)
                        io.github.fopwoc.mods.framework.client.ClientBackend.Companion.getCurrent())
                .renderBelowDebug(graphics);
    }
    */
    /*?}*/
}
