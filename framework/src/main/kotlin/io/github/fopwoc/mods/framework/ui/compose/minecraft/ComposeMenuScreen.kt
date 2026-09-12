package io.github.fopwoc.mods.framework.ui.compose.minecraft

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import net.minecraft.client.settings.KeyBinding
import org.lwjgl.input.Keyboard

/**
 * A non-pausing, background-less screen for a mod menu that reads mutable runtime state: it bumps
 * [refreshToken] every tick so a route can re-read that state with `LaunchedEffect`, closes on the
 * key that opened it, and defers closing to the next tick so a click handler may ask for it safely.
 *
 * ```kotlin
 * class MyScreen : ComposeMenuScreen(toggleKey = MyKeyBindings.openMenu) {
 *   @Composable override fun Content() =
 *       MyRoute(width, height, refreshToken = refreshToken, onClose = ::requestClose)
 * }
 * ```
 */
@SideOnly(Side.CLIENT)
abstract class ComposeMenuScreen(private val toggleKey: KeyBinding? = null) : ComposeGuiScreen() {
  private var closeRequested = false

  /** Increments every tick; key a `LaunchedEffect` on it to poll runtime state. */
  protected var refreshToken: Int by mutableIntStateOf(0)
    private set

  override val composeBackgroundStyle: ComposeBackgroundStyle = ComposeBackgroundStyle.None

  override fun doesGuiPauseGame(): Boolean = false

  protected fun requestClose() {
    closeRequested = true
  }

  /** Forces a re-read before the next tick, e.g. after a key shortcut changed state. */
  protected fun refreshNow() {
    refreshToken += 1
  }

  override fun onUnhandledKey(typedChar: Char, keyCode: Int): Boolean {
    val key = toggleKey?.keyCode ?: Keyboard.KEY_NONE
    if (key != Keyboard.KEY_NONE && keyCode == key) {
      mc.displayGuiScreen(null)
      return true
    }
    return false
  }

  override fun updateScreen() {
    super.updateScreen()
    refreshToken += 1
    if (closeRequested) {
      closeRequested = false
      mc.displayGuiScreen(null)
    }
  }
}
