package io.github.fopwoc.mods.framework.ui.compose.minecraft.render

import org.apache.logging.log4j.LogManager
import org.lwjgl.opengl.GL11

/** Deletes texture arrays without Angelica's 2D-only deferred texture deletion path. */
internal object GpuTextureDeletion {
  private val logger = LogManager.getLogger(GpuTextureDeletion::class.java)

  fun delete(texture: Int) {
    if (texture == 0) return
    val loader = GpuTextureDeletion::class.java.classLoader
    try {
      Class.forName("com.gtnewhorizons.angelica.glsm.GLStateManager", false, loader)
    } catch (_: ClassNotFoundException) {
      GL11.glDeleteTextures(texture)
      return
    }

    try {
      val manager =
          Class.forName("com.gtnewhorizons.angelica.glsm.backend.BackendManager", false, loader)
      val backend = manager.getField("RENDER_BACKEND").get(null)
      backend.javaClass.getMethod("deleteTextures", Int::class.javaPrimitiveType).invoke(backend, texture)
    } catch (failure: ReflectiveOperationException) {
      // Angelica's public deletion path assumes GL_TEXTURE_2D and can overwrite the GUI atlas.
      logger.error("Could not delete GPU canvas texture array through Angelica", failure)
    }
  }
}
