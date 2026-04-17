package io.github.fopwoc.mods.framework

import cpw.mods.fml.common.Mod
import cpw.mods.fml.common.event.FMLInitializationEvent
import io.github.fopwoc.mods.framework.MOD_ID
import io.github.fopwoc.mods.framework.MOD_NAME
import io.github.fopwoc.mods.framework.MOD_VERSION
import org.apache.logging.log4j.LogManager

@Mod(
    modid = MOD_ID,
    name = MOD_NAME,
    version = MOD_VERSION,
    modLanguageAdapter = "net.shadowfacts.forgelin.KotlinAdapter",
    dependencies = "required-after:forgelin;required-after:hodgepodge;"
)
object FrameworkMod {
    @Mod.EventHandler
    fun onInit(event: FMLInitializationEvent) {
        LogManager.getLogger(MOD_NAME).info("{} initialized", MOD_NAME)
    }
}

