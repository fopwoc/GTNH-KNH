package io.github.fopwoc.mods.framework.server

import java.util.ServiceLoader
import java.util.UUID
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer

/** Loader-specific access to online players and per-world tick measurements. */
interface ServerAccess {
    fun player(id: UUID): ServerPlayer?

    fun worldTickTimes(level: ServerLevel): TickSamples?

    companion object {
        val current: ServerAccess by lazy {
            checkNotNull(
                ServiceLoader.load(ServerAccess::class.java, ServerAccess::class.java.classLoader)
                    .firstOrNull()
            ) {
                "No KNH Core server access backend for this loader"
            }
        }
    }
}

data class TickSamples(val durationsNanos: LongArray, val lastIndex: Int)
