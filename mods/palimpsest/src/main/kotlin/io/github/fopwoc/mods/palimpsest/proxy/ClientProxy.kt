package io.github.fopwoc.mods.palimpsest.proxy

import io.github.fopwoc.mods.framework.ModProxy
import io.github.fopwoc.mods.palimpsest.client.command.PalimpsestCommand

@Suppress("unused")
class ClientProxy : ModProxy() {
    override fun init() {
        PalimpsestCommand.register()
    }
}
