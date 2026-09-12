package io.github.fopwoc.mods.gtnhmeasurement.client.compat

import cpw.mods.fml.common.Loader
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.gtnhmeasurement.config.MeasurementConfig
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import net.minecraft.entity.Entity
import org.apache.logging.log4j.LogManager

/**
 * Optional hook into `freecam-gtnh`. The mod has no API, so its controller is reached through
 * method handles resolved once; any failure simply disables the integration.
 */
@SideOnly(Side.CLIENT)
object FreecamCompat {
  private val logger = LogManager.getLogger(FreecamCompat::class.java)

  private const val MOD_ID = "freecam-gtnh"
  private const val CONTROLLER_CLASS = "com.caedis.freecam.camera.FreecamController"
  const val MIN_REACH = 1
  const val MAX_REACH = 256

  private val isActiveHandle: MethodHandle? by lazy(::resolveIsActive)
  private val heightLock: HeightLock? by lazy(::resolveHeightLock)

  private var wasActive = false
  private var lockedY: Double? = null

  val available: Boolean
    get() = isActiveHandle != null

  /**
   * Reach used while the camera is active; starts at the configured value each time freecam turns
   * on.
   */
  var reach: Int = MeasurementConfig.freecamReach
    private set

  /** Call once per client tick so the reach resets when the camera is switched off. */
  fun tick() {
    val active = isActive()
    if (active != wasActive) {
      wasActive = active
      reach = MeasurementConfig.freecamReach
    }
  }

  fun adjustReach(steps: Int) {
    reach = (reach + steps).coerceIn(MIN_REACH, MAX_REACH)
  }

  /**
   * Freecam moves its camera from the raw keyboard in its own client tick, so a key-state override
   * cannot stop it descending on Shift. Instead: [rememberHeight] at the start of the tick, and
   * [restoreHeight] before the frame is rendered puts the camera back and kills its vertical
   * momentum whenever [hold] is true — the camera never visibly moves.
   */
  fun rememberHeight() {
    val lock = heightLock ?: return
    lockedY = if (isActive()) lock.cameraY() else null
  }

  fun restoreHeight(hold: Boolean) {
    val lock = heightLock ?: return
    val y = lockedY ?: return
    if (hold && isActive()) {
      lock.setCameraY(y)
    }
  }

  /** True while the detached freecam camera is the render view entity. */
  fun isActive(): Boolean {
    val handle = isActiveHandle ?: return false
    return runCatching { handle.invoke() as Boolean }.getOrDefault(false)
  }

  private class HeightLock(
      private val instance: MethodHandle,
      private val cameraEntity: MethodHandle,
      private val velocityY: MethodHandle,
  ) {
    fun cameraY(): Double? = camera()?.posY

    fun setCameraY(y: Double) {
      val camera = camera() ?: return
      camera.posY = y
      camera.prevPosY = y
      camera.lastTickPosY = y
      velocityY.invoke(instance.invoke(), 0.0)
    }

    private fun camera(): Entity? =
        runCatching { cameraEntity.invoke(instance.invoke()) as? Entity }.getOrNull()
  }

  private fun resolveHeightLock(): HeightLock? {
    if (!Loader.isModLoaded(MOD_ID)) {
      return null
    }
    return runCatching {
          val controllerClass = Class.forName(CONTROLLER_CLASS)
          val lookup = MethodHandles.lookup()
          val instance =
              MethodHandles.publicLookup()
                  .findStatic(controllerClass, "instance", MethodType.methodType(controllerClass))
          val cameraField =
              controllerClass.getDeclaredField("cameraEntity").apply { isAccessible = true }
          val velocityField =
              controllerClass.getDeclaredField("velocityY").apply { isAccessible = true }
          HeightLock(
              instance = instance,
              cameraEntity = lookup.unreflectGetter(cameraField),
              velocityY = lookup.unreflectSetter(velocityField),
          )
        }
        .onFailure { logger.warn("Freecam height lock unavailable", it) }
        .getOrNull()
  }

  private fun resolveIsActive(): MethodHandle? {
    if (!Loader.isModLoaded(MOD_ID)) {
      return null
    }
    return runCatching {
          val lookup = MethodHandles.publicLookup()
          val controllerClass = Class.forName(CONTROLLER_CLASS)
          val instance =
              lookup.findStatic(controllerClass, "instance", MethodType.methodType(controllerClass))
          val isActive =
              lookup.findVirtual(
                  controllerClass,
                  "isActive",
                  MethodType.methodType(Boolean::class.javaPrimitiveType),
              )
          // instance().isActive() folded into one no-arg handle.
          MethodHandles.foldArguments(isActive, instance).also {
            logger.info("Freecam detected; camera reach applies while it is active")
          }
        }
        .onFailure {
          logger.warn("Freecam is present but its controller could not be bound", it)
        }
        .getOrNull()
  }
}
