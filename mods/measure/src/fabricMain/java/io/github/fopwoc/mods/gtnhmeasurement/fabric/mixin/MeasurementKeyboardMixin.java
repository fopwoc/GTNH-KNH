package io.github.fopwoc.mods.gtnhmeasurement.fabric.mixin;

import io.github.fopwoc.mods.gtnhmeasurement.client.measurement.ModernMeasurementInput;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class MeasurementKeyboardMixin {
    /*? if >=26 {*/
    @Inject(method = "keyPress", at = @At("HEAD"))
    private void measure$keyPress(
            long window, int action, net.minecraft.client.input.KeyEvent event, CallbackInfo callback) {
        ModernMeasurementInput.INSTANCE.onKey(event.key(), action);
    }
    /*?} else {*/
    /*@Inject(method = "keyPress", at = @At("HEAD"))
    private void measure$keyPress(
            long window, int key, int scancode, int action, int modifiers, CallbackInfo callback) {
        ModernMeasurementInput.INSTANCE.onKey(key, action);
    }
    */
    /*?}*/
}
