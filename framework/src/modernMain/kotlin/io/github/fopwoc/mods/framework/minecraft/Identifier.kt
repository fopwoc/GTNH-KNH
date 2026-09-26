package io.github.fopwoc.mods.framework.minecraft

// Mojang's namespaced resource id: 26.x renamed `ResourceLocation` to `Identifier`.
/*? if >=26 {*/
typealias Identifier = net.minecraft.resources.Identifier
/*?} else {*/
/*typealias Identifier = net.minecraft.resources.ResourceLocation
 *//*?}*/
