package io.github.fopwoc.mods.framework.client

// Fabric API renamed its key binding helper for 26.x's Mojang names.
/*? if >=26 {*/
internal typealias KeyMappingHelper = net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
/*?} else {*/
/*internal typealias KeyMappingHelper =
    net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
*//*?}*/
