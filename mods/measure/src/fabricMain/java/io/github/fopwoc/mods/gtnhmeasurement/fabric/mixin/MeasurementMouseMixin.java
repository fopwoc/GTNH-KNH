package io.github.fopwoc.mods.gtnhmeasurement.fabric.mixin;

import io.github.fopwoc.mods.gtnhmeasurement.client.measurement.ModernMeasurementInput;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MeasurementMouseMixin {
    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void measure$onButton(long window, MouseButtonInfo button, int action, CallbackInfo callback) {
        if (ModernMeasurementInput.INSTANCE.onMouseButton(button.button(), action)) callback.cancel();
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void measure$onScroll(long window, double horizontal, double vertical, CallbackInfo callback) {
        if (ModernMeasurementInput.INSTANCE.onScroll(vertical)) callback.cancel();
    }
}
