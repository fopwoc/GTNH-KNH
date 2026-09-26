package io.github.fopwoc.mods.framework.config

// Forge Config API Port moved its NeoForge-config API between Minecraft versions; the backends use
// these names on every version.

/*? if >=26 {*/
internal typealias ConfigRegistry = fuzs.forgeconfigapiport.fabric.api.v5.ConfigRegistry

internal typealias ModConfigEvents = fuzs.forgeconfigapiport.fabric.api.v5.ModConfigEvents

internal typealias ConfigScreenFactoryRegistry =
    fuzs.forgeconfigapiport.fabric.api.v5.client.ConfigScreenFactoryRegistry
/*?} else {*/
/*internal typealias ConfigRegistry =
    fuzs.forgeconfigapiport.fabric.api.neoforge.v4.NeoForgeConfigRegistry

internal typealias ModConfigEvents =
    fuzs.forgeconfigapiport.fabric.api.neoforge.v4.NeoForgeModConfigEvents

internal typealias ConfigScreenFactoryRegistry =
    fuzs.forgeconfigapiport.fabric.api.neoforge.v4.client.ConfigScreenFactoryRegistry
*//*?}*/
