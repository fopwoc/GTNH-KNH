package io.github.fopwoc.mods.framework

import io.github.fopwoc.mods.framework.log.logger


object FrameworkMod {
    private val logger = logger<FrameworkMod>()

    fun onInit() {
        checkKotlinRuntime()
        logger.info("{} {} ready", ModMetadata.MOD_NAME, ModMetadata.MOD_VERSION)
    }

    /**
     * Mods built on KNH Core must ship the matching core version; the runtime otherwise fails much
     * later with a `NoSuchMethodError`. Call from the mod's pre-init with its own version string.
     */
    fun checkDependent(modId: String, modVersion: String) {
        if (modVersion == ModMetadata.MOD_VERSION) {
            return
        }
        logger.error(
            "{} {} was built for KNH Core {}, but KNH Core {} is installed; expect crashes until the versions match",
            modId,
            modVersion,
            modVersion,
            ModMetadata.MOD_VERSION,
        )
    }

    /**
     * Forgelin supplies the Kotlin stdlib at runtime and this jar is compiled against a pinned
     * version of it. A Forgelin update that lowers the stdlib would otherwise surface as random
     * `NoSuchMethodError`s deep inside mods, so make the mismatch a loud log line instead.
     */
    private fun checkKotlinRuntime() {
        val runtime = KotlinVersion.CURRENT
        val expected = ModMetadata.KOTLIN_API_VERSION.split('.').mapNotNull(String::toIntOrNull)
        val (major, minor) = expected.getOrElse(0) { 0 } to expected.getOrElse(1) { 0 }
        if (runtime.isAtLeast(major, minor)) {
            logger.info(
                "Kotlin stdlib {} (compiled against {})",
                runtime,
                ModMetadata.KOTLIN_API_VERSION,
            )
        } else {
            logger.error(
                "Kotlin stdlib {} provided by Forgelin is older than the {} this build targets; " +
                    "expect NoSuchMethodError crashes",
                runtime,
                ModMetadata.KOTLIN_API_VERSION,
            )
        }
    }
}
