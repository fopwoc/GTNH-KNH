package io.github.fopwoc.mods.framework.client

import cpw.mods.fml.client.registry.ClientRegistry
import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.event.ClientEvents
import io.github.fopwoc.mods.framework.ui.compose.hud.HudLayer
import io.github.fopwoc.mods.framework.ui.compose.hud.HudLayerHost
import io.github.fopwoc.mods.framework.ui.compose.input.Key
import io.github.fopwoc.mods.framework.ui.compose.input.KeyBinding
import io.github.fopwoc.mods.framework.ui.compose.input.KeyPress
import io.github.fopwoc.mods.framework.ui.compose.minecraft.GtnhComposeScreenHost
import io.github.fopwoc.mods.framework.ui.compose.minecraft.HudRect
import io.github.fopwoc.mods.framework.ui.compose.minecraft.render.GtnhRenderSurface
import io.github.fopwoc.mods.framework.ui.compose.minecraft.render.MinecraftPrimitiveRenderCallbacks
import io.github.fopwoc.mods.framework.ui.compose.minecraft.screen.lwjglCode
import io.github.fopwoc.mods.framework.ui.compose.screen.ComposeScreen
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Gui
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLiving
import net.minecraft.entity.item.EntityItem
import net.minecraft.entity.monster.IMob
import net.minecraft.entity.player.EntityPlayer
import net.minecraftforge.client.ClientCommandHandler
import net.minecraftforge.client.event.RenderGameOverlayEvent
import net.minecraftforge.common.MinecraftForge
import org.lwjgl.input.Keyboard
import org.lwjgl.input.Mouse

@SideOnly(Side.CLIENT)
class GtnhClientBackend : ClientBackend {
    private val hudLayers = mutableListOf<HudLayerHost>()

    override val isInWorld: Boolean
        get() = Minecraft.getMinecraft().let { it.thePlayer != null && it.theWorld != null }

    override val playerPosition: PlayerPosition?
        get() =
            Minecraft.getMinecraft().thePlayer?.let { PlayerPosition(it.posX, it.posY, it.posZ) }

    override val playerYaw: Float?
        get() = Minecraft.getMinecraft().thePlayer?.rotationYaw

    override val isHudHidden: Boolean
        get() = Minecraft.getMinecraft().gameSettings.hideGUI

    override fun entitiesNear(radius: Double): List<EntitySighting> {
        val minecraft = Minecraft.getMinecraft()
        val player = minecraft.thePlayer ?: return emptyList()
        val world = minecraft.theWorld ?: return emptyList()
        return world.loadedEntityList.mapNotNull { entity ->
            if (
                entity !is Entity ||
                    entity === player ||
                    entity.isDead ||
                    abs(entity.posX - player.posX) > radius ||
                    abs(entity.posZ - player.posZ) > radius
            )
                return@mapNotNull null
            val kind =
                when (entity) {
                    is EntityItem -> EntityKind.ITEM
                    is EntityPlayer -> EntityKind.PLAYER
                    is IMob -> EntityKind.HOSTILE
                    is EntityLiving -> EntityKind.PASSIVE
                    else -> return@mapNotNull null
                }
            EntitySighting(entity.entityId, kind, entity.posX, entity.posY, entity.posZ)
        }
    }

    override val currentDimensionId: String?
        get() = Minecraft.getMinecraft().thePlayer?.dimension?.toString()

    override val currentWorldId: String?
        get() = ClientWorldContext.currentId()

    override val isPlayerListOpen: Boolean
        get() {
            val minecraft = Minecraft.getMinecraft()
            if (!minecraft.gameSettings.keyBindPlayerList.getIsKeyPressed()) return false
            val player = minecraft.thePlayer ?: return false
            val world = minecraft.theWorld ?: return false
            val handler = player.sendQueue ?: return false
            return !minecraft.isIntegratedServerRunning() ||
                handler.playerInfoList.size > 1 ||
                world.scoreboard.func_96539_a(0) != null
        }

