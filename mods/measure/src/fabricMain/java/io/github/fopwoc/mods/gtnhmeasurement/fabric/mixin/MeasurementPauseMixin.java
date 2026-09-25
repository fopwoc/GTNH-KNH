package io.github.fopwoc.mods.gtnhmeasurement.fabric.mixin;

import io.github.fopwoc.mods.gtnhmeasurement.client.measurement.ModernMeasurementInput;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MeasurementPauseMixin {
    @Inject(method = "pauseGame", at = @At("HEAD"), cancellable = true)
    private void measure$pauseGame(boolean suppressPauseMenu, CallbackInfo callback) {
        if (ModernMeasurementInput.INSTANCE.onPause()) callback.cancel();
    }
}
