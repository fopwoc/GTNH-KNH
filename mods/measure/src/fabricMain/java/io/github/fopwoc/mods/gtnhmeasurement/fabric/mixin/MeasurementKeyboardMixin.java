package io.github.fopwoc.mods.gtnhmeasurement.fabric.mixin;

import io.github.fopwoc.mods.gtnhmeasurement.client.measurement.ModernMeasurementInput;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class MeasurementKeyboardMixin {
    @Inject(method = "keyPress", at = @At("HEAD"))
    private void measure$keyPress(long window, int action, KeyEvent event, CallbackInfo callback) {
        ModernMeasurementInput.INSTANCE.onKey(event.key(), action);
    }
}
