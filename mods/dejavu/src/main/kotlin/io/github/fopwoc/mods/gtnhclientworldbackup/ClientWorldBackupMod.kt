package io.github.fopwoc.mods.gtnhclientworldbackup

import cpw.mods.fml.common.Mod
import cpw.mods.fml.common.SidedProxy
import cpw.mods.fml.common.event.FMLInitializationEvent
import cpw.mods.fml.common.event.FMLPreInitializationEvent
import io.github.fopwoc.mods.framework.ModProxy
import io.github.fopwoc.mods.gtnhclientworldbackup.CLIENT_PROXY_CLASS
import io.github.fopwoc.mods.gtnhclientworldbackup.MOD_ID
import io.github.fopwoc.mods.gtnhclientworldbackup.MOD_NAME
import io.github.fopwoc.mods.gtnhclientworldbackup.MOD_VERSION
import io.github.fopwoc.mods.gtnhclientworldbackup.SERVER_PROXY_CLASS
import io.github.fopwoc.mods.gtnhclientworldbackup.config.BackupConfig
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

@Mod(
    modid = MOD_ID,
    name = MOD_NAME,
    version = MOD_VERSION,
    modLanguageAdapter = "net.shadowfacts.forgelin.KotlinAdapter",
    dependencies = "required-after:forgelin;required-after:knhcore;"
)
object ClientWorldBackupMod {
    lateinit var logger: Logger

    @SidedProxy(
        clientSide = CLIENT_PROXY_CLASS,
        serverSide = SERVER_PROXY_CLASS
    )
    lateinit var proxy: ModProxy

    @Mod.EventHandler
    fun onPreInit(event: FMLPreInitializationEvent) {
        logger = LogManager.getLogger(MOD_NAME)
        BackupConfig.load(event.modConfigurationDirectory)
    }

    @Mod.EventHandler
    fun onInit(event: FMLInitializationEvent) {
        proxy.init()
        logger.info("{} initialized", MOD_NAME)
    }
}



