package io.github.fopwoc.mods.framework

import cpw.mods.fml.common.Mod
import cpw.mods.fml.common.event.FMLInitializationEvent
import org.apache.logging.log4j.LogManager

@Mod(
    modid = MOD_ID,
    name = MOD_NAME,
    version = MOD_VERSION,
    modLanguageAdapter = "net.shadowfacts.forgelin.KotlinAdapter",
    dependencies = "required-after:forgelin;required-after:hodgepodge;",
    acceptableRemoteVersions = "*",
)
object FrameworkMod {
  private val logger = LogManager.getLogger(FrameworkMod::class.java)

  @Mod.EventHandler
  fun onInit(event: FMLInitializationEvent) {
    checkKotlinRuntime()
    logger.info("{} {} ready", MOD_NAME, MOD_VERSION)
  }

  /**
   * Mods built on KNH Core must ship the matching core version; the runtime otherwise fails much
   * later with a `NoSuchMethodError`. Call from the mod's pre-init with its own version string.
   */
  fun checkDependent(modId: String, modVersion: String) {
    if (modVersion == MOD_VERSION) {
      return
    }
    logger.error(
        "{} {} was built for KNH Core {}, but KNH Core {} is installed; expect crashes until the versions match",
        modId,
        modVersion,
        modVersion,
        MOD_VERSION,
    )
  }

  /**
   * Forgelin supplies the Kotlin stdlib at runtime and this jar is compiled against a pinned
   * version of it. A Forgelin update that lowers the stdlib would otherwise surface as random
   * `NoSuchMethodError`s deep inside mods, so make the mismatch a loud log line instead.
   */
  private fun checkKotlinRuntime() {
    val runtime = KotlinVersion.CURRENT
    val expected = EXPECTED_KOTLIN_STDLIB_VERSION.split('.').mapNotNull(String::toIntOrNull)
    val (major, minor) = expected.getOrElse(0) { 0 } to expected.getOrElse(1) { 0 }
    if (runtime.isAtLeast(major, minor)) {
      logger.info("Kotlin stdlib {} (compiled against {})", runtime, EXPECTED_KOTLIN_STDLIB_VERSION)
    } else {
      logger.error(
          "Kotlin stdlib {} provided by Forgelin is older than the {} this build targets; expect NoSuchMethodError crashes",
          runtime,
          EXPECTED_KOTLIN_STDLIB_VERSION,
      )
    }
  }
}
