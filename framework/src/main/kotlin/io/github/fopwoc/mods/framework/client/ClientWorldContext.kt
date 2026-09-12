package io.github.fopwoc.mods.framework.client

import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import net.minecraft.client.Minecraft

/**
 * Stable, file-name-safe id for "where the client is": the singleplayer world or the server
 * address. Mods key per-world client state (saved measurements, cached profiles) on it.
 */
@SideOnly(Side.CLIENT)
object ClientWorldContext {
  fun currentId(minecraft: Minecraft = Minecraft.getMinecraft()): String? {
    val world = minecraft.theWorld ?: return null
    val worldName = runCatching { world.worldInfo.worldName }.getOrNull()?.takeIf(String::isNotBlank)
    val serverDescriptor = serverDescriptor(minecraft)
    return when {
      minecraft.isSingleplayer -> "singleplayer-${sanitize(worldName ?: "world")}"
      serverDescriptor != null -> "server-${sanitize(serverDescriptor)}"
      else -> "world-${sanitize(worldName ?: "world")}"
    }
  }

  private fun serverDescriptor(minecraft: Minecraft): String? {
    // func_147104_D is Minecraft.getCurrentServerData; it has no MCP name in the 1.7.10 mappings.
    val serverData = minecraft.func_147104_D() ?: return null
    return listOf(serverData.serverIP, serverData.serverName).firstOrNull { !it.isNullOrBlank() }
  }

  fun sanitize(value: String): String = value.replace(Regex("[^A-Za-z0-9._-]"), "_")
}
