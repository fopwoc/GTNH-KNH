package io.github.fopwoc.mods.framework.client

import cpw.mods.fml.client.registry.ClientRegistry
import cpw.mods.fml.common.FMLCommonHandler
import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.common.gameevent.InputEvent
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import net.minecraft.client.settings.KeyBinding
import org.lwjgl.input.Keyboard

/**
 * Key bindings with an action attached. Registers with `ClientRegistry` and dispatches presses from
 * one FML listener; fires while no screen is open (vanilla's `isPressed` semantics).
 *
 * ```kotlin
 * val openMenu = ClientKeyBindings.bind("key.mymod.openMenu", "key.categories.mymod") {
 *   ScreenOpener.open(::MyScreen)
 * }
 * ```
 */
@SideOnly(Side.CLIENT)
object ClientKeyBindings {
  private val actions = LinkedHashMap<KeyBinding, () -> Unit>()
  private var registered = false

  fun bind(
      description: String,
      category: String,
      defaultKey: Int = Keyboard.KEY_NONE,
      action: () -> Unit,
  ): KeyBinding {
    val binding = KeyBinding(description, defaultKey, category)
    ClientRegistry.registerKeyBinding(binding)
    actions[binding] = action
    if (!registered) {
      registered = true
      FMLCommonHandler.instance().bus().register(this)
    }
    return binding
  }

  @SubscribeEvent
  fun onKeyInput(event: InputEvent.KeyInputEvent) {
    actions.forEach { (binding, action) ->
      while (binding.isPressed) {
        action()
      }
    }
  }
}
