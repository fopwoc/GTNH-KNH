package io.github.fopwoc.mods.hotspot.server.profiler

import cpw.mods.fml.common.Loader

/** Kept free of Opis types so it can be consulted before [OpisTickProfiler] is ever loaded. */
object OpisAvailability {
  val isPresent: Boolean by lazy {
    Loader.isModLoaded("Opis") &&
        runCatching { Class.forName("mcp.mobius.mobiuscore.profiler.ProfilerSection") }.isSuccess
  }
}
