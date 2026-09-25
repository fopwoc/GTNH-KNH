package io.github.fopwoc.mods.framework.fabric.mixin;

import io.github.fopwoc.mods.framework.server.LevelTickSamples;
import java.util.function.BooleanSupplier;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class ServerLevelTickMixin implements LevelTickSamples {
    @Unique
    private final long[] knhcore$tickTimesNanos = new long[100];

    @Unique
    private long knhcore$tickStartedNanos;

    @Unique
    private int knhcore$lastTickIndex = -1;

    @Inject(method = "tick", at = @At("HEAD"))
    private void knhcore$startTick(BooleanSupplier haveTime, CallbackInfo callback) {
        knhcore$tickStartedNanos = System.nanoTime();
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void knhcore$finishTick(BooleanSupplier haveTime, CallbackInfo callback) {
        knhcore$lastTickIndex = (knhcore$lastTickIndex + 1) % knhcore$tickTimesNanos.length;
        knhcore$tickTimesNanos[knhcore$lastTickIndex] = System.nanoTime() - knhcore$tickStartedNanos;
    }

    @Override
    public long[] getTickTimesNanos() {
        return knhcore$tickTimesNanos;
    }

    @Override
    public int getLastTickIndex() {
        return knhcore$lastTickIndex;
    }
}
