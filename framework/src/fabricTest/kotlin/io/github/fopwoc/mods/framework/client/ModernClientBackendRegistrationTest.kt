//? if >=26 {
// Not ported to 1.21.1 yet: the whole file exists only from 26.x.
package io.github.fopwoc.mods.framework.client

import androidx.compose.runtime.Composable
import com.mojang.blaze3d.platform.InputConstants
import io.github.fopwoc.mods.framework.ui.compose.hud.HudLayer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import net.minecraft.client.KeyMapping

class ModernClientBackendRegistrationTest {
    @Test
    fun concurrentHudRegistrationInstallsOneLoaderHook() {
        val backend = RecordingBackend()
        val workers = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        try {
            val results =
                (0 until 8).map { index ->
                    workers.submit {
                        start.await()
                        backend.registerHud(
                            object : HudLayer("test-$index") {
                                @Composable override fun Content() = Unit
                            }
                        )
                    }
                }
            start.countDown()
            results.forEach { it.get(5, TimeUnit.SECONDS) }
            assertEquals(1, backend.hudHooks.get())
        } finally {
            workers.shutdownNow()
        }
    }

    private class RecordingBackend : ModernClientBackend() {
        val hudHooks = AtomicInteger()

        override fun installHud() {
            hudHooks.incrementAndGet()
            Thread.sleep(25)
        }

        override fun installCommands() = Unit

        override fun registerKeyMapping(mapping: KeyMapping, category: KeyMapping.Category) = Unit

        override fun boundKey(mapping: KeyMapping): InputConstants.Key = mapping.defaultKey
    }
}
//?}
