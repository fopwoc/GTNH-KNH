package io.github.fopwoc.mods.gtnhclientworldbackup.proxy

import cpw.mods.fml.common.FMLCommonHandler
import io.github.fopwoc.mods.framework.ModProxy
import io.github.fopwoc.mods.gtnhclientworldbackup.ClientWorldBackupMod
import io.github.fopwoc.mods.gtnhclientworldbackup.backup.ClientWorldBackupManager
import io.github.fopwoc.mods.gtnhclientworldbackup.client.command.BackupStatusCommand
import io.github.fopwoc.mods.gtnhclientworldbackup.client.gui.BackupStatusScreenController
import io.github.fopwoc.mods.gtnhclientworldbackup.client.highlight.BackedUpChunkHighlighter
import net.minecraftforge.client.ClientCommandHandler
import net.minecraftforge.common.MinecraftForge

@Suppress("unused")
class ClientProxy : ModProxy() {
  override fun init() {
    BackedUpChunkHighlighter.initialize()
    ClientCommandHandler.instance.registerCommand(BackupStatusCommand())
    MinecraftForge.EVENT_BUS.register(BackedUpChunkHighlighter)
    MinecraftForge.EVENT_BUS.register(ClientWorldBackupManager)
    FMLCommonHandler.instance().bus().register(BackupStatusScreenController)
    FMLCommonHandler.instance().bus().register(ClientWorldBackupManager)
    ClientWorldBackupMod.logger.info("Registered client world backup systems and chat commands")
  }
}
