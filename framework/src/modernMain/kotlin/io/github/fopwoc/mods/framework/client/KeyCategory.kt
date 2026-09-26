package io.github.fopwoc.mods.framework.client

import io.github.fopwoc.mods.framework.minecraft.Identifier

// Key mapping categories: a registered id with a type of its own on 26.x, a translation key on
// 1.21.1. Both translate as key.category.<namespace>.main.
/*? if >=26 {*/
typealias KeyCategory = net.minecraft.client.KeyMapping.Category

internal fun keyCategory(namespace: String): KeyCategory =
    KeyCategory(Identifier.fromNamespaceAndPath(namespace, "main"))
/*?} else {*/
/*typealias KeyCategory = String

internal fun keyCategory(namespace: String): KeyCategory =
    Identifier.fromNamespaceAndPath(namespace, "main").toLanguageKey("key.category")
*//*?}*/