    override fun playerListBounds(screenWidth: Int): HudRect? {
        val minecraft = Minecraft.getMinecraft()
        val player = minecraft.thePlayer ?: return null
        val handler = player.sendQueue ?: return null
        if (!isPlayerListOpen) return null

        val maxPlayers = max(1, handler.currentServerMaxPlayers)
        var rows = maxPlayers
        var columns = 1
        while (rows > 20) {
            columns++
            rows = (maxPlayers + columns - 1) / columns
        }
        val columnWidth = min(150, 300 / columns)
        return HudRect(
            (screenWidth - columns * columnWidth) / 2 - 1,
            9,
            columns * columnWidth + 1,
            rows * 9 + 1,
        )
    }

    override fun textWidth(text: String): Int =
        Minecraft.getMinecraft().fontRenderer.getStringWidth(text)

    override fun trimTextToWidth(text: String, width: Int): String =
        Minecraft.getMinecraft().fontRenderer.trimStringToWidth(text, width)

    override fun openScreen(screen: ComposeScreen) =
        Minecraft.getMinecraft().displayGuiScreen(GtnhComposeScreenHost(screen))

    override fun registerHud(layer: HudLayer) {
        if (hudLayers.isEmpty()) MinecraftForge.EVENT_BUS.register(this)
        hudLayers += HudLayerHost(layer) { GtnhRenderSurface(HudPrimitives) }
    }

    override fun registerCommand(command: ClientCommand) {
        ClientCommandHandler.instance.registerCommand(GtnhClientCommand(command))
    }

    private val bindings = LinkedHashMap<KeyBinding, net.minecraft.client.settings.KeyBinding>()

    override fun registerKeyBinding(binding: KeyBinding) {
        if (bindings.isEmpty()) ClientEvents.tickEnd.subscribe { pollBindings() }
        val native =
            net.minecraft.client.settings.KeyBinding(
                binding.name,
                lwjglCode(binding.defaultKey ?: Key.Unknown),
                "key.categories.${binding.category}",
            )
        ClientRegistry.registerKeyBinding(native)
        bindings[binding] = native
    }

    override fun isBindingDown(binding: KeyBinding): Boolean = native(binding).getIsKeyPressed()

    override fun bindingMatches(binding: KeyBinding, press: KeyPress): Boolean =
        native(binding).keyCode.let { it != Keyboard.KEY_NONE && it == press.code }

    override fun isKeyDown(key: Key): Boolean =
        lwjglCode(key).let { it != Keyboard.KEY_NONE && Keyboard.isKeyDown(it) }

    override val pointerX: Double
        get() =
            Minecraft.getMinecraft().let {
                Mouse.getX() *
                    ScaledResolution(it, it.displayWidth, it.displayHeight).scaledWidth_double /
                    it.displayWidth
            }

    override val pointerY: Double
        get() =
            Minecraft.getMinecraft().let {
                val height =
                    ScaledResolution(it, it.displayWidth, it.displayHeight).scaledHeight_double
                height - Mouse.getY() * height / it.displayHeight
            }

    override fun isMouseButtonDown(button: Int): Boolean = Mouse.isButtonDown(button)

    private fun native(binding: KeyBinding) =
        checkNotNull(bindings[binding]) { "Key binding ${binding.name} is not registered" }

    private fun pollBindings() {
        bindings.forEach { (binding, native) ->
            while (native.isPressed) binding.onPress()
        }
    }

    @SubscribeEvent
    fun onRenderOverlay(event: RenderGameOverlayEvent.Post) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) return
        hudLayers.forEach { it.render(event.resolution.scaledWidth, event.resolution.scaledHeight) }
    }

    private object HudPrimitives : MinecraftPrimitiveRenderCallbacks {
        override fun fillRect(left: Int, top: Int, right: Int, bottom: Int, color: Int) =
            Gui.drawRect(left, top, right, bottom, color)

        override fun drawHorizontalLine(startX: Int, endX: Int, y: Int, color: Int) =
            Gui.drawRect(min(startX, endX), y, max(startX, endX) + 1, y + 1, color)

        override fun drawVerticalLine(x: Int, startY: Int, endY: Int, color: Int) =
            Gui.drawRect(x, min(startY, endY), x + 1, max(startY, endY) + 1, color)
    }
}
