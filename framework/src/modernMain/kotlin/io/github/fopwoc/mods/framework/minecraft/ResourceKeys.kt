package io.github.fopwoc.mods.framework.minecraft

import net.minecraft.resources.ResourceKey

/** The id of the entry a key names, like `minecraft:overworld`. */
val ResourceKey<*>.id: Identifier
    get() {
        /*? if >=26 {*/
        return identifier()
        /*?} else {*/
        /*return location()
         */
        /*?}*/
    }
