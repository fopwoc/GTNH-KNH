package io.github.fopwoc.mods.gtnhmeasurement.client.compat

import cpw.mods.fml.common.Loader
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.gtnhmeasurement.MeasurementMod
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

/**
 * Optional hook into `freecam-gtnh`. The mod has no API, so its controller is reached through
 * method handles resolved once; any failure simply disables the integration.
 */
@SideOnly(Side.CLIENT)
object FreecamCompat {
  private const val MOD_ID = "freecam-gtnh"
  private const val CONTROLLER_CLASS = "com.caedis.freecam.camera.FreecamController"

  private val isActiveHandle: MethodHandle? by lazy(::resolveIsActive)

  val available: Boolean
    get() = isActiveHandle != null

  /** True while the detached freecam camera is the render view entity. */
  fun isActive(): Boolean {
    val handle = isActiveHandle ?: return false
    return runCatching { handle.invoke() as Boolean }.getOrDefault(false)
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
            MeasurementMod.logger.info("Freecam detected; camera reach applies while it is active")
          }
        }
        .onFailure {
          MeasurementMod.logger.warn("Freecam is present but its controller could not be bound", it)
        }
        .getOrNull()
  }
}
