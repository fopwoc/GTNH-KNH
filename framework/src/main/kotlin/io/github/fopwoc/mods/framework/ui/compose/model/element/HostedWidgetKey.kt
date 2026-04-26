package io.github.fopwoc.mods.framework.ui.compose.model.element

/**
 * Opaque identity key used to retain hosted native Minecraft widgets across recompositions.
 *
 * This stays as a dedicated reference type so the hosting registry cannot accidentally be keyed
 * by value-based objects such as strings or data classes, which would not work with its
 * identity-based storage.
 */
internal class HostedWidgetKey internal constructor()

