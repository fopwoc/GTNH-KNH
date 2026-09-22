package io.github.fopwoc.mods.framework.platform

import cpw.mods.fml.common.FMLCommonHandler
import java.io.File
import cpw.mods.fml.common.Loader as FmlLoader

class GtnhPlatformBackend : PlatformBackend {
    override val loader = Loader.GTNH
    override val minecraftVersion: String = FmlLoader.MC_VERSION
    override val isClient: Boolean get() = FMLCommonHandler.instance().side.isClient
    override val configDirectory: File get() = FmlLoader.instance().configDir
    override val gameDirectory: File get() = configDirectory.parentFile

    override fun isModLoaded(modId: String): Boolean = FmlLoader.isModLoaded(modId)

    override fun installEvents() {
        FMLCommonHandler.instance().bus().register(GtnhServerEvents)
        if (isClient) FMLCommonHandler.instance().bus().register(GtnhClientEvents)
    }
}
