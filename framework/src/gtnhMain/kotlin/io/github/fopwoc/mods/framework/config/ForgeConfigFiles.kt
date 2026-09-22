package io.github.fopwoc.mods.framework.config

import cpw.mods.fml.client.event.ConfigChangedEvent
import cpw.mods.fml.common.FMLCommonHandler
import cpw.mods.fml.common.Loader
import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.common.gameevent.TickEvent
import io.github.fopwoc.mods.framework.log.logger
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Registered configs of every mod: applies in-game edits (`ConfigChangedEvent`) and picks up
 * external file edits by polling once a second on both sides.
 */
object ForgeConfigFiles {
    private const val POLL_INTERVAL_TICKS = 20

    private val logger = logger<ForgeConfigFiles>()
    private val bindings = CopyOnWriteArrayList<ForgeConfigBinding>()
    private var ticks = 0

    init {
        FMLCommonHandler.instance().bus().register(this)
    }

    fun register(config: ModConfig, directory: File = Loader.instance().configDir) {
        check(bindings.none { it.config === config }) { "${config.javaClass.simpleName} is already registered" }
        val binding = ForgeConfigBinding(config, File(directory, "${config.name}.cfg"))
        binding.load()
        bindings += binding
        logger.info("Loaded {} from {}.cfg", config.javaClass.simpleName, config.name)
    }

    fun binding(config: ModConfig): ForgeConfigBinding =
        checkNotNull(bindings.firstOrNull { it.config === config }) { "${config.javaClass.simpleName} is not registered" }

    @SubscribeEvent
    fun onConfigChanged(event: ConfigChangedEvent.OnConfigChangedEvent) {
        bindings.filter { it.config.modId == event.modID }.forEach(ForgeConfigBinding::synchronize)
    }

    @SubscribeEvent
    fun onClientTick(event: TickEvent.ClientTickEvent) = poll(event.phase)

    @SubscribeEvent
    fun onServerTick(event: TickEvent.ServerTickEvent) = poll(event.phase)

    private fun poll(phase: TickEvent.Phase) {
        if (phase != TickEvent.Phase.END || ++ticks % POLL_INTERVAL_TICKS != 0) return
        bindings.forEach(ForgeConfigBinding::refreshIfChanged)
    }
}
